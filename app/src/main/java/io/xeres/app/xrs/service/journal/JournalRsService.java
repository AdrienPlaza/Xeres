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

package io.xeres.app.xrs.service.journal;

import io.xeres.app.crypto.hash.sha1.Sha1MessageDigest;
import io.xeres.app.crypto.rsa.RSA;
import io.xeres.app.database.DatabaseSession;
import io.xeres.app.database.DatabaseSessionManager;
import io.xeres.app.database.model.gxs.*;
import io.xeres.app.database.repository.GxsJournalGroupRepository;
import io.xeres.app.database.repository.GxsJournalMessageRepository;
import io.xeres.app.database.repository.GxsCommentMessageRepository;
import io.xeres.app.database.repository.GxsVoteMessageRepository;
import io.xeres.app.net.peer.PeerConnection;
import io.xeres.app.net.peer.PeerConnectionManager;
import io.xeres.app.service.ReputationService;
import io.xeres.app.service.notification.journal.JournalNotificationService;
import io.xeres.app.xrs.common.CommentMessageItem;
import io.xeres.app.xrs.common.VoteMessageItem;
import io.xeres.app.xrs.item.Item;
import io.xeres.app.xrs.service.RsServiceRegistry;
import io.xeres.app.xrs.service.gxs.GxsAuthentication;
import io.xeres.app.xrs.service.gxs.GxsHelperService;
import io.xeres.app.xrs.service.gxs.GxsRsService;
import io.xeres.app.xrs.service.gxs.GxsTransactionManager;
import io.xeres.app.xrs.service.gxs.item.GxsSyncMessageRequestItem;
import io.xeres.app.xrs.service.identity.IdentityManager;
import io.xeres.app.xrs.service.identity.item.IdentityGroupItem;
import io.xeres.app.xrs.service.journal.item.JournalGroupItem;
import io.xeres.app.xrs.service.journal.item.JournalMessageItem;
import io.xeres.common.gxs.GxsGroupConstants;
import io.xeres.app.util.GxsUtils;
import io.xeres.common.id.GxsId;
import io.xeres.common.id.MsgId;
import io.xeres.common.protocol.xrs.RsServiceType;
import io.xeres.common.util.image.ImageUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static io.xeres.app.util.GxsUtils.IMAGE_MAX_INPUT_SIZE;
import static io.xeres.app.util.GxsUtils.MAXIMUM_GXS_MESSAGE_SIZE;
import static io.xeres.app.xrs.service.gxs.GxsAuthentication.Flags.CHILD_NEEDS_AUTHOR;
import static io.xeres.app.xrs.service.gxs.GxsAuthentication.Flags.ROOT_NEEDS_PUBLISH;
import static io.xeres.common.protocol.xrs.RsServiceType.GXS_JOURNALS;

@Component
public class JournalRsService extends GxsRsService<JournalGroupItem, JournalMessageItem>
{
	private static final int IMAGE_MESSAGE_WIDTH = 480;
	private static final int IMAGE_MESSAGE_HEIGHT = 270;

	private static final Duration SYNCHRONIZATION_INITIAL_DELAY = Duration.ofSeconds(90);
	private static final Duration SYNCHRONIZATION_DELAY = Duration.ofMinutes(1);

	private final GxsJournalGroupRepository gxsJournalGroupRepository;
	private final GxsJournalMessageRepository gxsJournalMessageRepository;
	private final GxsHelperService<JournalGroupItem, JournalMessageItem> gxsHelperService;
	private final DatabaseSessionManager databaseSessionManager;
	private final JournalNotificationService journalNotificationService;
	private final GxsCommentMessageRepository gxsCommentMessageRepository;
	private final GxsVoteMessageRepository gxsVoteMessageRepository;

	public JournalRsService(RsServiceRegistry rsServiceRegistry, PeerConnectionManager peerConnectionManager, GxsTransactionManager gxsTransactionManager, DatabaseSessionManager databaseSessionManager, IdentityManager identityManager, GxsHelperService<JournalGroupItem, JournalMessageItem> gxsHelperService, ReputationService reputationService, GxsJournalGroupRepository gxsJournalGroupRepository, GxsJournalMessageRepository gxsJournalMessageRepository, JournalNotificationService journalNotificationService, GxsCommentMessageRepository gxsCommentMessageRepository, GxsVoteMessageRepository gxsVoteMessageRepository)
	{
		super(rsServiceRegistry, peerConnectionManager, gxsTransactionManager, databaseSessionManager, identityManager, gxsHelperService, reputationService);
		this.gxsJournalGroupRepository = gxsJournalGroupRepository;
		this.gxsJournalMessageRepository = gxsJournalMessageRepository;
		this.gxsHelperService = gxsHelperService;
		this.databaseSessionManager = databaseSessionManager;
		this.journalNotificationService = journalNotificationService;
		this.gxsCommentMessageRepository = gxsCommentMessageRepository;
		this.gxsVoteMessageRepository = gxsVoteMessageRepository;
	}

