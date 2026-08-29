/*
 * Copyright (c) 2025-2026 by David Gerber - https://zapek.com
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

package io.xeres.ui.model.journal;

import io.xeres.common.dto.journal.JournalCommentDTO;
import io.xeres.common.dto.journal.JournalGroupDTO;
import io.xeres.common.dto.journal.JournalMessageDTO;
import io.xeres.ui.client.PaginatedResponse;

import java.util.List;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

public final class JournalMapper
{
	private JournalMapper()
	{
		throw new UnsupportedOperationException("Utility class");
	}

	public static JournalGroup fromDTO(JournalGroupDTO dto)
	{
		if (dto == null)
		{
			return null;
		}

		var journalGroup = new JournalGroup();
		journalGroup.setId(dto.id());
		journalGroup.setName(dto.name());
		journalGroup.setGxsId(dto.gxsId());
		journalGroup.setDescription(dto.description());
		journalGroup.setHasImage(dto.hasImage());
		journalGroup.setSubscribed(dto.subscribed());
		journalGroup.setExternal(dto.external());
		journalGroup.setOwn(dto.own());
		journalGroup.setVisibleMessageCount(dto.visibleMessageCount());
		journalGroup.setLastActivity(dto.lastActivity());
		return journalGroup;
	}

	public static JournalMessage fromDTO(JournalMessageDTO dto)
	{
		if (dto == null)
		{
			return null;
		}

		var journalMessage = new JournalMessage();
		journalMessage.setId(dto.id());
		journalMessage.setGxsId(dto.gxsId());
		journalMessage.setGroupGxsId(dto.groupGxsId());
		journalMessage.setMsgId(dto.msgId());
		journalMessage.setAuthorGxsId(dto.authorGxsId());
		journalMessage.setAuthorName(dto.authorName());
		journalMessage.setGroupName(dto.groupName());
		journalMessage.setTitle(dto.title());
		journalMessage.setContent(dto.content());
		journalMessage.setHasImage(dto.hasImage());
		journalMessage.setImageWidth(dto.imageWidth());
		journalMessage.setImageHeight(dto.imageHeight());
		journalMessage.setPublished(dto.published());
		journalMessage.setSnippet(dto.snippet());
		journalMessage.setLikeCount(dto.likeCount());
		journalMessage.setDislikeCount(dto.dislikeCount());
		journalMessage.setMyVote(dto.myVote());
		journalMessage.setCommentCount(dto.commentCount());
		journalMessage.setFavorited(dto.favorited());
		journalMessage.setRead(dto.read());
		return journalMessage;
	}

	public static JournalComment fromDTO(JournalCommentDTO dto)
	{
		if (dto == null)
		{
			return null;
		}

		var journalComment = new JournalComment();
		journalComment.setArticleMsgId(dto.articleMsgId());
		journalComment.setAuthorGxsId(dto.authorGxsId());
		journalComment.setAuthorName(dto.authorName());
		journalComment.setContent(dto.comment());
		journalComment.setPublished(dto.published());
		return journalComment;
	}

	public static PaginatedResponse<JournalMessage> fromDTO(PaginatedResponse<JournalMessageDTO> dto)
	{
		return new PaginatedResponse<>(
				emptyIfNull(dto.content()).stream()
						.map(JournalMapper::fromDTO)
						.toList(),
				dto.page()
		);
	}

	public static List<JournalMessage> fromDTOs(List<JournalMessageDTO> dtos)
	{
		return emptyIfNull(dtos).stream()
				.map(JournalMapper::fromDTO)
				.toList();
	}
}
