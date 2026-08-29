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

package io.xeres.ui.controller.journal;

import io.xeres.common.util.RemoteUtils;
import io.xeres.ui.client.GeneralClient;
import io.xeres.ui.client.JournalClient;
import io.xeres.ui.controller.WindowController;
import io.xeres.ui.model.journal.JournalComment;
import io.xeres.ui.model.journal.JournalMessage;
import io.xeres.ui.support.contentline.Content;
import io.xeres.ui.support.markdown.MarkdownService;
import io.xeres.ui.support.util.UiUtils;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import net.rgielen.fxweaver.core.FxmlView;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.ResourceBundle;

import static io.xeres.common.rest.PathConfig.JOURNALS_PATH;
import static io.xeres.ui.support.util.UiUtils.getWindow;

@Component
@FxmlView(value = "/view/journal/journal_message_view.fxml")
public class JournalMessageWindowController implements WindowController
{
	@FXML
	private Label titleLabel;

	@FXML
	private Label sourceLabel;

	@FXML
	private Label metaLabel;

	@FXML
	private VBox contentBox;

	@FXML
	private ImageView imageView;

	@FXML
	private Button likeButton;

	@FXML
	private Button dislikeButton;

	@FXML
	private Button favoriteButton;

	@FXML
	private VBox commentsBox;

	@FXML
	private TextArea commentInput;

	@FXML
	private Button sendComment;

	private long messageId;

	private String myVote = "NONE";
	private boolean favorited;

	private static final String ACTIVE_STYLE = "-fx-font-weight: bold; -fx-text-fill: -color-accent-fg;";
	private static final String INACTIVE_STYLE = "";

	private final JournalClient journalClient;
	private final GeneralClient generalClient;
	private final MarkdownService markdownService;
	private final ResourceBundle bundle;

	public JournalMessageWindowController(JournalClient journalClient, GeneralClient generalClient, MarkdownService markdownService, ResourceBundle bundle)
	{
		this.journalClient = journalClient;
		this.generalClient = generalClient;
		this.markdownService = markdownService;
		this.bundle = bundle;
	}

	@Override
	public void initialize()
	{
		// Nothing to initialize upfront
	}

	@Override
	public void onShown()
	{
		var userData = UiUtils.getUserData(titleLabel);
		if (userData == null)
		{
			throw new IllegalArgumentException("Missing message id");
		}
		messageId = (long) userData;

		loadMessage();
		loadComments();

		likeButton.setOnAction(this::onLike);
		dislikeButton.setOnAction(this::onDislike);
		favoriteButton.setOnAction(this::onFavorite);
		sendComment.setOnAction(_ -> onSendComment());
	}

	private void loadMessage()
	{
		journalClient.getMessage(messageId)
				.doOnSuccess(message -> Platform.runLater(() -> showMessage(message)))
				.doOnError(UiUtils::webAlertError)
				.subscribe();
		journalClient.getVotes(messageId)
				.doOnSuccess(votes -> Platform.runLater(() -> updateVotes(votes.likeCount(), votes.dislikeCount(), votes.myVote())))
				.doOnError(UiUtils::webAlertError)
				.subscribe();
	}