	@Override
	public RsServiceType getServiceType()
	{
		return GXS_JOURNALS;
	}

	@Override
	protected GxsAuthentication getAuthentication()
	{
		// Only the journal owner can write articles; comments and votes are signed by their author.
		return new GxsAuthentication.Builder()
				.withRequirements(EnumSet.of(ROOT_NEEDS_PUBLISH, CHILD_NEEDS_AUTHOR))
				.build();
	}

	@Override
	public void initialize(PeerConnection peerConnection)
	{
		super.initialize(peerConnection);
		peerConnection.scheduleWithFixedDelay(
				() -> syncMessages(peerConnection),
				SYNCHRONIZATION_INITIAL_DELAY,
				SYNCHRONIZATION_DELAY
		);
	}

	@Override
	protected void syncMessages(PeerConnection recipient)
	{
		try (var ignored = new DatabaseSession(databaseSessionManager))
		{
			findAllSubscribedGroups().forEach(journalGroupItem -> {
				var request = new GxsSyncMessageRequestItem(journalGroupItem.getGxsId(), gxsHelperService.getLastPeerMessagesUpdate(recipient.getLocation(), journalGroupItem.getGxsId(), getServiceType()), ChronoUnit.YEARS.getDuration());
				peerConnectionManager.writeItem(recipient, request, this);
			});
		}
	}

	@Override
	protected List<JournalGroupItem> onAvailableGroupListRequest()
	{
		return findAllSubscribedGroups();
	}

	@Override
	protected List<JournalGroupItem> onGroupListRequest(Set<GxsId> ids)
	{
		return findAllGroups(ids);
	}

	@Override
	protected Set<GxsId> onAvailableGroupListResponse(Map<GxsId, Instant> ids)
	{
		var existingMap = findAllGroups(ids.keySet()).stream()
				.collect(Collectors.toMap(GxsGroupItem::getGxsId, GxsGroupItem::getPublished));

		ids.entrySet().removeIf(gxsIdInstantEntry -> {
			var existing = existingMap.get(gxsIdInstantEntry.getKey());
			return existing != null && !gxsIdInstantEntry.getValue().isAfter(existing);
		});
		return ids.keySet();
	}

	@Override
	protected boolean onGroupReceived(JournalGroupItem item)
	{
		log.debug("Received {}, saving/updating...", item);
		return true;
	}

	@Override
	protected void onGroupsSaved(List<JournalGroupItem> items)
	{
		// Auto-subscribe to friends' journals so their articles appear in the aggregated feed.
		items.forEach(group -> {
			if (group.isExternal() && !group.isSubscribed())
			{
				group.setSubscribed(true);
				gxsJournalGroupRepository.save(group);
			}
		});
		journalNotificationService.addOrUpdateGroups(items);
	}

	@Override
	protected List<JournalMessageItem> onPendingMessageListRequest(GxsId gxsId, Instant since)
	{
		return findAllMessagesInGroupSince(gxsId, since);
	}

	@Override
	protected List<? extends GxsMessageItem> onMessageListRequest(GxsId gxsId, Set<MsgId> msgIds)
	{
		return findAllMessagesVotesAndCommentsIncludingOlds(gxsId, msgIds);
	}

	@Override
	protected List<MsgId> onMessageListResponse(GxsId gxsId, Set<MsgId> msgIds)
	{
		var existing = findAllMessagesVotesAndCommentsIncludingOlds(gxsId, msgIds).stream()
				.map(GxsMessageItem::getMsgId)
				.collect(Collectors.toSet());

		msgIds.removeAll(existing);

		return msgIds.stream().toList();
	}

	@Override
	protected boolean onMessageReceived(JournalMessageItem item)
	{
		if (item.hasImage())
		{
			var dimension = ImageUtils.getImageDimension(new ByteArrayInputStream(item.getImage()));
			if (dimension != null)
			{
				item.setImageWidth((int) dimension.getWidth());
				item.setImageHeight((int) dimension.getHeight());
			}
		}
		log.debug("Received message {}, saving...", item);
		return true;
	}

	@Override
	protected void onMessagesSaved(List<JournalMessageItem> items)
	{
		journalNotificationService.addOrUpdateMessages(items);
	}

