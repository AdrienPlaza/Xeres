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

import io.xeres.common.util.OsUtils;
import io.xeres.ui.client.JournalClient;
import io.xeres.ui.client.LocationClient;
import io.xeres.ui.controller.WindowController;
import io.xeres.ui.custom.EditorView;
import io.xeres.ui.custom.ImageSelectorView;
import io.xeres.ui.support.markdown.MarkdownService;
import io.xeres.ui.support.util.ChooserUtils;
import io.xeres.ui.support.util.UiUtils;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import net.rgielen.fxweaver.core.FxmlView;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ResourceBundle;

import static io.xeres.ui.support.util.UiUtils.getWindow;

@Component
@FxmlView(value = "/view/journal/journal_article_view.fxml")
public class JournalArticleWindowController implements WindowController
{
	@FXML
	private Label groupName;

	@FXML
	private TextField title;

	@FXML
	private ImageSelectorView postLogo;

	@FXML
	private EditorView editorView;

	@FXML
	private Button send;

	private long groupId;

	private final JournalClient journalClient;
	private final LocationClient locationClient;
	private final MarkdownService markdownService;
	private final ResourceBundle bundle;

	public JournalArticleWindowController(JournalClient journalClient, LocationClient locationClient, MarkdownService markdownService, ResourceBundle bundle)
	{
		this.journalClient = journalClient;
		this.locationClient = locationClient;
		this.markdownService = markdownService;
		this.bundle = bundle;
	}

	@Override
	public void initialize()
	{
		postLogo.setOnSelectAction(this::selectImage);
		postLogo.setOnDeleteAction(this::clearImage);

		Platform.runLater(() -> title.requestFocus());

		editorView.setInputContextMenu(locationClient);
		editorView.setMarkdownService(markdownService);
		title.setOnKeyTyped(_ -> checkSendable());

		send.setOnAction(_ -> postArticle());
		checkSendable();
	}

	@Override
	public void onShown()
	{
		var userData = UiUtils.getUserData(title);
		if (userData == null)
		{
			throw new IllegalArgumentException("Missing group id");
		}
		groupId = (long) userData;

		journalClient.getGroups()
				.filter(group -> group.getId() == groupId)
				.next()
				.doOnSuccess(group -> Platform.runLater(() -> groupName.setText(group != null ? group.getName() : "")))
				.doOnError(UiUtils::webAlertError)
				.subscribe();
	}

	private void checkSendable()
	{
		send.setDisable(StringUtils.isBlank(title.getText()));
	}

	private void postArticle()
	{
		send.setDisable(true);
		journalClient.createArticle(groupId, title.getText(), editorView.getText(), postLogo.getFile())
				.doOnSuccess(_ -> Platform.runLater(() -> UiUtils.closeWindow(send)))
				.doOnError(UiUtils::webAlertError)
				.doFinally(_ -> send.setDisable(false))
				.subscribe();
	}

	private void selectImage(ActionEvent event)
	{
		var fileChooser = new FileChooser();
		fileChooser.setTitle(bundle.getString("journal.article.image"));
		ChooserUtils.setInitialDirectory(fileChooser, OsUtils.getDownloadDir());
		ChooserUtils.setSupportedLoadImageFormats(fileChooser);
		var selectedFile = fileChooser.showOpenDialog(getWindow(event));
		postLogo.setFile(selectedFile);
	}

	private void clearImage(ActionEvent event)
	{
		postLogo.setImage(null);
	}
}
