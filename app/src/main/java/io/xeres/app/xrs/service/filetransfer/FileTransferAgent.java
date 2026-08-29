/*
 * Copyright (c) 2024-2026 by David Gerber - https://zapek.com
 *
 * This file is part of Xeres.
 *
 * Xeres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Xeres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Xeres.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.xeres.app.xrs.service.filetransfer;

import io.xeres.app.database.model.location.Location;
import io.xeres.common.id.Sha1Sum;
import io.xeres.common.util.FileNameUtils;
import io.xeres.common.util.OsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/// Responsible for sending/receiving one file.
/// There can be several leechers or seeders per file.
class FileTransferAgent
{
	private static final Logger log = LoggerFactory.getLogger(FileTransferAgent.class);

	/// Computes chunk checksums off the manager thread so several chunks can be verified in parallel.
	private static final ExecutorService crcExecutor = Executors.newVirtualThreadPerTaskExecutor();

	/// Time after which a download or upload is considered stale.
	private static final long IDLE_TIME = Duration.ofMinutes(5).toNanos();

	/// Number of chunks (1 MB each) requested in advance from a seeder (pipelining window).
	private static final int DOWNLOAD_WINDOW_CHUNKS = 16;

	/// Delay between two processings while a transfer is active. Keeps the pipe full without busy looping.
	private static final Duration ACTIVE_DELAY = Duration.ofMillis(10);

	/// Delay between two processings while nothing is in progress (waiting for a chunk map, etc.).
	private static final Duration IDLE_DELAY = Duration.ofMillis(250);

	/// Maximum number of 8 KB slices sent to a leecher per processing tick. Bounds memory usage;
	/// the writability check stops earlier if the socket buffer is full.
	private static final int MAX_SLICES_PER_TICK = 128;

	/// Delay between two processings of a leecher that still has data to send.
	private static final Duration LEECHER_ACTIVE_DELAY = Duration.ofMillis(5);

	/// Maximum time to wait for a chunk CRC answer before considering it verified anyway (avoids stalling).
	private static final Duration CRC_TIMEOUT = Duration.ofSeconds(60);

	private final FileTransferRsService fileTransferRsService;
	private final FileProvider fileProvider;
	private final Sha1Sum hash;
	private final String fileName;
	private boolean done;
	private long lastActivity;
	private boolean trusted;

	private final Map<Location, FileLeecher> leechers = new LinkedHashMap<>();
	private final Map<Location, FileSeeder> seeders = new LinkedHashMap<>();

	/// Chunks that have been fully written but are awaiting CRC verification (chunk number -> request time).
	private final Map<Integer, Instant> crcPending = new HashMap<>();

	private final PriorityQueue<FilePeer> queue = new PriorityQueue<>();

	public FileTransferAgent(FileTransferRsService fileTransferRsService, String fileName, Sha1Sum hash, FileProvider fileProvider)
	{
		this.fileTransferRsService = fileTransferRsService;
		this.hash = hash;
		this.fileProvider = fileProvider;
		this.fileName = fileName;
		lastActivity = System.nanoTime();
	}

	public void setTrusted(boolean trusted)
	{
		this.trusted = trusted;
	}

	public FileProvider getFileProvider()
	{
		return fileProvider;
	}

	public String getFileName()
	{
		return fileName;
	}

	/// Adds a seeder, that is, someone we can get the file from.
	///
	/// @param peer the location
	public void addSeeder(Location peer)
	{
		seeders.computeIfAbsent(peer, _ -> {
			var fileSeeder = new FileSeeder(peer);
			fileSeeder.setWindowSize(DOWNLOAD_WINDOW_CHUNKS);
			queue.add(fileSeeder);
			return fileSeeder;
		});
		fileTransferRsService.sendChunkMapRequest(peer, hash, false);
	}

	/// Adds a leecher, that is, someone who wants to download our file.
	/// @param peer the location
	/// @param offset the requested offset of the file
	/// @param size the requested size of the chunk
	public void addLeecher(Location peer, long offset, int size)
	{
		leechers.computeIfAbsent(peer, _ -> {
			var fileLeecher = new FileLeecher(peer);
			queue.add(fileLeecher);
			return fileLeecher;
		}).addSliceSender(new SliceSender(fileTransferRsService, peer, fileProvider, hash, fileProvider.getFileSize(), offset, size));
	}

	public void removePeer(Location peer)
	{
		FilePeer removed = seeders.remove(peer);
		if (removed == null)
		{
			removed = leechers.remove(peer);
		}

		if (removed == null)
		{
			log.warn("Removal of peer {} failed because it's not in the list. This shouldn't happen.", peer);
		}
		queue.remove(removed);
	}

	/// Processes file transfers.
	public void process()
	{
		processPeers();
	}

	public void cancel()
	{
		if (!fileProvider.isComplete())
		{
			fileProvider.closeAndDelete();
		}
	}

	public void stop()
	{
		fileProvider.close();
	}

	public void addChunkMap(Location peer, BitSet chunkMap)
	{
		var seeder = seeders.get(peer);
		if (seeder == null)
		{
			log.error("Seeder not found for adding chunkmap");
			return;
		}
		seeder.updateChunkMap(chunkMap);
	}

	/// Tells if an agent idle. That is, nothing has been sent or received
	/// for more than 5 minutes.
	///
	/// @return true if idle
	public boolean isIdle()
	{
		return System.nanoTime() - lastActivity > IDLE_TIME;
	}

	public boolean isDone() // XXX: isDone what? it's only when it's done loading, should be clearer
	{
		return done;
	}

	/// Returns the next desired processing.
	///
	/// @return when the next processing happens, null if there's no processing needed
	public Instant getNextProcessing()
	{
		var filePeer = queue.peek();
		if (filePeer != null)
		{
			return filePeer.getNextScheduling();
		}
		return null;
	}

	private void processPeers()
	{
		var now = Instant.now();
		while (true)
		{
			var filePeer = queue.peek();
			if (filePeer == null || filePeer.getNextScheduling().isAfter(now))
			{
				break;
			}
			queue.poll();
			switch (filePeer)
			{
				case FileSeeder fileSeeder -> processSeeder(fileSeeder);
				case FileLeecher fileLeecher -> processLeecher(fileLeecher);
				case null ->
				{
					// Empty queue
				}
				default -> throw new IllegalStateException("Unhandled peer class");
			}
		}
	}

	private void processSeeder(FileSeeder fileSeeder)
	{
		var chunkReceiver = fileSeeder.getChunkReceiver();

		// Drop the chunks that have been fully written from the in-flight set, and trigger CRC verification.
		for (var it = chunkReceiver.getRequestedChunks().iterator(); it.hasNext(); )
		{
			var chunkNumber = it.next();
			if (fileProvider.hasChunk(chunkNumber))
			{
				it.remove();
				boolean alreadyPending;
				synchronized (crcPending)
				{
					alreadyPending = crcPending.containsKey(chunkNumber);
				}
				if (!fileProvider.isVerified(chunkNumber) && !alreadyPending
						&& fileSeeder.hasChunkMap() && fileSeeder.getChunkMap().get(chunkNumber))
				{
					fileTransferRsService.sendSingleChunkCrcRequest(fileSeeder.getLocation(), hash, chunkNumber);
					synchronized (crcPending)
					{
						crcPending.put(chunkNumber, Instant.now());
					}
				}
			}
		}

		// Give up on CRC verification after a while so the transfer can finish even if a peer never answers.
		var now = Instant.now();
		synchronized (crcPending)
		{
			crcPending.entrySet().removeIf(entry -> {
				if (Duration.between(entry.getValue(), now).compareTo(CRC_TIMEOUT) > 0)
				{
					fileProvider.setVerified(entry.getKey());
					return true;
				}
				return false;
			});
		}

		if (fileProvider.isComplete() && fileProvider.isFullyVerified())
		{
			if (!done)
			{
				log.debug("File is complete, size: {}, renaming to {}", fileProvider.getFileSize(), fileName);
				stop();
				fileTransferRsService.markDownloadAsCompleted(hash);
				fileTransferRsService.deactivateTunnels(hash);
				var newPath = renameFile(fileProvider.getPath(), fileName);
				setFileSecurity(newPath);
				done = true; // Prevents closing the file several times (we might have several seeders)
			}
			removePeer(fileSeeder.getLocation());
			return; // Don't reinsert in the queue
		}

		if (fileSeeder.hasChunkMap())
		{
			// Keep several chunks in flight so the link stays saturated (pipelining).
			while (chunkReceiver.getRequestedChunkCount() < chunkReceiver.getWindowSize())
			{
				var chunkNumber = getNextChunk(fileSeeder.getChunkMap());
				if (chunkNumber.isEmpty())
				{
					break;
				}
				log.debug("Requesting chunk number {} to peer {}", chunkNumber.get(), fileSeeder.getLocation());
				fileTransferRsService.sendDataRequest(fileSeeder.getLocation(), hash, fileProvider.getFileSize(), (long) chunkNumber.get() * FileTransferRsService.CHUNK_SIZE, FileTransferRsService.CHUNK_SIZE);
				chunkReceiver.addRequestedChunk(chunkNumber.get());
			}
		}

		// Stay active while there is data in flight so completed chunks are replaced promptly.
		addNextScheduling(fileSeeder, chunkReceiver.getRequestedChunkCount() > 0 || !crcPending.isEmpty() ? ACTIVE_DELAY : IDLE_DELAY);
	}

	/// Called when a chunk CRC answer comes back. Verifies it against the locally computed hash.
	///
	/// @param chunkNumber the chunk number
	/// @param expected    the checksum sent by the peer
	void verifyChunk(int chunkNumber, Sha1Sum expected)
	{
		synchronized (crcPending)
		{
			// Mark as pending (replaces the request timestamp) while we compute the hash off-thread.
			crcPending.put(chunkNumber, Instant.now());
		}
		crcExecutor.submit(() ->
		{
			var local = fileProvider.computeHash((long) chunkNumber * FileTransferRsService.CHUNK_SIZE);
			if (local != null && local.equals(expected))
			{
				fileProvider.setVerified(chunkNumber);
			}
			else
			{
				log.warn("Chunk {} CRC mismatch for hash {}, downloading it again", chunkNumber, hash);
				fileProvider.resetChunk(chunkNumber);
			}
			synchronized (crcPending)
			{
				crcPending.remove(chunkNumber);
			}
		});
	}

	private void setFileSecurity(Path path)
	{
		if (path != null)
		{
			OsUtils.setFileSecurity(path, trusted);
		}
	}

	private void processLeecher(FileLeecher fileLeecher)
	{
		var sliceSender = fileLeecher.getSliceSender();
		var sent = 0;
		var more = true;
		// Send as many slices as the socket accepts. The writability check provides back-pressure
		// (no 50 ms artificial cap), while MAX_SLICES_PER_TICK bounds memory usage.
		while (more && sent < MAX_SLICES_PER_TICK && fileTransferRsService.isWritable(fileLeecher.getLocation()))
		{
			more = sliceSender.send();
			if (more)
			{
				sent++;
				lastActivity = System.nanoTime();
			}
		}

		if (!more)
		{
			// We just remove the leecher here and nothing else. The fileTransferManager will close the file
			// when it's idle for some time, otherwise it would need to be reopened immediately for the
			// next slice.
			fileLeecher.removeSliceSender(sliceSender);
			if (fileLeecher.hasNoMoreSlices())
			{
				removePeer(fileLeecher.getLocation());
				return;
			}
		}
		addNextScheduling(fileLeecher, LEECHER_ACTIVE_DELAY);
	}

	private void addNextScheduling(FilePeer filePeer, Duration duration)
	{
		filePeer.addNextScheduling(duration);
		queue.offer(filePeer);
	}

	private static Path renameFile(Path filePath, String fileName)
	{
		var success = false;
		Path path = null;

		while (!success)
		{
			try
			{
				var newPath = filePath.resolveSibling(fileName);
				Files.move(filePath, newPath);
				OsUtils.setFileVisible(newPath, true);
				success = true;
				path = newPath;
			}
			catch (FileAlreadyExistsException _)
			{
				log.warn("File name {} already exists, renaming...", fileName);
				fileName = FileNameUtils.rename(fileName);
			}
			catch (InvalidPathException _)
			{
				log.warn("File name {} is invalid, trying to fix the characters...", fileName);
				var newFileName = OsUtils.sanitizeFileName(fileName);
				if (newFileName.equals(fileName))
				{
					fileName = "InvalidFileName_RenameMe";
					log.error("Couldn't find a proper name for file {}, using: {}. Rename by hand and report", filePath, fileName);
				}
				else
				{
					fileName = newFileName;
				}
			}
			catch (IOException e)
			{
				log.error("Couldn't rename the file {} to {}", filePath, fileName, e);
				success = true; // This is really a failure, but there's nothing else we can do
			}
		}
		return path;
	}

	/// Gets the next available chunk.
	///
	/// @return the chunk number
	private Optional<Integer> getNextChunk(BitSet chunkMap)
	{
		return fileProvider.getNeededChunk(chunkMap);
	}
}