	@Override
	protected boolean onCommentReceived(CommentMessageItem item)
	{
		return true;
	}

	@Override
	protected void onCommentsSaved(List<CommentMessageItem> items)
	{
		journalNotificationService.addOrUpdateComments(items);
	}

	@Override
	protected boolean onVoteReceived(VoteMessageItem item)
	{
		return true;
	}

	@Override
	protected void onVotesSaved(List<VoteMessageItem> items)
	{
		journalNotificationService.addOrUpdateVotes(items);
	}

	@Override
	protected Set<GxsId> getAdditionalIdentities(JournalGroupItem group)
	{
		return Set.of();
	}

	@Transactional
	@Override
	public void handleItem(PeerConnection sender, Item item)
	{
		super.handleItem(sender, item);
	}

	public Optional<JournalGroupItem> findById(long id)
	{
		return gxsJournalGroupRepository.findById(id);
	}

	public List<JournalGroupItem> findAllGroups()
	{
		return gxsJournalGroupRepository.findAll();
	}

	public List<JournalGroupItem> findAllSubscribedGroups()
	{
		return gxsJournalGroupRepository.findAllBySubscribedIsTrue();
	}

	public List<JournalGroupItem> findAllGroups(Set<GxsId> gxsIds)
	{
		return gxsJournalGroupRepository.findAllByGxsIdIn(gxsIds);
	}

	public List<JournalMessageItem> findAllMessagesInGroupSince(GxsId gxsId, Instant since)
	{
		return gxsJournalMessageRepository.findAllByGxsIdAndPublishedAfterAndHiddenFalse(gxsId, since);
	}

	public List<JournalMessageItem> findAllMessagesIncludingOlds(GxsId gxsId, Set<MsgId> msgIds)
	{
		return gxsJournalMessageRepository.findAllByGxsIdAndMsgIdIn(gxsId, msgIds);
	}

	public List<GxsMessageItem> findAllMessagesVotesAndCommentsIncludingOlds(GxsId gxsId, Set<MsgId> msgIds)
	{
		var messages = findAllMessagesIncludingOlds(gxsId, msgIds);
		var votes = gxsVoteMessageRepository.findAllByGxsIdAndMsgIdIn(gxsId, msgIds);
		var comments = gxsCommentMessageRepository.findAllByGxsIdAndMsgIdIn(gxsId, msgIds);

		var result = new ArrayList<GxsMessageItem>();
		result.addAll(messages);
		result.addAll(votes);
		result.addAll(comments);
		return result;
	}

	public int countVotes(MsgId parentMsgId, VoteMessageItem.Type type)
	{
		return (int) gxsVoteMessageRepository.countByParentMsgIdAndTypeAndHiddenIsFalse(parentMsgId, type);
	}

	public Page<JournalMessageItem> findAllArticlesByTitle(Pageable pageable, String title)
	{
		return gxsJournalMessageRepository.findAllByHiddenFalseAndTitleContainingIgnoreCase(pageable, title);
	}

	public List<JournalMessageItem> findLikedArticleMessages(GxsId ownGxsId)
	{
		var votes = gxsVoteMessageRepository.findAllByAuthorGxsIdAndTypeAndHiddenIsFalse(ownGxsId, VoteMessageItem.Type.UP);
		var msgIds = votes.stream().map(VoteMessageItem::getParentMsgId).filter(Objects::nonNull).collect(Collectors.toSet());
		if (msgIds.isEmpty())
		{
			return List.of();
		}
		return gxsJournalMessageRepository.findAllByMsgIdInAndHiddenFalse(msgIds);
	}

	public List<JournalMessageItem> findAllByIds(List<Long> ids)
	{
		return gxsJournalMessageRepository.findAllById(ids);
	}

	public Page<JournalMessageItem> findAllArticles(Pageable pageable)
	{
		return gxsJournalMessageRepository.findAllByHiddenFalse(pageable);
	}

	public Page<JournalMessageItem> findAllArticlesByGroup(GxsId gxsId, Pageable pageable)
	{
		return gxsJournalMessageRepository.findAllByGxsIdAndHiddenFalse(gxsId, pageable);
	}

	public Optional<JournalMessageItem> findMessageById(long id)
	{
		return gxsJournalMessageRepository.findById(id);
	}

	public int getUnreadCount(long groupId)
	{
		var journalGroupItem = gxsJournalGroupRepository.findById(groupId).orElseThrow();
		return gxsJournalMessageRepository.countUnreadMessages(journalGroupItem.getGxsId());
	}

