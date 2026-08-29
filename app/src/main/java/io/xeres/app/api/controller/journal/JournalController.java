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

package io.xeres.app.api.controller.journal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.xeres.app.database.model.gxs.GxsGroupItem;
import io.xeres.app.database.model.journal.JournalCommentHistory;
import io.xeres.app.database.model.journal.JournalFavorite;
import io.xeres.app.database.model.journal.JournalMapper;
import io.xeres.app.database.repository.JournalCommentHistoryRepository;
import io.xeres.app.database.repository.JournalFavoriteRepository;
import io.xeres.app.service.IdentityService;
import io.xeres.app.service.JournalMessageService;
import io.xeres.app.service.UnHtmlService;
import io.xeres.app.xrs.common.VoteMessageItem;
import io.xeres.app.xrs.service.identity.item.IdentityGroupItem;
import io.xeres.app.xrs.service.journal.JournalRsService;
import io.xeres.app.xrs.service.journal.item.JournalGroupItem;
import io.xeres.app.xrs.service.journal.item.JournalMessageItem;
import io.xeres.common.dto.journal.CreateJournalCommentRequest;
import io.xeres.common.dto.journal.JournalCommentDTO;
import io.xeres.common.dto.journal.JournalGroupDTO;
import io.xeres.common.dto.journal.JournalMessageDTO;
import io.xeres.common.dto.journal.JournalVoteDTO;
import io.xeres.common.dto.journal.JournalVoteRequest;
import io.xeres.common.id.GxsId;
import io.xeres.common.id.MsgId;
import io.xeres.common.util.image.ImageUtils;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.xeres.app.database.model.journal.JournalMapper.toDTOs;
import static io.xeres.common.rest.PathConfig.JOURNALS_PATH;

