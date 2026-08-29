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

import io.xeres.common.id.Sha1Sum;

import java.io.IOException;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Optional;

/// Represents a local file. Can be complete or being completed.
interface FileProvider
{
	long getFileSize();

	boolean open();

	byte[] read(long offset, int size) throws IOException;

	void write(long offset, byte[] data) throws IOException;

	void close();

	void closeAndDelete();

	BitSet getChunkMap();

	Optional<Integer> getNeededChunk(BitSet chunkMap);

	boolean hasChunk(int index);

	boolean isComplete();

	Path getPath();

	long getBytesWritten();

	long getId();

	Sha1Sum computeHash(long offset);

	/// Marks a chunk as verified (its CRC matched). Optional, defaults to always verified.
	///
	/// @param chunk the chunk number
	default void setVerified(int chunk)
	{
		// Nothing to do by default
	}

	/// Tells if a chunk has been verified.
	///
	/// @param chunk the chunk number
	/// @return true if verified
	default boolean isVerified(int chunk)
	{
		return true;
	}

	/// Tells if every chunk has been verified.
	///
	/// @return true if fully verified
	default boolean isFullyVerified()
	{
		return true;
	}

	/// Forgets a chunk's data so it gets downloaded again (used after a CRC mismatch).
	///
	/// @param chunk the chunk number
	default void resetChunk(int chunk)
	{
		// Nothing to do by default
	}
}
