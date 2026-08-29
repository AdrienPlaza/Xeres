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

import io.xeres.ui.client.GeneralClient;
import io.xeres.ui.client.JournalClient;
import io.xeres.ui.client.NotificationClient;
import io.xeres.ui.controller.Controller;
import io.xeres.ui.controller.WindowController;
import io.xeres.ui.model.journal.JournalGroup;
import io.xeres.ui.model.journal.JournalMessage;
import io.xeres.ui.support.util.UiUtils;
import io.xeres.ui.support.window.WindowManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import net.rgielen.fxweaver.core.FxmlView;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;



@Component
@FxmlView(value = "/view/journal/journal_view.fxml")
public class JournalViewController implements Controller
{
	private enum Filter
	{
		ALL,
		LIKED,
		FAVORITED,
		COMMENTED
	}

	@FXML
	private Button createJournal;

	@FXML
	private Button newArticle;

	@FXML
	private TextField searchField;

	@FXML
	private ToggleButton filterAll;

	@FXML
	private ToggleButton filterLiked;

	@FXML
	private ToggleButton filterFavorited;

	@FXML
	private ToggleButton filterCommented;

	@FXML
	private ListView<JournalMessage> articleList;

	@FXML
	private VBox emptyView;

	private final ObservableList<JournalMessage> articles = FXCollections.observableArrayList();
	private final FilteredList<JournalMessage> filteredArticles = new FilteredList<>(articles);

	private final ResourceBundle bundle;
	private final JournalClient journalClient;
	private final NotificationClient notificationClient;
	private final GeneralClient generalClient;
	private final WindowManager windowManager;

	private Filter currentFilter = Filter.ALL;
	private String searchText = "";

	private Disposable notificationDisposable;

	public JournalViewController(ResourceBundle bundle, JournalClient journalClient, NotificationClient notificationClient, GeneralClient generalClient, WindowManager windowManager)
	{
		this.bundle = bundle;
		this.journalClient = journalClient;
		this.notificationClient = notificationClient;
		this.generalClient = generalClient;
		this.windowManager = windowManager;
	}

	@Override
	public void initialize()
	{
		var toggleGroup = new ToggleGroup();
		filterAll.setToggleGroup(toggleGroup);
		filterLiked.setToggleGroup(toggleGroup);
		filterFavorited.setToggleGroup(toggleGroup);
		filterCommented.setToggleGroup(toggleGroup);
		filterAll.setSelected(true);

		articleList.setCellFactory(_ -> new JournalMessageCell(generalClient));
		articleList.setItems(filteredArticles);
		articleList.getSelectionModel().selectedItemProperty().addListener((_, _, newValue) -> {
			if (newValue != null)
			{
				windowManager.openJournalMessage(newValue.getId());
				Platform.runLater(() -> articleList.getSelectionModel().clearSelection());
			}
		});

		createJournal.setOnAction(_ -> windowManager.openJournalCreation(0L));
		newArticle.setOnAction(_ -> newArticle());

		filterAll.setOnAction(_ -> setFilter(Filter.ALL));
		filterLiked.setOnAction(_ -> setFilter(Filter.LIKED));
		filterFavorited.setOnAction(_ -> setFilter(Filter.FAVORITED));
		filterCommented.setOnAction(_ -> setFilter(Filter.COMMENTED));

		searchField.textProperty().addListener((_, _, newValue) -> {
			searchText = newValue == null ? "" : newValue.toLowerCase();
			applyFilter();
		});

		setupNotifications();
		loadFeed();
	}

	private void setFilter(Filter filter)
	{
		currentFilter = filter;
		loadFeed();
	}

	private void loadFeed()
	{
		var mono = switch (currentFilter)
		{
			case ALL -> journalClient.getFeed(0, 50).map(p -> new ArrayList<>(p.content()));
			case LIKED -> journalClient.getLikedArticles(0, 50).map(p -> new ArrayList<>(p.content()));
			case FAVORITED -> journalClient.getFavoritedArticles().map(ArrayList::new);
			case COMMENTED -> journalClient.getMyCommentedArticles().map(ArrayList::new);
		};

		mono.doOnSuccess(list -> Platform.runLater(() -> setArticles(list)))
				.doOnError(UiUtils::webAlertError)
				.subscribe();
	}

	private void setArticles(List<JournalMessage> list)
	{
		articles.setAll(list);
		applyFilter();
	}

	private void applyFilter()
	{
		filteredArticles.setPredicate(a -> StringUtils.isBlank(searchText) || (a.getTitle() != null && a.getTitle().toLowerCase().contains(searchText)));
		var empty = filteredArticles.isEmpty();
		emptyView.setVisible(empty);
		emptyView.setManaged(empty);
	}

	private void newArticle()
	{
		journalClient.getGroups()
				.filter(JournalGroup::isOwn)
				.next()
				.flatMap(group -> {
					windowManager.openJournalArticle(group.getId());
					return Mono.empty();
				})
				.switchIfEmpty(Mono.fromRunnable(() -> windowManager.openJournalCreation(0L)))
				.subscribe();
	}

	private void setupNotifications()
	{
		notificationDisposable = notificationClient.getJournalNotifications()
				.doOnError(UiUtils::webAlertError)
				.doOnNext(sse -> {
					if (sse.data() != null)
					{
						loadFeed();
					}
				})
				.subscribe();
	}
}