	private void showMessage(JournalMessage message)
	{
		titleLabel.setText(message.getTitle() == null ? "" : message.getTitle());
		sourceLabel.setText(message.getGroupName() == null ? "" : message.getGroupName());
		metaLabel.setText((message.getAuthorName() == null ? "" : message.getAuthorName())
				+ "  •  "
				+ (message.getPublished() != null ? io.xeres.ui.support.util.DateUtils.DATE_TIME_FORMAT.format(message.getPublished()) : ""));

		contentBox.getChildren().clear();
		var rendered = markdownService.parse(StringUtils.defaultString(message.getContent()), EnumSet.noneOf(MarkdownService.Rendering.class));
		for (var content : rendered)
		{
			contentBox.getChildren().add(content.getNode());
		}

		if (message.hasImage())
		{
			var url = RemoteUtils.getControlUrl() + JOURNALS_PATH + "/messages/" + message.getId() + "/image";
			var bytes = generalClient.getImage(url).block();
			if (bytes != null)
			{
				imageView.setImage(new Image(new ByteArrayInputStream(bytes)));
				imageView.setFitWidth(Math.min(message.getImageWidth(), 480));
				imageView.setFitHeight(Math.min(message.getImageHeight(), 480));
				imageView.setPreserveRatio(true);
				imageView.setVisible(true);
				imageView.setManaged(true);
			}
		}
		else
		{
			imageView.setVisible(false);
			imageView.setManaged(false);
		}

		favorited = message.isFavorited();
		updateFavoriteButton();
	}

	private void updateFavoriteButton()
	{
		favoriteButton.setText((favorited ? "★ " : "☆ ") + bundle.getString("journal.message.favorite"));
		favoriteButton.setStyle(favorited ? ACTIVE_STYLE : INACTIVE_STYLE);
	}

	private void updateVotes(int likeCount, int dislikeCount, String myVote)
	{
		this.myVote = myVote;
		var up = "UP".equals(myVote);
		var down = "DOWN".equals(myVote);
		likeButton.setText("👍 " + likeCount);
		dislikeButton.setText("👎 " + dislikeCount);
		likeButton.setStyle(up ? ACTIVE_STYLE : INACTIVE_STYLE);
		dislikeButton.setStyle(down ? ACTIVE_STYLE : INACTIVE_STYLE);
	}

	private void onLike(ActionEvent event)
	{
		// The server applies the toggle: clicking the active vote removes it.
		vote("UP");
	}

	private void onDislike(ActionEvent event)
	{
		vote("DOWN");
	}

	private void vote(String direction)
	{
		journalClient.createVote(messageId, direction)
				.doOnSuccess(_ -> journalClient.getVotes(messageId)
						.doOnSuccess(votes -> Platform.runLater(() -> updateVotes(votes.likeCount(), votes.dislikeCount(), votes.myVote())))
						.doOnError(UiUtils::webAlertError)
						.subscribe())
				.doOnError(UiUtils::webAlertError)
				.subscribe();
	}

	private void onFavorite(ActionEvent event)
	{
		var next = !favorited;
		journalClient.setFavorite(messageId, next)
				.doOnSuccess(_ -> Platform.runLater(() -> {
					favorited = next;
					updateFavoriteButton();
				}))
				.doOnError(UiUtils::webAlertError)
				.subscribe();
	}

	private void onSendComment()
	{
		var text = commentInput.getText();
		if (StringUtils.isBlank(text))
		{
			return;
		}
		journalClient.createComment(messageId, text)
				.doOnSuccess(_ -> Platform.runLater(() -> commentInput.clear()))
				.doFinally(_ -> loadComments())
				.doOnError(UiUtils::webAlertError)
				.subscribe();
	}

	private void loadComments()
	{
		journalClient.getComments(messageId)
				.doOnSuccess(comments -> Platform.runLater(() -> showComments(comments)))
				.doOnError(UiUtils::webAlertError)
				.subscribe();
	}

	private void showComments(List<JournalComment> comments)
	{
		commentsBox.getChildren().clear();
		for (var comment : comments)
		{
			var meta = new Label((comment.getAuthorName() == null ? "" : comment.getAuthorName())
					+ "  •  "
					+ (comment.getPublished() != null ? io.xeres.ui.support.util.DateUtils.DATE_TIME_FORMAT.format(comment.getPublished()) : ""));
			meta.setStyle("-fx-text-fill: -color-fg-muted;");
			var body = new Label(comment.getContent() == null ? "" : comment.getContent());
			body.setWrapText(true);
			commentsBox.getChildren().add(new VBox(2, meta, body));
		}
	}
}
