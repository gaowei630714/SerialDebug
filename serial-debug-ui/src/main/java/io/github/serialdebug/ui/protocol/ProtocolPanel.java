package io.github.serialdebug.ui.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.serialdebug.protocol.*;
import io.github.serialdebug.protocol.ProtocolValidator.ValidationResult;
import io.github.serialdebug.ui.controller.UiHelper;
import io.github.serialdebug.ui.i18n.Messages;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;
import java.util.function.Consumer;

/**
 * Protocol editor panel: load/save/delete protocols, configure framing and
 * fields via a TableView, live JSON preview.
 */
public class ProtocolPanel extends VBox {

    private static final String[] TYPES = {
            "uint8", "uint16_le", "uint16_be", "uint32_le", "uint32_be",
            "int8", "int16_le", "int16_be", "int32_le", "int32_be",
            "float32_le", "float32_be", "float64_le", "float64_be"
    };

    private final ProtocolStore store;
    private final Consumer<Protocol> onLoad;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final ComboBox<String> nameCombo = new ComboBox<>();
    private final TextField headerField = new TextField();
    private final TextField frameLengthField = new TextField();
    private final ToggleGroup framingGroup = new ToggleGroup();
    private final RadioButton headerRadio = new RadioButton();
    private final RadioButton fixedRadio = new RadioButton();
    private final ObservableList<ProtocolFieldRow> fieldRows = FXCollections.observableArrayList();
    private final TableView<ProtocolFieldRow> fieldTable = new TableView<>();
    private final TextArea jsonPreview = new TextArea();

    public ProtocolPanel(ProtocolStore store, Consumer<Protocol> onLoad) {
        this.store = store;
        this.onLoad = onLoad;
        build();
        reloadList();
    }

    private void build() {
        setPadding(new Insets(8));
        setSpacing(6);
        getStyleClass().add("protocol-panel");

        addSelectionRow();
        addFramingRow();
        addFieldsTable();
        addJsonPreview();
    }

    // ---- selection row ----

    private void addSelectionRow() {
        HBox.setHgrow(nameCombo, Priority.ALWAYS);
        Button loadBtn = new Button(Messages.get("protocol.load"), new FontIcon("mdi2f-file-download"));
        Button newBtn = new Button(Messages.get("protocol.new"), new FontIcon("mdi2f-file-plus"));
        Button saveBtn = new Button(Messages.get("protocol.save"), new FontIcon("mdi2c-content-save"));
        Button delBtn = new Button(Messages.get("protocol.delete"), new FontIcon("mdi2d-trash-can"));

        loadBtn.setOnAction(e -> loadSelected());
        newBtn.setOnAction(e -> newProtocol());
        saveBtn.setOnAction(e -> save());
        delBtn.setOnAction(e -> deleteSelected());

        HBox selectRow = new HBox(6,
                new Label(Messages.get("protocol.title") + ":"), nameCombo,
                loadBtn, newBtn, saveBtn, delBtn);
        selectRow.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(selectRow);
    }

    // ---- framing ----

    private void addFramingRow() {
        headerRadio.setToggleGroup(framingGroup);
        headerRadio.setText(Messages.get("protocol.framing.header"));
        headerRadio.setSelected(true);
        fixedRadio.setToggleGroup(framingGroup);
        fixedRadio.setText(Messages.get("protocol.framing.fixed"));

        headerField.setPrefWidth(110);
        headerField.setText("AA55");
        frameLengthField.setPrefWidth(70);
        frameLengthField.setText("10");

        // Header hex field visibility bound to header radio
        HBox headerRow = new HBox(6, new Label(Messages.get("protocol.header") + ":"), headerField);
        headerRow.visibleProperty().bind(headerRadio.selectedProperty());
        headerRow.managedProperty().bind(headerRadio.selectedProperty());

        HBox mainFraming = new HBox(6,
                new Label(Messages.get("protocol.framing") + ":"),
                headerRadio, fixedRadio,
                new Label(Messages.get("protocol.frameLength") + ":"), frameLengthField);
        mainFraming.setAlignment(Pos.CENTER_LEFT);
        mainFraming.setPadding(new Insets(4, 0, 0, 0));

        VBox framingSection = new VBox(4, mainFraming, headerRow);
        framingSection.setPadding(new Insets(4, 0, 0, 0));
        getChildren().add(new Separator());
        getChildren().add(framingSection);

        headerField.textProperty().addListener((obs, old, val) -> updateJsonPreview());
        frameLengthField.textProperty().addListener((obs, old, val) -> updateJsonPreview());
    }

    // ---- fields table ----

    private void addFieldsTable() {
        ObservableList<ProtocolFieldRow> items = fieldRows;
        fieldTable.setItems(items);
        fieldTable.setPrefHeight(180);

        TableColumn<ProtocolFieldRow, Boolean> enabledCol = new TableColumn<>(Messages.get("protocol.field.enabled"));
        enabledCol.setPrefWidth(60);
        enabledCol.setCellValueFactory(cell -> cell.getValue().enabledProperty());
        enabledCol.setCellFactory(CheckBoxTableCell.forTableColumn());

        TableColumn<ProtocolFieldRow, String> nameCol = new TableColumn<>("Name");
        nameCol.setPrefWidth(100);
        nameCol.setCellValueFactory(cell -> cell.getValue().nameProperty());
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());

