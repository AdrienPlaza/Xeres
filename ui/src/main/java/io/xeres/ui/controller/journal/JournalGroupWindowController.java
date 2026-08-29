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
import io.xeres.ui.controller.WindowController;
import io.xeres.ui.custom.ImageSelectorView;
import io.xeres.ui.support.util.ChooserUtils;
import io.xeres.ui.support.util.UiUtils;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import net.rgielen.fxweaver.core.FxmlView;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ResourceBundle;

import static io.xeres.ui.support.util.UiUtils.getWindow;

@Component
@FxmlView(value = "/view/journal/journal_group_view.fxml")
public class JournalGroupWindowController implements WindowController
{
	@FXML
	private TextField name;

	@FXML
	private TextField description;

	@FXML
	private ImageSelectorView postLogo;

	@FXML
	private Button send;

	private final JournalClient journalClient;
	private final ResourceBundle bundle;

	public JournalGroupWindowController(JournalClient journalClient, ResourceBundle bundle)
	{
		this.journalClient = journalClient;
		this.bundle = bundle;
	}

	@Override
	public void initialize()
	{
		postLogo.setOnSelectAction(this::selectImage);
		postLogo.setOnDeleteAction(this::clearImage);

		Platform.runLater(() -> name.requestFocus());

		name.setOnKeyTyped(_ -> checkSendable());
		send.setOnAction(_ -> createJournal());
		checkSendable();
	}

	private void checkSendable()
	{
		send.setDisable(StringUtils.isBlank(name.getText()));
	}

	private void createJournal()
	{
		send.setDisable(true);
		journalClient.createJournalGroup(name.getText(), description.getText(), postLogo.getFile())
				.doOnSuccess(_ -> Platform.runLater(() -> UiUtils.closeWindow(send)))
				.doOnError(UiUtils::webAlertError)
				.doFinally(_ -> send.setDisable(false))
				.subscribe();
	}

	private void selectImage(ActionEvent event)
	{
		var fileChooser = new FileChooser();
		fileChooser.setTitle(bundle.getString("journal.group.image"));
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