	@Transactional
	public long createJournalGroup(GxsId identity, String name, String description, MultipartFile imageFile) throws IOException
	{
		var group = createGroup(name, true);
		group.setDescription(description);

		if (imageFile != null && !imageFile.isEmpty())
		{
			group.setImage(GxsUtils.getScaledGroupImage(imageFile, GxsGroupConstants.IMAGE_SIDE_SIZE));
		}

		if (identity != null)
		{
			group.setAuthorGxsId(identity);
		}

		group.setCircleType(GxsCircleType.PUBLIC);
		group.setSignatureFlags(Set.of(GxsSignatureFlags.NONE_REQUIRED, GxsSignatureFlags.AUTHENTICATION_REQUIRED));
		group.setDiffusionFlags(EnumSet.of(GxsPrivacyFlags.PUBLIC));

		group.setSubscribed(true);

		group = saveJournal(group);

		journalNotificationService.addOrUpdateGroups(List.of(group));

		return group.getId();
	}

	@Transactional
	public void updateJournalGroup(long groupId, String name, String description, MultipartFile imageFile, boolean updateImage) throws IOException
	{
		var journalGroupItem = gxsJournalGroupRepository.findById(groupId).orElseThrow();
		journalGroupItem.setName(name);
		journalGroupItem.setDescription(description);
		if (updateImage)
		{
			if (imageFile != null && !imageFile.isEmpty())
			{
				journalGroupItem.setImage(GxsUtils.getScaledGroupImage(imageFile, GxsGroupConstants.IMAGE_SIDE_SIZE));
			}
			else
			{
				journalGroupItem.setImage(null);
			}
		}

		journalGroupItem = saveJournal(journalGroupItem);
		journalNotificationService.addOrUpdateGroups(List.of(journalGroupItem));
	}

	private JournalGroupItem saveJournal(JournalGroupItem journalGroupItem)
	{
		signGroupIfNeeded(journalGroupItem);
		var savedJournal = gxsJournalGroupRepository.save(journalGroupItem);
		gxsHelperService.setLastServiceGroupsUpdateNow(GXS_JOURNALS);
		peerConnectionManager.doForAllPeers(this::sendSyncNotification, this);
		return savedJournal;
	}

	@Transactional
	public long createJournalMessage(IdentityGroupItem author, long journalId, String title, String content, MultipartFile imageFile) throws IOException
	{
		var group = gxsJournalGroupRepository.findById(journalId).orElseThrow();

		var builder = new MessageBuilder(group, author, title);

		if (StringUtils.isNotBlank(content))
		{
			builder.getMessageItem().setContent(content);
		}

		if (imageFile != null && !imageFile.isEmpty())
		{
			if (imageFile.getSize() >= IMAGE_MAX_INPUT_SIZE)
			{
				throw new IllegalArgumentException("Journal message image size is bigger than " + IMAGE_MAX_INPUT_SIZE + " bytes");
			}

			var image = ImageUtils.limitMaximumImageSize(ImageIO.read(imageFile.getInputStream()), IMAGE_MESSAGE_WIDTH * IMAGE_MESSAGE_HEIGHT);
			var imageOut = new ByteArrayOutputStream();
			if (!ImageUtils.writeImageAsJpeg(image, MAXIMUM_GXS_MESSAGE_SIZE - title.length() - (content == null ? 0 : content.length()), imageOut))
			{
				throw new IllegalArgumentException("Couldn't write the image. Unsupported format?");
			}

			var data = imageOut.toByteArray();
			builder.getMessageItem().setImage(data);
			builder.getMessageItem().setImageWidth(image.getWidth());
			builder.getMessageItem().setImageHeight(image.getHeight());
		}

		var journalMessageItem = saveMessage(builder);

		journalNotificationService.addOrUpdateMessages(List.of(journalMessageItem));

		peerConnectionManager.doForAllPeers(this::sendSyncNotification, this);

		return journalMessageItem.getId();
	}

	private JournalMessageItem saveMessage(MessageBuilder messageBuilder)
	{
		var journalMessageItem = messageBuilder.build();

		journalMessageItem.setId(gxsJournalMessageRepository.findByGxsIdAndMsgId(journalMessageItem.getGxsId(), journalMessageItem.getMsgId()).orElse(journalMessageItem).getId());
		var savedMessage = gxsJournalMessageRepository.save(journalMessageItem);
		markOriginalMessageAsHidden(List.of(savedMessage));
		var journalGroupItem = gxsJournalGroupRepository.findByGxsId(journalMessageItem.getGxsId()).orElseThrow();
		journalGroupItem.setLastUpdated(Instant.now());
		gxsJournalGroupRepository.save(journalGroupItem);
		return savedMessage;
	}

