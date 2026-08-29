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

package io.xeres.app.service;

import io.xeres.app.database.model.gxs.GxsGroupItem;
import io.xeres.app.database.model.journal.JournalCommentHistory;
import io.xeres.app.database.repository.GxsCommentMessageRepository;
import io.xeres.app.database.repository.GxsVoteMessageRepository;
import io.xeres.app.database.repository.JournalCommentHistoryRepository;
import io.xeres.app.database.repository.JournalFavoriteRepository;
import io.xeres.app.xrs.common.CommentMessageItem;
import io.xeres.app.xrs.common.VoteMessageItem;
import io.xeres.app.xrs.service.identity.item.IdentityGroupItem;
import io.xeres.app.xrs.service.journal.item.JournalMessageItem;
import io.xeres.common.id.GxsId;
import io.xeres.common.id.MsgId;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JournalMessageService
{
	private final GxsCommentMessageRepository gxsCommentMessageRepository;
	private final GxsVoteMessageRepository gxsVoteMessageRepository;
	private final JournalFavoriteRepository journalFavoriteRepository;
	private final JournalCommentHistoryRepository journalCommentHistoryRepository;
	private final IdentityService identityService;

	public JournalMessageService(GxsCommentMessageRepository gxsCommentMessageRepository, GxsVoteMessageRepository gxsVoteMessageRepository, JournalFavoriteRepository journalFavoriteRepository, JournalCommentHistoryRepository journalCommentHistoryRepository, IdentityService identityService)
	{
		this.gxsCommentMessageRepository = gxsCommentMessageRepository;
		this.gxsVoteMessageRepository = gxsVoteMessageRepository;
		this.journalFavoriteRepository = journalFavoriteRepository;
		this.journalCommentHistoryRepository = journalCommentHistoryRepository;
		this.identityService = identityService;
	}

	public Map<GxsId, IdentityGroupItem> getAuthorsMapFromMessages(List<JournalMessageItem> journalMessages)
	{
		var authors = journalMessages.stream()
				.map(JournalMessageItem::getAuthorGxsId)
				.collect(Collectors.toSet());

		return identityService.findAll(authors).stream()
				.collect(Collectors.toMap(GxsGroupItem::getGxsId, Function.identity()));
	}

	public Map<MsgId, int[]> getVoteCounts(Set<MsgId> msgIds)
	{
		var counts = new HashMap<MsgId, int[]>();
		msgIds.forEach(msgId -> counts.put(msgId, new int[]{0, 0}));
		if (msgIds.isEmpty())
		{
			return counts;
		}
		gxsVoteMessageRepository.findAllByParentMsgIdIn(msgIds).forEach(vote -> {
			if (vote.isHidden())
			{
				return;
			}
			var array = counts.computeIfAbsent(vote.getParentMsgId(), _ -> new int[]{0, 0});
			if (vote.getType() == VoteMessageItem.Type.UP)
			{
				array[0]++;
			}
			else if (vote.getType() == VoteMessageItem.Type.DOWN)
			{
				array[1]++;
			}
		});
		return counts;
	}

	public Map<MsgId, String> getMyVotes(Set<MsgId> msgIds, GxsId ownGxsId)
	{
		var myVotes = new HashMap<MsgId, String>();
		if (msgIds.isEmpty())
		{
			return myVotes;
		}
		var ownVotes = gxsVoteMessageRepository.findAllByParentMsgIdIn(msgIds).stream()
				.filter(vote -> !vote.isHidden())
				.filter(vote -> vote.getAuthorGxsId() != null && vote.getAuthorGxsId().equals(ownGxsId))
				.collect(Collectors.toMap(VoteMessageItem::getParentMsgId, VoteMessageItem::getType, (a, b) -> b));
		ownVotes.forEach((msgId, type) -> myVotes.put(msgId, type == null ? "NONE" : type.name()));
		return myVotes;
	}

	public Map<MsgId, Integer> getCommentCounts(Set<MsgId> msgIds)
	{
		var counts = new HashMap<MsgId, Integer>();
		if (msgIds.isEmpty())
		{
			return counts;
		}
		gxsCommentMessageRepository.findAllByParentMsgIdIn(msgIds).stream()
				.filter(comment -> !comment.isHidden())
				.forEach(comment -> counts.merge(comment.getParentMsgId(), 1, Integer::sum));
		return counts;
	}

	public Set<Long> getFavoritedMessageIds(List<Long> messageIds)
	{
		return journalFavoriteRepository.findAllByMessageIdInAndFavoriteIsTrue(messageIds).stream()
				.map(f -> f.getMessageId())
				.collect(Collectors.toSet());
	}

	public Set<Long> getCommentedArticleIds(List<Long> messageIds)
	{
		return journalCommentHistoryRepository.findAllByArticleMessageIdIn(messageIds).stream()
				.map(JournalCommentHistory::getArticleMessageId)
				.collect(Collectors.toSet());
	}

	public List<CommentMessageItem> findComments(MsgId articleMsgId)
	{
		return gxsCommentMessageRepository.findAllByParentMsgIdIn(Set.of(articleMsgId));
	}
}
