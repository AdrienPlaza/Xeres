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

package io.xeres.app.service.notification.journal;

import io.xeres.app.database.model.gxs.GxsGroupItem;
import io.xeres.app.database.model.journal.JournalMapper;
import io.xeres.app.service.IdentityService;
import io.xeres.app.service.UnHtmlService;
import io.xeres.app.service.notification.NotificationService;
import io.xeres.app.xrs.common.CommentMessageItem;
import io.xeres.app.xrs.common.VoteMessageItem;
import io.xeres.app.xrs.service.identity.item.IdentityGroupItem;
import io.xeres.app.xrs.service.journal.JournalRsService;
import io.xeres.app.xrs.service.journal.item.JournalGroupItem;
import io.xeres.app.xrs.service.journal.item.JournalMessageItem;
import io.xeres.common.id.GxsId;
import io.xeres.common.id.MsgId;
import io.xeres.common.rest.notification.journal.AddOrUpdateJournalComments;
import io.xeres.common.rest.notification.journal.AddOrUpdateJournalGroups;
import io.xeres.common.rest.notification.journal.AddOrUpdateJournalMessages;
import io.xeres.common.rest.notification.journal.AddOrUpdateJournalVotes;
import io.xeres.common.rest.notification.journal.SetJournalGroupMessagesReadState;
import io.xeres.common.rest.notification.journal.SetJournalMessageReadState;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.xeres.app.database.model.journal.JournalMapper.toCommentDTOs;
import static io.xeres.app.database.model.journal.JournalMapper.toDTOs;

@Service
public class JournalNotificationService extends NotificationService
{
	private final JournalRsService journalRsService;
	private final IdentityService identityService;
	private final UnHtmlService unHtmlService;

	public JournalNotificationService(@Lazy JournalRsService journalRsService, IdentityService identityService, UnHtmlService unHtmlService)
	{
		this.journalRsService = journalRsService;
		this.identityService = identityService;
		this.unHtmlService = unHtmlService;
	}

	public void addOrUpdateGroups(List<JournalGroupItem> groups)
	{
		sendNotification(new AddOrUpdateJournalGroups(toDTOs(groups, identityService.getOwnIdentity().getGxsId())));
	}

	public void addOrUpdateMessages(List<JournalMessageItem> messages)
	{
		sendNotification(new AddOrUpdateJournalMessages(toMessageDTOs(messages, false)));
	}

	private List<io.xeres.common.dto.journal.JournalMessageDTO> toMessageDTOs(List<JournalMessageItem> messages, boolean withContent)
	{
		var authors = identityService.findAll(messages.stream().map(JournalMessageItem::getAuthorGxsId).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(GxsGroupItem::getGxsId, Function.identity()));
		var groupNames = journalRsService.findAllGroups().stream()
				.collect(Collectors.toMap(JournalGroupItem::getGxsId, JournalGroupItem::getName, (a, b) -> a));
		return messages.stream()
				.map(message -> io.xeres.app.database.model.journal.JournalMapper.toDTO(
						unHtmlService,
						message,
						authors.getOrDefault(message.getAuthorGxsId(), IdentityGroupItem.EMPTY).getName(),
						groupNames.getOrDefault(message.getGxsId(), ""),
						0L,
						0L,
						"NONE",
						0,
						false,
						withContent
				))
				.toList();
	}

	public void setMessageReadState(long groupId, long messageId, boolean read)
	{
		sendNotification(new SetJournalMessageReadState(groupId, messageId, read));
	}

	public void setGroupMessagesReadState(long groupId, boolean read)
	{
		sendNotification(new SetJournalGroupMessagesReadState(groupId, read));
	}

	public void addOrUpdateComments(List<CommentMessageItem> comments)
	{
		var authors = identityService.findAll(comments.stream().map(CommentMessageItem::getAuthorGxsId).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(GxsGroupItem::getGxsId, Function.identity()));
		sendNotification(new AddOrUpdateJournalComments(toCommentDTOs(comments, authors)));
	}

	public void addOrUpdateVotes(List<VoteMessageItem> votes)
	{
		votes.stream()
				.map(VoteMessageItem::getParentMsgId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet())
				.forEach(articleMsgId -> {
					var likeCount = journalRsService.countVotes(articleMsgId, VoteMessageItem.Type.UP);
					var dislikeCount = journalRsService.countVotes(articleMsgId, VoteMessageItem.Type.DOWN);
					sendNotification(new AddOrUpdateJournalVotes(articleMsgId, likeCount, dislikeCount, "NONE"));
				});
	}
}