@RestController
@Tag(name = "Journals", description = "Journals (friend-to-friend articles)")
@RequestMapping(value = JOURNALS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class JournalController
{
	private final JournalRsService journalRsService;
	private final IdentityService identityService;
	private final JournalMessageService journalMessageService;
	private final UnHtmlService unHtmlService;
	private final JournalFavoriteRepository journalFavoriteRepository;
	private final JournalCommentHistoryRepository journalCommentHistoryRepository;

	public JournalController(JournalRsService journalRsService, IdentityService identityService, JournalMessageService journalMessageService, UnHtmlService unHtmlService, JournalFavoriteRepository journalFavoriteRepository, JournalCommentHistoryRepository journalCommentHistoryRepository)
	{
		this.journalRsService = journalRsService;
		this.identityService = identityService;
		this.journalMessageService = journalMessageService;
		this.unHtmlService = unHtmlService;
		this.journalFavoriteRepository = journalFavoriteRepository;
		this.journalCommentHistoryRepository = journalCommentHistoryRepository;
	}

	@GetMapping("/groups")
	@Operation(summary = "Gets the list of journals")
	public List<JournalGroupDTO> getJournalGroups()
	{
		var ownGxsId = identityService.getOwnIdentity().getGxsId();
		return toDTOs(journalRsService.findAllGroups(), ownGxsId);
	}

	@PostMapping(value = "/groups", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Creates a journal")
	@ApiResponse(responseCode = "201", description = "Journal created successfully")
	public ResponseEntity<Void> createJournalGroup(@RequestParam("name") String name,
	                                              @RequestParam(value = "description", required = false) String description,
	                                              @RequestParam(value = "image", required = false) MultipartFile imageFile) throws IOException
	{
		var ownIdentity = identityService.getOwnIdentity();
		var id = journalRsService.createJournalGroup(ownIdentity.getGxsId(), name, description, imageFile);

		var location = ServletUriComponentsBuilder.fromCurrentRequest().replacePath(JOURNALS_PATH + "/groups/{id}").buildAndExpand(id).toUri();
		return ResponseEntity.created(location).build();
	}

	@PutMapping(value = "/groups/{groupId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Updates a journal")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void updateJournalGroup(@PathVariable long groupId,
	                               @RequestParam("name") String name,
	                               @RequestParam(value = "description", required = false) String description,
	                               @RequestParam(value = "image", required = false) MultipartFile imageFile,
	                               @RequestParam(value = "updateImage", defaultValue = "false") boolean updateImage) throws IOException
	{
		journalRsService.updateJournalGroup(groupId, name, description, imageFile, updateImage);
	}

	@GetMapping(value = "/groups/{id}/image", produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE})
	@Operation(summary = "Returns a journal's image")
	public ResponseEntity<InputStreamResource> downloadJournalGroupImage(@PathVariable long id)
	{
		var group = journalRsService.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		var imageType = ImageUtils.getImageMimeType(group.getImage());
		if (imageType == null)
		{
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.ok()
				.contentLength(group.getImage().length)
				.contentType(imageType)
				.body(new InputStreamResource(new ByteArrayInputStream(group.getImage())));
	}

	@GetMapping("/groups/{groupId}")
	@Operation(summary = "Gets the details of a journal")
	public JournalGroupDTO getJournalGroupById(@PathVariable long groupId)
	{
		var ownGxsId = identityService.getOwnIdentity().getGxsId();
		return io.xeres.app.database.model.journal.JournalMapper.toDTO(journalRsService.findById(groupId).orElseThrow(), ownGxsId);
	}

	@GetMapping("/groups/{groupId}/unread-count")
	@Operation(summary = "Get the unread count of a journal")
	public int getJournalUnreadCount(@PathVariable long groupId)
	{
		return journalRsService.getUnreadCount(groupId);
	}

	@PutMapping("/groups/{groupId}/subscription")
	@Operation(summary = "Subscribes to a journal")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void subscribeToJournalGroup(@PathVariable long groupId)
	{
		journalRsService.subscribeToJournalGroup(groupId);
	}

	@DeleteMapping("/groups/{groupId}/subscription")
	@Operation(summary = "Unsubscribes from a journal")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unsubscribeFromJournalGroup(@PathVariable long groupId)
	{
		journalRsService.unsubscribeFromJournalGroup(groupId);
	}

	@PutMapping("/groups/{groupId}/read")
	@Operation(summary = "Sets all messages in the group as read or unread")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void setAllGroupMessagesReadState(@PathVariable long groupId, @RequestParam("read") Boolean read)
	{
		journalRsService.setAllGroupMessagesReadState(groupId, read);
	}

	@GetMapping("/groups/{groupId}/messages")
	@Operation(summary = "Gets the messages (articles) in a journal")
	public Page<JournalMessageDTO> getJournalMessages(@PathVariable long groupId, @PageableDefault(size = 50, sort = "published", direction = Sort.Direction.DESC) Pageable pageable)
	{
		var group = journalRsService.findById(groupId).orElseThrow();
		var messages = journalRsService.findAllArticlesByGroup(group.getGxsId(), pageable);
		return buildFeed(messages);
	}

	@GetMapping("/articles")
	@Operation(summary = "Gets the aggregated feed of all articles (optionally filtered by title)")
	public Page<JournalMessageDTO> getFeed(@RequestParam(value = "search", required = false) String search,
	                                       @PageableDefault(size = 50, sort = "published", direction = Sort.Direction.DESC) Pageable pageable)
	{
		var messages = search == null || search.isBlank()
				? journalRsService.findAllArticles(pageable)
				: journalRsService.findAllArticlesByTitle(pageable, search);
		return buildFeed(messages);
	}

	@GetMapping("/articles/liked")
	@Operation(summary = "Gets the articles the user liked")
	public Page<JournalMessageDTO> getLikedFeed(@PageableDefault(size = 50, sort = "published", direction = Sort.Direction.DESC) Pageable pageable)
	{
		var ownGxsId = identityService.getOwnIdentity().getGxsId();
		var liked = journalRsService.findLikedArticleMessages(ownGxsId);
		return buildFeed(new PageImpl<>(liked, pageable, liked.size()));
	}

	@GetMapping("/favorites")
	@Operation(summary = "Gets the favorited articles")
	public List<JournalMessageDTO> getFavorites()
	{
		var ids = journalFavoriteRepository.findAllByFavoriteIsTrue().stream().map(JournalFavorite::getMessageId).toList();
		var messages = journalRsService.findAllByIds(ids);
		return buildFeedList(messages);
	}

	@GetMapping("/my-comments")
	@Operation(summary = "Gets the articles the user commented on")
	public List<JournalMessageDTO> getMyComments()
	{
		var ids = journalCommentHistoryRepository.findAllByOrderByPublishedDesc().stream()
				.map(JournalCommentHistory::getArticleMessageId)
				.distinct()
				.toList();
		var messages = journalRsService.findAllByIds(ids);
		return buildFeedList(messages);
	}

	@GetMapping("/messages/{messageId}")
	@Operation(summary = "Gets an article")
	public JournalMessageDTO getJournalMessage(@PathVariable long messageId)
	{
		var message = journalRsService.findMessageById(messageId).orElseThrow();
		return toSingleDTO(message, true);
	}

	@PostMapping(value = "/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Creates an article")
	@ApiResponse(responseCode = "201", description = "Article created successfully")
	public ResponseEntity<Void> createJournalMessage(@RequestParam("journalId") long journalId,
	                                                @RequestParam("title") String title,
	                                                @RequestParam(value = "content", required = false) String content,
	                                                @RequestParam(value = "image", required = false) MultipartFile imageFile) throws IOException
	{
		var ownIdentity = identityService.getOwnIdentity();
		var id = journalRsService.createJournalMessage(ownIdentity, journalId, title, content, imageFile);

		var location = ServletUriComponentsBuilder.fromCurrentRequest().replacePath(JOURNALS_PATH + "/messages/{id}").buildAndExpand(id).toUri();
		return ResponseEntity.created(location).build();
	}

	@GetMapping(value = "/messages/{id}/image", produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE})
	@Operation(summary = "Returns an article's image")
	public ResponseEntity<InputStreamResource> downloadJournalMessageImage(@PathVariable long id)
	{
		var message = journalRsService.findMessageById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		var imageType = ImageUtils.getImageMimeType(message.getImage());
		if (imageType == null)
		{
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.ok()
				.contentLength(message.getImage().length)
				.contentType(imageType)
				.body(new InputStreamResource(new ByteArrayInputStream(message.getImage())));
	}

	@PatchMapping("/messages")
	@Operation(summary = "Modifies an article read state")
	@ResponseStatus(HttpStatus.OK)
	public void setJournalMessageReadState(@RequestParam long messageId, @RequestParam boolean read)
	{
		journalRsService.setMessageReadState(messageId, read);
	}

	@GetMapping("/messages/{messageId}/comments")
	@Operation(summary = "Gets the comments of an article")
	public List<JournalCommentDTO> getComments(@PathVariable long messageId)
	{
		var message = journalRsService.findMessageById(messageId).orElseThrow();
		var comments = journalMessageService.findComments(message.getMsgId());
		var authors = identityService.findAll(comments.stream().map(io.xeres.app.xrs.common.CommentMessageItem::getAuthorGxsId).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(GxsGroupItem::getGxsId, IdentityGroupItem::getName));
		return comments.stream()
				.map(comment -> io.xeres.app.database.model.journal.JournalMapper.toDTO(comment, authors.getOrDefault(comment.getAuthorGxsId(), "")))
				.toList();
	}

	@PostMapping("/messages/{messageId}/comments")
	@Operation(summary = "Adds a comment to an article")
	@ResponseStatus(HttpStatus.CREATED)
	public void createComment(@PathVariable long messageId, @Valid @RequestBody CreateJournalCommentRequest request) throws IOException
	{
		var ownIdentity = identityService.getOwnIdentity();
		journalRsService.createComment(ownIdentity, messageId, request.comment());
		recordCommentHistory(messageId, request.comment());
	}

	@GetMapping("/messages/{messageId}/votes")
	@Operation(summary = "Gets the votes (like/dislike counts) of an article")
	public JournalVoteDTO getVotes(@PathVariable long messageId)
	{
		var message = journalRsService.findMessageById(messageId).orElseThrow();
		var likeCount = journalRsService.countVotes(message.getMsgId(), VoteMessageItem.Type.UP);
		var dislikeCount = journalRsService.countVotes(message.getMsgId(), VoteMessageItem.Type.DOWN);
		var ownGxsId = identityService.getOwnIdentity().getGxsId();
		var myVote = journalMessageService.getMyVotes(Set.of(message.getMsgId()), ownGxsId).getOrDefault(message.getMsgId(), "NONE");
		return new JournalVoteDTO(likeCount, dislikeCount, myVote);
	}

	@PostMapping("/messages/{messageId}/votes")
	@Operation(summary = "Votes (like/dislike) on an article")
	@ResponseStatus(HttpStatus.CREATED)
	public void createVote(@PathVariable long messageId, @Valid @RequestBody JournalVoteRequest request) throws IOException
	{
		var ownIdentity = identityService.getOwnIdentity();
		var type = switch (request.vote() == null ? "NONE" : request.vote().toUpperCase())
		{
			case "UP" -> VoteMessageItem.Type.UP;
			case "DOWN" -> VoteMessageItem.Type.DOWN;
			default -> VoteMessageItem.Type.NONE;
		};
		journalRsService.createVote(ownIdentity, messageId, type);
	}

	@PutMapping("/messages/{messageId}/favorite")
	@Operation(summary = "Marks/unmarks an article as favorite")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void setFavorite(@PathVariable long messageId, @RequestParam boolean favorite)
	{
		journalFavoriteRepository.save(new JournalFavorite(messageId, favorite));
	}

	private void recordCommentHistory(long articleMessageId, String comment)
	{
		journalCommentHistoryRepository.save(new JournalCommentHistory(articleMessageId, comment, Instant.now()));
		var all = journalCommentHistoryRepository.findAllByOrderByPublishedDesc();
		if (all.size() > 10)
		{
			journalCommentHistoryRepository.deleteAll(all.subList(10, all.size()));
		}
	}

	private Map<GxsId, String> groupNames()
	{
		return journalRsService.findAllGroups().stream()
				.collect(Collectors.toMap(JournalGroupItem::getGxsId, JournalGroupItem::getName, (a, b) -> a));
	}

	private JournalMessageDTO toSingleDTO(JournalMessageItem message, boolean withContent)
	{
		var ownGxsId = identityService.getOwnIdentity().getGxsId();
		var authorName = identityService.findByGxsId(message.getAuthorGxsId()).map(GxsGroupItem::getName).orElse(null);
		var likeCount = journalRsService.countVotes(message.getMsgId(), VoteMessageItem.Type.UP);
		var dislikeCount = journalRsService.countVotes(message.getMsgId(), VoteMessageItem.Type.DOWN);
		var myVote = journalMessageService.getMyVotes(Set.of(message.getMsgId()), ownGxsId).getOrDefault(message.getMsgId(), "NONE");
		var commentCount = journalMessageService.getCommentCounts(Set.of(message.getMsgId())).getOrDefault(message.getMsgId(), 0);
		var favorited = journalFavoriteRepository.existsByMessageIdAndFavoriteIsTrue(message.getId());
		var groupName = groupNames().getOrDefault(message.getGxsId(), "");
		return io.xeres.app.database.model.journal.JournalMapper.toDTO(
				unHtmlService,
				message,
				authorName,
				groupName,
				0L,
				0L,
				myVote,
				commentCount,
				favorited,
				withContent
		);
	}

	private Page<JournalMessageDTO> buildFeed(Page<JournalMessageItem> messages)
	{
		var ownGxsId = identityService.getOwnIdentity().getGxsId();
		var authors = journalMessageService.getAuthorsMapFromMessages(messages.getContent());
		var msgIds = messages.getContent().stream().map(JournalMessageItem::getMsgId).collect(Collectors.toSet());
		var voteCounts = journalMessageService.getVoteCounts(msgIds);
		var myVotes = journalMessageService.getMyVotes(msgIds, ownGxsId);
		var commentCounts = journalMessageService.getCommentCounts(msgIds);
		var localIds = messages.getContent().stream().map(JournalMessageItem::getId).toList();
		var favoritedIds = journalMessageService.getFavoritedMessageIds(localIds);
		var groupNames = groupNames();

		var dtos = messages.getContent().stream()
				.map(message -> {
					var counts = voteCounts.getOrDefault(message.getMsgId(), new int[]{0, 0});
					var myVote = myVotes.getOrDefault(message.getMsgId(), "NONE");
					var commentCount = commentCounts.getOrDefault(message.getMsgId(), 0);
					var favorited = favoritedIds.contains(message.getId());
					var authorName = authors.getOrDefault(message.getAuthorGxsId(), IdentityGroupItem.EMPTY).getName();
					var groupName = groupNames.getOrDefault(message.getGxsId(), "");
					return io.xeres.app.database.model.journal.JournalMapper.toDTO(
							unHtmlService,
							message,
							authorName,
							groupName,
							0L,
							0L,
							myVote,
							commentCount,
							favorited,
							false
					);
				})
				.toList();

		return new PageImpl<>(dtos, messages.getPageable(), messages.getTotalElements());
	}

	private List<JournalMessageDTO> buildFeedList(List<JournalMessageItem> messages)
	{
		var ownGxsId = identityService.getOwnIdentity().getGxsId();
		var sorted = messages.stream()
				.sorted(Comparator.comparing(JournalMessageItem::getPublished).reversed())
				.toList();
		var authors = journalMessageService.getAuthorsMapFromMessages(sorted);
		var msgIds = sorted.stream().map(JournalMessageItem::getMsgId).collect(Collectors.toSet());
		var voteCounts = journalMessageService.getVoteCounts(msgIds);
		var myVotes = journalMessageService.getMyVotes(msgIds, ownGxsId);
		var commentCounts = journalMessageService.getCommentCounts(msgIds);
		var localIds = sorted.stream().map(JournalMessageItem::getId).toList();
		var favoritedIds = journalMessageService.getFavoritedMessageIds(localIds);
		var groupNames = groupNames();

		return sorted.stream()
				.map(message -> {
					var counts = voteCounts.getOrDefault(message.getMsgId(), new int[]{0, 0});
					var myVote = myVotes.getOrDefault(message.getMsgId(), "NONE");
					var commentCount = commentCounts.getOrDefault(message.getMsgId(), 0);
					var favorited = favoritedIds.contains(message.getId());
					var authorName = authors.getOrDefault(message.getAuthorGxsId(), IdentityGroupItem.EMPTY).getName();
					var groupName = groupNames.getOrDefault(message.getGxsId(), "");
					return io.xeres.app.database.model.journal.JournalMapper.toDTO(
							unHtmlService,
							message,
							authorName,
							groupName,
							0L,
							0L,
							myVote,
							commentCount,
							favorited,
							false
					);
				})
				.toList();
	}
}
