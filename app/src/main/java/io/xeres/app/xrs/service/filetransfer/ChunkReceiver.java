package io.xeres.app.xrs.service.filetransfer;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

/// Tracks the state of a chunk reception from a given seeder.
/// Instead of a single in-flight chunk, it keeps a set of chunks that have been
/// requested but not yet fully written, allowing several chunks to be in flight
/// at the same time (pipelining) for higher throughput.
class ChunkReceiver
{
	private BitSet chunkMap;
	private final Set<Integer> requestedChunks = new HashSet<>();
	private int windowSize = 1;

	public boolean hasChunkMap()
	{
		return chunkMap != null;
	}

	public BitSet getChunkMap()
	{
		return chunkMap;
	}

	public void setChunkMap(BitSet chunkMap)
	{
		this.chunkMap = chunkMap;
	}

	public void addRequestedChunk(int chunkNumber)
	{
		requestedChunks.add(chunkNumber);
	}

	public void removeRequestedChunk(int chunkNumber)
	{
		requestedChunks.remove(chunkNumber);
	}

	public boolean isChunkRequested(int chunkNumber)
	{
		return requestedChunks.contains(chunkNumber);
	}

	public int getRequestedChunkCount()
	{
		return requestedChunks.size();
	}

	public Set<Integer> getRequestedChunks()
	{
		return requestedChunks;
	}

	public void setWindowSize(int windowSize)
	{
		this.windowSize = windowSize;
	}

	public int getWindowSize()
	{
		return windowSize;
	}
}