	@Transactional
	public void subscribeToJournalGroup(long id)
	{
		var journalGroupItem = findById(id).orElseThrow();
		journalGroupItem.setSubscribed(true);
		gxsHelperService.setLastServiceGroupsUpdateNow(GXS_JOURNALS);
	}

	@Transactional
	public void unsubscribeFromJournalGroup(long id)
	{
		var journalGroupItem = findById(id).orElseThrow();
		journalGroupItem.setSubscribed(false);
	}

	@Transactional
	public void setMessageReadState(long messageId, boolean read)
	{
		var message = gxsJournalMessageRepository.findById(messageId).orElseThrow();
		message.setRead(read);
		var group = gxsJournalGroupRepository.findByGxsId(message.getGxsId()).orElseThrow();
		journalNotificationService.setMessageReadState(group.getId(), message.getId(), read);
	}

	@Transactional
	public void setAllGroupMessagesReadState(long groupId, boolean read)
	{
		var group = gxsJournalGroupRepository.findById(groupId).orElseThrow();
		gxsJournalMessageRepository.setAllGroupMessagesReadState(group.getGxsId(), read);
		journalNotificationService.setGroupMessagesReadState(groupId, read);
	}

	@Transactional
	public long createComment(IdentityGroupItem author, long articleMessageId, String comment) throws IOException
	{
		var article = gxsJournalMessageRepository.findById(articleMessageId).orElseThrow();

		var commentItem = new CommentMessageItem(article.getGxsId(), "");
		commentItem.setComment(comment);
		commentItem.setAuthorGxsId(author.getGxsId());
		commentItem.setParentMsgId(article.getMsgId());
		commentItem.setOriginalMsgId(article.getMsgId());

		signChildMessage(commentItem, author);

		var saved = gxsHelperService.saveComment(commentItem, this::onCommentReceived).orElseThrow();
		peerConnectionManager.doForAllPeers(peer -> sendGxsMessages(peer, List.of(saved)), this);
		journalNotificationService.addOrUpdateComments(List.of(saved));
		return saved.getId();
	}

	@Transactional
	public long createVote(IdentityGroupItem author, long articleMessageId, VoteMessageItem.Type clicked) throws IOException
	{
		var article = gxsJournalMessageRepository.findById(articleMessageId).orElseThrow();
		var parentMsgId = article.getMsgId();

		// A user can only have a single active vote per article. Find the existing one
		// (there should be at most one) so we can hide it before applying the new choice.
		var current = gxsVoteMessageRepository.findAllByParentMsgIdAndAuthorGxsIdAndHiddenIsFalse(parentMsgId, author.getGxsId()).stream().findFirst();
		var currentType = current.map(VoteMessageItem::getType).orElse(VoteMessageItem.Type.NONE);

		// Toggle semantics:
		//   - clicking the vote you already set removes it (NONE)
		//   - clicking the other vote switches to it (the previous one is hidden)
		//   - clicking with NONE explicitly clears any existing vote
		var finalType = (clicked == VoteMessageItem.Type.NONE || currentType == clicked)
				? VoteMessageItem.Type.NONE
				: clicked;

		current.ifPresent(vote -> {
			vote.setHidden(true);
			var saved = gxsVoteMessageRepository.save(vote);
			journalNotificationService.addOrUpdateVotes(List.of(saved));
		});

		if (finalType == VoteMessageItem.Type.NONE)
		{
			return articleMessageId;
		}

		var voteItem = new VoteMessageItem(article.getGxsId(), "");
		voteItem.setType(finalType);
		voteItem.setAuthorGxsId(author.getGxsId());
		voteItem.setParentMsgId(parentMsgId);
		voteItem.setOriginalMsgId(parentMsgId);

		signChildMessage(voteItem, author);

		var saved = gxsHelperService.saveVote(voteItem, this::onVoteReceived).orElseThrow();
		peerConnectionManager.doForAllPeers(peer -> sendGxsMessages(peer, List.of(saved)), this);
		journalNotificationService.addOrUpdateVotes(List.of(saved));
		return saved.getId();
	}

	private <T extends GxsMessageItem> void signChildMessage(T item, IdentityGroupItem author) throws IOException
	{
		item.updatePublished();
		var data = io.xeres.app.xrs.item.ItemUtils.serializeItemForSignature(item, this);
		var md = new Sha1MessageDigest();
		md.update(data);
		item.setMsgId(new MsgId(md.getBytes()));
		var signature = RSA.sign(author.getAdminPrivateKey(), data);
		item.setAuthorSignature(signature);
	}

	@Override
	public void shutdown()
	{
		journalNotificationService.shutdown();
	}
}
