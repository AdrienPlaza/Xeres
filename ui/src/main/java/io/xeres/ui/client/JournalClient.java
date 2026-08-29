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

package io.xeres.ui.client;

import io.xeres.common.dto.journal.JournalCommentDTO;
import io.xeres.common.dto.journal.JournalGroupDTO;
import io.xeres.common.dto.journal.JournalMessageDTO;
import io.xeres.common.dto.journal.JournalVoteDTO;
import io.xeres.common.events.StartupEvent;
import io.xeres.common.util.RemoteUtils;
import io.xeres.ui.model.journal.JournalComment;
import io.xeres.ui.model.journal.JournalGroup;
import io.xeres.ui.model.journal.JournalMapper;
import io.xeres.ui.model.journal.JournalMessage;
import io.xeres.ui.support.util.ClientUtils;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.util.List;

import static io.xeres.common.rest.PathConfig.JOURNALS_PATH;
import static io.xeres.ui.model.journal.JournalMapper.fromDTO;
import static io.xeres.ui.model.journal.JournalMapper.fromDTOs;

@Component
public class JournalClient
{
	private final WebClient.Builder webClientBuilder;

	private WebClient webClient;

	public JournalClient(WebClient.Builder webClientBuilder)
	{
		this.webClientBuilder = webClientBuilder;
	}

	@EventListener
	public void init(@SuppressWarnings("unused") StartupEvent event)
	{
		webClient = webClientBuilder.clone()
				.baseUrl(RemoteUtils.getControlUrl() + JOURNALS_PATH)
				.build();
	}

	public Flux<JournalGroup> getGroups()
	{
		return webClient.get()
				.uri("/groups")
				.retrieve()
				.bodyToFlux(JournalGroupDTO.class)
				.map(JournalMapper::fromDTO);
	}

	public Mono<Long> createJournalGroup(String name, String description, File image)
	{
		var builder = ClientUtils.createGroupBuilder(name, description, image);

		return webClient.post()
				.uri("/groups")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(BodyInserters.fromMultipartData(builder.build()))
				.exchangeToMono(ClientUtils::getCreatedId);
	}

	public Mono<PaginatedResponse<JournalMessage>> getFeed(int page, int size)
	{
		return webClient.get()
				.uri(uriBuilder -> uriBuilder
						.path("/articles")
						.queryParam("page", page)
						.queryParam("size", size)
						.queryParam("sort", "published,desc")
						.build())
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<PaginatedResponse<JournalMessageDTO>>()
				{
				})
				.map(JournalMapper::fromDTO);
	}

	public Mono<PaginatedResponse<JournalMessage>> getLikedArticles(int page, int size)
	{
		return webClient.get()
				.uri(uriBuilder -> uriBuilder
						.path("/articles/liked")
						.queryParam("page", page)
						.queryParam("size", size)
						.queryParam("sort", "published,desc")
						.build())
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<PaginatedResponse<JournalMessageDTO>>()
				{
				})
				.map(JournalMapper::fromDTO);
	}

	public Mono<List<JournalMessage>> getFavoritedArticles()
	{
		return webClient.get()
				.uri("/favorites")
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<JournalMessageDTO>>()
				{
				})
				.map(JournalMapper::fromDTOs);
	}

	public Mono<List<JournalMessage>> getMyCommentedArticles()
	{
		return webClient.get()
				.uri("/my-comments")
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<JournalMessageDTO>>()
				{
				})
				.map(JournalMapper::fromDTOs);
	}

	public Mono<JournalMessage> getMessage(long messageId)
	{
		return webClient.get()
				.uri("/messages/{messageId}", messageId)
				.retrieve()
				.bodyToMono(JournalMessageDTO.class)
				.map(JournalMapper::fromDTO);
	}

	public Mono<List<JournalComment>> getComments(long messageId)
	{
		return webClient.get()
				.uri("/messages/{messageId}/comments", messageId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<JournalCommentDTO>>()
				{
				})
				.map(list -> list.stream().map(JournalMapper::fromDTO).toList());
	}

	public Mono<JournalVoteDTO> getVotes(long messageId)
	{
		return webClient.get()
				.uri("/messages/{messageId}/votes", messageId)
				.retrieve()
				.bodyToMono(JournalVoteDTO.class);
	}

	public Mono<Long> createArticle(long groupId, String title, String content, File image)
	{
		var builder = new org.springframework.http.client.MultipartBodyBuilder();
		builder.part("journalId", groupId);
		if (title == null || title.isBlank())
		{
			throw new IllegalArgumentException("Title is required");
		}
		builder.part("title", title);
		if (content != null && !content.isBlank())
		{
			builder.part("content", content);
		}
		if (image != null)
		{
			builder.part("image", new org.springframework.core.io.FileSystemResource(image));
		}

		return webClient.post()
				.uri("/messages")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(BodyInserters.fromMultipartData(builder.build()))
				.exchangeToMono(ClientUtils::getCreatedId);
	}

	public Mono<Void> createComment(long messageId, String comment)
	{
		var request = new io.xeres.common.dto.journal.CreateJournalCommentRequest(comment);

		return webClient.post()
				.uri("/messages/{messageId}/comments", messageId)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(Void.class);
	}

	public Mono<Void> createVote(long messageId, String vote)
	{
		var request = new io.xeres.common.dto.journal.JournalVoteRequest(vote);

		return webClient.post()
				.uri("/messages/{messageId}/votes", messageId)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(Void.class);
	}

	public Mono<Void> setFavorite(long messageId, boolean favorite)
	{
		return webClient.put()
				.uri(uriBuilder -> uriBuilder
						.path("/messages/{messageId}/favorite")
						.queryParam("favorite", favorite)
						.build(messageId))
				.retrieve()
				.bodyToMono(Void.class);
	}
}
