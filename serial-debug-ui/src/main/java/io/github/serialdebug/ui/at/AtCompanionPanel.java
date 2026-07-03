package io.github.serialdebug.ui.at;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.function.Consumer;

/**
 * AT 指令伴侣 panel: left side = template library (ListView + search + add/remove),
 * right side = reply display (TextFlow with keyword coloring).
 */
public class AtCompanionPanel extends BorderPane {

    private static final int MAX_RESPONSE_LINES = 1000;

    private final AtCommandService service;
    private final ObservableList<AtCommand> commands = FXCollections.observableArrayList();
    private final FilteredList<AtCommand> filteredCommands;
    private final Consumer<String> onCommandSelected;

    private final ListView<AtCommand> listView = new ListView<>();
    private final TextFlow responseFlow = new TextFlow();
    private final TextField searchField = new TextField();

    public AtCompanionPanel(Consumer<String> onCommandSelected) {
        this.onCommandSelected = onCommandSelected;
        this.service = new AtCommandService();
        this.filteredCommands = new FilteredList<>(commands);

        // Load commands
        commands.setAll(service.load());

        // Left: template library
        buildLeftPane();

        // Right: response display
        buildRightPane();

        // Wire list selection
        listView.setItems(filteredCommands);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(AtCommand item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setOnMouseClicked(null);
                } else {
                    setText(item.name() + "  " + item.command());
                    setTooltip(new Tooltip(item.description()));
                    setOnMouseClicked(e -> {
                        if (onCommandSelected != null && item.command() != null) {
                            onCommandSelected.accept(item.command());
                        }
                    });
                }
            }
        });

        // Search filter
        searchField.textProperty().addListener((obs, old, val) -> {
            if (val == null || val.isEmpty()) {
                filteredCommands.setPredicate(null);
            } else {
                String lower = val.toLowerCase();
                filteredCommands.setPredicate(cmd ->
                        cmd.name().toLowerCase().contains(lower)
                                || cmd.command().toLowerCase().contains(lower));
            }
        });
    }

    private void buildLeftPane() {
        VBox leftPane = new VBox(6);
        leftPane.setPadding(new Insets(8));
        leftPane.setPrefWidth(260);

        Label title = new Label("AT 指令模板库");
        title.getStyleClass().add("section-title");

        searchField.setPromptText("搜索指令...");

        listView.setPrefHeight(400);

        Button addBtn = new Button("+ 添加");
        addBtn.setOnAction(e -> showAddDialog());
        Button delBtn = new Button("- 删除");
        delBtn.setOnAction(e -> {
            AtCommand sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                commands.remove(sel);
                service.save(commands);
            }
        });

        HBox buttons = new HBox(8, addBtn, delBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        leftPane.getChildren().addAll(title, searchField, listView, buttons);
        VBox.setVgrow(listView, Priority.ALWAYS);
        setLeft(leftPane);
    }

    private void buildRightPane() {
        VBox rightPane = new VBox(6);
        rightPane.setPadding(new Insets(8));

        Label title = new Label("模块回复（关键词着色）");
        title.getStyleClass().add("section-title");

        responseFlow.setPadding(new Insets(8));
        responseFlow.setStyle("-fx-background-color: #1e1e1e; -fx-background-radius: 4;");
        responseFlow.setLineSpacing(2);
        responseFlow.setMinHeight(400);
        responseFlow.setPrefWidth(400);

        ScrollPane scrollPane = new ScrollPane(responseFlow);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Button clearBtn = new Button("清空");
        clearBtn.setOnAction(e -> clearResponses());
        HBox btnBox = new HBox(clearBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        rightPane.getChildren().addAll(title, scrollPane, btnBox);
        setCenter(rightPane);
    }

    /**
     * Append a response line with keyword coloring. Call on FX thread.
     */
    public void appendResponse(String text) {
        if (text == null || text.isBlank()) return;

        Text node = new Text(text + "\n");
        node.setFont(javafx.scene.text.Font.font("Consolas", 13));

        // Keyword coloring
        String upper = text.toUpperCase();
        if (upper.contains("+CME ERROR") || upper.contains("+CMS ERROR") || upper.contains("ERROR")) {
            node.setFill(Color.web("#e74c3c"));
        } else if (upper.contains("OK")) {
            node.setFill(Color.web("#2ecc71"));
        } else if (text.trim().startsWith("+")) {
            node.setFill(Color.web("#3498db"));
        } else {
            node.setFill(Color.web("#e0e0e0"));
        }

        responseFlow.getChildren().add(node);

        // Prune old lines
        while (responseFlow.getChildren().size() > MAX_RESPONSE_LINES) {
            responseFlow.getChildren().remove(0);
        }
    }

    /** Clear the response display area. */
    public void clearResponses() {
        responseFlow.getChildren().clear();
    }

    private void showAddDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("添加 AT 指令");

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("名称（如：查询信号强度）");
        TextField cmdField = new TextField();
        cmdField.setPromptText("指令（如：AT+CSQ）");
        TextField descField = new TextField();
        descField.setPromptText("说明（可选）");

        VBox content = new VBox(8,
                new Label("名称:"), nameField,
                new Label("指令:"), cmdField,
                new Label("说明:"), descField);
        content.setPadding(new Insets(12));
        dialogPane.setContent(content);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK
                    && !nameField.getText().isBlank()
                    && !cmdField.getText().isBlank()) {
                commands.add(new AtCommand(
                        nameField.getText().trim(),
                        cmdField.getText().trim(),
                        descField.getText().trim()));
                service.save(commands);
            }
        });
    }
}
