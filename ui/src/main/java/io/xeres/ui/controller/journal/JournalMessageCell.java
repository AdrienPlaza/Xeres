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
 * along with Xeres.  If not see <http://www.gnu.org/licenses/>.
 */

package io.xeres.ui.controller.journal;

import io.xeres.common.util.RemoteUtils;
import io.xeres.ui.client.GeneralClient;
import io.xeres.ui.model.journal.JournalMessage;
import io.xeres.ui.support.util.DateUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.ByteArrayInputStream;

import static io.xeres.common.rest.PathConfig.JOURNALS_PATH;

class JournalMessageCell extends ListCell<JournalMessage>
{
	private final GeneralClient generalClient;

	JournalMessageCell(GeneralClient generalClient)
	{
		this.generalClient = generalClient;
	}

	@Override
	protected void updateItem(JournalMessage item, boolean empty)
	{
		super.updateItem(item, empty);

		if (empty || item == null)
		{
			setGraphic(null);
			return;
		}

		var publication = new Label(item.getGroupName() == null ? "" : item.getGroupName());
		publication.setStyle("-fx-font-weight: bold; -fx-text-fill: -color-accent-fg;");

		var title = new Label(item.getTitle() == null ? "" : item.getTitle());
		title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

		var meta = new Label((item.getAuthorName() == null ? "" : item.getAuthorName())
				+ "  •  "
				+ (item.getPublished() != null ? DateUtils.DATE_TIME_FORMAT.format(item.getPublished()) : ""));
		meta.setStyle("-fx-text-fill: -color-fg-muted;");

		var snippet = new Label(item.getSnippet() == null ? "" : item.getSnippet());
		snippet.setWrapText(true);

		var counts = new Label(formatCounts(item));
		counts.setStyle("-fx-text-fill: -color-fg-muted;");

		var textBox = new VBox(3, publication, title, meta, snippet, counts);
		textBox.setPadding(new Insets(6));
		HBox.setHgrow(textBox, Priority.ALWAYS);

		var hbox = new HBox(8, textBox);

		setGraphic(hbox);

		if (item.hasImage())
		{
			var url = RemoteUtils.getControlUrl() + JOURNALS_PATH + "/messages/" + item.getId() + "/image";
			generalClient.getImage(url)
					.doOnSuccess(bytes -> {
						if (bytes != null && bytes.length > 0 && getItem() == item)
						{
							Platform.runLater(() -> {
								var imageView = new ImageView(new Image(new ByteArrayInputStream(bytes)));
								imageView.setFitWidth(96);
								imageView.setFitHeight(96);
								imageView.setPreserveRatio(true);
								hbox.getChildren().add(imageView);
							});
						}
					})
					.subscribe();
		}
	}

	private static String formatCounts(JournalMessage item)
	{
		var liked = "UP".equals(item.getMyVote());
		var disliked = "DOWN".equals(item.getMyVote());
		return "👍 " + item.getLikeCount() + (liked ? " (vous)" : "")
				+ "   👎 " + item.getDislikeCount() + (disliked ? " (vous)" : "")
				+ "   💬 " + item.getCommentCount()
				+ (item.isFavorited() ? "   ★" : "");
	}
}