        TableColumn<ProtocolFieldRow, String> labelCol = new TableColumn<>("Label");
        labelCol.setPrefWidth(100);
        labelCol.setCellValueFactory(cell -> cell.getValue().labelProperty());
        labelCol.setCellFactory(TextFieldTableCell.forTableColumn());

        TableColumn<ProtocolFieldRow, Number> offsetCol = new TableColumn<>("Offset");
        offsetCol.setPrefWidth(70);
        offsetCol.setCellValueFactory(cell -> cell.getValue().offsetProperty());
        offsetCol.setCellFactory(TextFieldTableCell.forTableColumn());

        TableColumn<ProtocolFieldRow, Number> sizeCol = new TableColumn<>("Size");
        sizeCol.setPrefWidth(60);
        sizeCol.setCellValueFactory(cell -> cell.getValue().sizeProperty());
        sizeCol.setCellFactory(TextFieldTableCell.forTableColumn());

        TableColumn<ProtocolFieldRow, String> typeCol = new TableColumn<>("Type");
        typeCol.setPrefWidth(110);
        typeCol.setCellValueFactory(cell -> cell.getValue().typeDisplayProperty());
        typeCol.setCellFactory(tc -> new TableCell<>() {
            private final ComboBox<String> comboBox = new ComboBox<>(FXCollections.observableArrayList(TYPES));
            { comboBox.setStyle("-fx-background-color: transparent;"); }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); setText(null); }
                else {
                    comboBox.setValue(item);
                    setGraphic(comboBox);
                    comboBox.setOnAction(e -> {
                        ProtocolFieldRow row = getTableView().getItems().get(getIndex());
                        row.setType(comboBox.getValue());
                        getTableView().edit(getIndex(), typeCol);
                    });
                }
            }
        });

        TableColumn<ProtocolFieldRow, String> scaleCol = new TableColumn<>("Scale");
        scaleCol.setPrefWidth(70);
        scaleCol.setCellValueFactory(cell -> cell.getValue().scaleProperty());
        scaleCol.setCellFactory(TextFieldTableCell.forTableColumn());

        TableColumn<ProtocolFieldRow, String> biasCol = new TableColumn<>("Bias");
        biasCol.setPrefWidth(70);
        biasCol.setCellValueFactory(cell -> cell.getValue().biasProperty());
        biasCol.setCellFactory(TextFieldTableCell.forTableColumn());

        TableColumn<ProtocolFieldRow, String> bitCol = new TableColumn<>("Bits");
        bitCol.setPrefWidth(100);
        bitCol.setCellValueFactory(cell -> cell.getValue().bitsProperty());
        bitCol.setCellFactory(TextFieldTableCell.forTableColumn());

        TableColumn<ProtocolFieldRow, Void> actionCol = new TableColumn<>("");
        actionCol.setPrefWidth(50);
        actionCol.setCellFactory(tc -> new TableCell<>() {
            private final Button btn = new Button(null, new FontIcon("mdi2d-trash-can"));
            {
                btn.setOnAction(e -> {
                    ObservableList<ProtocolFieldRow> data = getTableView().getItems();
                    data.remove(getIndex());
                    updateJsonPreview();
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        fieldTable.getColumns().addAll(enabledCol, nameCol, labelCol, offsetCol, sizeCol, typeCol, scaleCol, biasCol, bitCol, actionCol);

        Button addFieldBtn = new Button(Messages.get("protocol.add.field"), new FontIcon("mdi2p-plus"));
        addFieldBtn.setOnAction(e -> {
            ProtocolFieldRow row = new ProtocolFieldRow();
            row.setName("field" + (fieldRows.size() + 1));
            row.setOffset(0);
            row.setSize(1);
            row.setType("uint8");
            row.setScale("1.0");
            row.setBias("0.0");
            row.setEnabled(true);
            fieldRows.add(row);
            updateJsonPreview();
        });

        VBox fieldsBox = new VBox(4,
                new Label(Messages.get("protocol.fields") + ":"),
                fieldTable,
                addFieldBtn);
        VBox.setVgrow(fieldTable, Priority.ALWAYS);
        getChildren().add(new Separator());
        getChildren().add(fieldsBox);

        items.addListener((java.util.collections.ObservableListChangeListener.Change<? extends ProtocolFieldRow> c) -> {
            updateJsonPreview();
        });
    }

    // ---- json preview ----

    private void addJsonPreview() {
        jsonPreview.setEditable(false);
        jsonPreview.getStyleClass().add("mono-text-area");
        jsonPreview.setPrefHeight(80);

        HBox.setHgrow(jsonPreview, Priority.ALWAYS);
        getChildren().add(new Separator());
        getChildren().add(new Label(Messages.get("protocol.json.preview") + ":"));
        getChildren().add(jsonPreview);
    }

    // ---- actions ----

    private void loadSelected() {
        String name = nameCombo.getSelectionModel().getSelectedItem();
        if (name == null || name.isBlank()) return;
        Optional<Protocol> opt = store.load(name);
        opt.ifPresent(this::populate);
    }

    private void populate(Protocol p) {
        nameCombo.getSelectionModel().select(p.name());
        ProtocolFraming f = p.framing();
        if ("header".equals(f.mode())) {
            headerRadio.setSelected(true);
            headerField.setText(f.header());
        } else {
            fixedRadio.setSelected(true);
        }
        frameLengthField.setText(String.valueOf(f.frameLength()));
        fieldRows.clear();
        for (ProtocolField field : p.fields()) {
            fieldRows.add(new ProtocolFieldRow(field));
        }
        updateJsonPreview();
        if (onLoad != null) Platform.runLater(() -> onLoad.accept(p));
    }

    private void newProtocol() {
        nameCombo.getSelectionModel().clearSelection();
        headerRadio.setSelected(true);
        headerField.setText("AA55");
        frameLengthField.setText("10");
        fieldRows.clear();
        fieldRows.add(new ProtocolFieldRow());
        fieldRows.get(0).setName("field1");
        fieldRows.get(0).setOffset(0);
        fieldRows.get(0).setSize(1);
        fieldRows.get(0).setType("uint8");
        fieldRows.get(0).setScale("1.0");
        fieldRows.get(0).setBias("0.0");
        fieldRows.get(0).setEnabled(true);
        updateJsonPreview();
    }

    public void save() {
        Optional<Protocol> opt = buildProtocol();
        if (opt.isEmpty()) return;
        Protocol p = opt.get();
        String name = p.name();
        ValidationResult vr = ProtocolValidator.validate(p);
        if (!vr.valid()) {
            UiHelper.showWarning(vr.errorMessage());
            return;
        }
        store.save(name, p);
        reloadList();
        UiHelper.showInfo(Messages.get("protocol.title") + " " + name + " saved.");
        if (onLoad != null) Platform.runLater(() -> onLoad.accept(p));
    }

    private void deleteSelected() {
        String name = nameCombo.getSelectionModel().getSelectedItem();
        if (name == null || name.isBlank()) return;
        store.delete(name);
        reloadList();
        newProtocol();
    }

    // ---- helpers ----

    public void reloadList() {
        nameCombo.getItems().setAll(store.listNames());
    }

    private Optional<Protocol> buildProtocol() {
        String name = nameCombo.getSelectionModel().getSelectedItem();
        if (name == null || name.isBlank()) {
            UiHelper.showWarning(Messages.get("protocol.error.select.name"));
            return Optional.empty();
        }
        int frameLength;
        try { frameLength = Integer.parseInt(frameLengthField.getText()); }
        catch (NumberFormatException e) {
            UiHelper.showWarning(Messages.get("protocol.error.invalid.frameLength"));
            return Optional.empty();
        }
        String mode = headerRadio.isSelected() ? "header" : "fixed";
        String header = headerRadio.isSelected() ? headerField.getText() : "";
        return buildProtocolWithValidation(name, frameLength, mode, header);
    }

    /** Build from controls and validate (used by save()). */
    private Optional<Protocol> buildProtocolWithValidation(String name, int frameLength,
                                                           String mode, String header) {
        List<ProtocolField> fields = fieldRows.stream()
                .map(ProtocolFieldRow::toProtocolField)
                .filter(f -> !(f.name() == null || f.name().isBlank()))
                .toList();
        ProtocolBuilder builder = new ProtocolBuilder()
                .name(name)
                .version("1.0")
                .framing(mode, header, frameLength);
        fields.forEach(f -> builder.addField(
                f.name(), f.label(), f.offset(), f.size(),
                f.type(), f.scale(), f.bias(), f.bits(), f.enabled()));
        return builder.build();
    }

    /**
     * Build a Protocol for display/preview without validation or i18n warnings.
     */
    private Optional<Protocol> buildProtocolSilent() {
        String name = nameCombo.getSelectionModel().getSelectedItem();
        if (name == null || name.isBlank()) return Optional.empty();
        int frameLength;
        try { frameLength = Integer.parseInt(frameLengthField.getText()); }
        catch (NumberFormatException e) { return Optional.empty(); }
        String mode = headerRadio.isSelected() ? "header" : "fixed";
        String header = headerRadio.isSelected() ? headerField.getText() : "";
        List<ProtocolField> fields = fieldRows.stream()
                .map(ProtocolFieldRow::toProtocolField)
                .filter(f -> !(f.name() == null || f.name().isBlank()))
                .toList();
        return Optional.of(new Protocol(name, "1.0",
                new ProtocolFraming(mode, header, frameLength), fields));
    }

    /**
     * Internal package helper for testing the ProtocolBuilder construction
     * that ProtocolPanel.save() relies on.
     */
    static ProtocolBuilder makeBuilder(String name, String mode, String header,
                                       int frameLength) {
        return new ProtocolBuilder()
                .name(name)
                .version("1.0")
                .framing(mode, header, frameLength);
    }
}
