/*
 * Copyright (c) 2026 by David Gerber - https://zapek.com
 *
 * This file part of Xeres.
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

package io.xeres.app.database.model.journal;

import io.xeres.app.service.UnHtmlService;
import io.xeres.app.xrs.common.CommentMessageItem;
import io.xeres.app.xrs.common.VoteMessageItem;
import io.xeres.app.xrs.service.identity.item.IdentityGroupItem;
import io.xeres.app.xrs.service.journal.item.JournalGroupItem;
import io.xeres.app.xrs.service.journal.item.JournalMessageItem;
import io.xeres.common.dto.journal.JournalCommentDTO;
import io.xeres.common.dto.journal.JournalGroupDTO;
import io.xeres.common.dto.journal.JournalMessageDTO;
import io.xeres.common.id.GxsId;
import io.xeres.common.id.MsgId;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

public final class JournalMapper
{
	private static final int SNIPPET_LENGTH = 280;

	private JournalMapper()
	{
		throw new UnsupportedOperationException("Utility class");
	}

	public static JournalGroupDTO toDTO(JournalGroupItem item, GxsId ownGxsId)
	{
		if (item == null)
		{
			return null;
		}

		return new JournalGroupDTO(
				item.getId(),
				item.getGxsId(),
				item.getName(),
				item.getDescription(),
				item.hasImage(),
				item.isSubscribed(),
				item.isExternal(),
				item.getVisibleMessageCount(),
				item.getLastActivity(),
				ownGxsId != null && ownGxsId.equals(item.getAuthorGxsId())
		);
	}

	public static List<JournalGroupDTO> toDTOs(List<JournalGroupItem> items, GxsId ownGxsId)
	{
		return emptyIfNull(items).stream()
				.map(item -> toDTO(item, ownGxsId))
				.toList();
	}

	public static JournalMessageDTO toDTO(JournalMessageItem item, String authorName, String groupName, long originalId, long parentId, String content, int likeCount, int dislikeCount, String myVote, int commentCount, boolean favorited)
	{
		if (item == null)
		{
			return null;
		}

		return new JournalMessageDTO(
				item.getId(),
				item.getGxsId(),
				item.getGxsId(),
				item.getMsgId(),
				originalId,
				parentId,
				item.getAuthorGxsId(),
				authorName,
				groupName,
				item.getName(),
				item.getPublished(),
				item.getTitle(),
				content,
				toSnippet(content),
				item.hasImage(),
				item.getImageWidth(),
				item.getImageHeight(),
				item.isRead(),
				likeCount,
				dislikeCount,
				myVote,
				commentCount,
				favorited
		);
	}

	public static JournalMessageDTO toDTO(UnHtmlService unHtmlService, JournalMessageItem item, String authorName, String groupName, long originalId, long parentId, String myVote, int commentCount, boolean favorited, boolean withContent)
	{
		var content = withContent ? unHtmlService.cleanupMessage(item.getContent()) : "";
		var likeCount = 0;
		var dislikeCount = 0;
		return toDTO(item, authorName, groupName, originalId, parentId, content, likeCount, dislikeCount, myVote, commentCount, favorited);
	}

	public static JournalCommentDTO toDTO(CommentMessageItem item, String authorName)
	{
		if (item == null)
		{
			return null;
		}

		return new JournalCommentDTO(
				item.getId(),
				item.getGxsId(),
				item.getParentMsgId(),
				item.getMsgId(),
				item.getAuthorGxsId(),
				authorName,
				item.getComment(),
				item.getPublished()
		);
	}

	public static List<JournalCommentDTO> toCommentDTOs(List<CommentMessageItem> comments, Map<GxsId, IdentityGroupItem> authors)
	{
		return emptyIfNull(comments).stream()
				.map(comment -> toDTO(comment, authors.getOrDefault(comment.getAuthorGxsId(), IdentityGroupItem.EMPTY).getName()))
				.toList();
	}

	public static String toSnippet(String content)
	{
		if (StringUtils.isBlank(content))
		{
			return "";
		}
		var plain = content.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
		return plain.length() <= SNIPPET_LENGTH ? plain : plain.substring(0, SNIPPET_LENGTH) + "…";
	}

	public static String voteType(VoteMessageItem.Type type)
	{
		return type == null ? "NONE" : type.name();
	}
}
