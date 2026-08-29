/*
 * Copyright (c) 2026 by David Gerber - https://zapek.com
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
 * along with Xeres.  If not see <http://www.gnu.org/licenses/>.
 */

package io.xeres.common.dto.journal;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.xeres.common.id.GxsId;
import io.xeres.common.id.MsgId;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

public record JournalMessageDTO(
		long id,
		GxsId gxsId,
		GxsId groupGxsId,
		MsgId msgId,
		long originalId,
		long parentId,
		GxsId authorGxsId,
		String authorName,
		String groupName,
		String name,
		Instant published,
		String title,
		@JsonInclude(NON_EMPTY)
		String content,
		@JsonInclude(NON_EMPTY)
		String snippet,
		boolean hasImage,
		int imageWidth,
		int imageHeight,
		boolean read,
		int likeCount,
		int dislikeCount,
		String myVote,
		int commentCount,
		boolean favorited
)
{
	public JournalMessageDTO
	{
		if (content == null)
		{
			content = "";
		}
		if (snippet == null)
		{
			snippet = "";
		}
		myVote = myVote == null ? "NONE" : myVote;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof JournalMessageDTO that))
		{
			return false;
		}
		return Objects.equals(gxsId, that.gxsId);
	}

	@Override
	public int hashCode()
	{
		return Objects.hashCode(gxsId);
	}

	@Override
	public @NonNull String toString()
	{
		return "JournalMessageDTO{" +
				"gxsId=" + gxsId +
				", title='" + title + '\'' +
				'}';
	}
}
