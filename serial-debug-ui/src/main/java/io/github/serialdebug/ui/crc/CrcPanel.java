package io.github.serialdebug.ui.crc;

import io.github.serialdebug.core.crc.CrcEngine;
import io.github.serialdebug.ui.i18n.Messages;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

/**
 * CRC calculator panel. HEX input → real-time results for 5 algorithms.
 * Supports appending CRC bytes (as space-separated HEX) to send area
 * and copying formatted result to clipboard.
 */
public class CrcPanel extends VBox {

    private static record AlgRow(String name, Label resultLabel,
                                  ToLongFunction<byte[]> crcFn, int byteCount) {
    }

    private final TextField hexField;
    private final Consumer<String> onAppend;
    private final List<AlgRow> rows = new ArrayList<>();
    private byte[] lastParsed = new byte[0];

    public CrcPanel(Consumer<String> onAppend) {
        this.onAppend = onAppend;
        setSpacing(8);
        setPadding(new Insets(12));
        getStyleClass().add("crc-panel");

        Label hexLabel = new Label();
        hexLabel.textProperty().bind(Messages.createStringBinding("crc.input"));
        hexField = new TextField();
        hexField.promptTextProperty().bind(Messages.createStringBinding("crc.example"));
        HBox.setHgrow(hexField, Priority.ALWAYS);
        HBox inputRow = new HBox(8, hexLabel, hexField);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        hexField.textProperty().addListener((obs, old, val) -> {
            lastParsed = parseHex(val);
            updateAll(lastParsed);
        });

        // Algorithm rows: display format is "0xVV" but append sends raw bytes
        rows.add(new AlgRow("CRC-8/Dallas", new Label("—"), CrcEngine::crc8Dallas, 1));
        rows.add(new AlgRow("CRC-16/Modbus", new Label("—"), CrcEngine::crc16Modbus, 2));
        rows.add(new AlgRow("CRC-32", new Label("—"), d -> (int)(CrcEngine.crc32(d) & 0xFFFFFFFFL), 4));
        rows.add(new AlgRow("SUM-8", new Label("—"), CrcEngine::sum8, 1));
        rows.add(new AlgRow("SUM-16", new Label("—"), CrcEngine::sum16, 2));

        getChildren().add(inputRow);
        for (AlgRow row : rows) {
            HBox r = new HBox(8);
            r.setAlignment(Pos.CENTER_LEFT);
            Label nameLabel = new Label(row.name() + ":");
            nameLabel.setPrefWidth(120);
            row.resultLabel().setStyle("-fx-font-family: Consolas, monospace; -fx-font-weight: bold;");
            updateLabel(row, new byte[0]);

            Button appendBtn = new Button();
            appendBtn.textProperty().bind(Messages.createStringBinding("crc.append"));
            appendBtn.setGraphic(new FontIcon("mdi2p-plus"));
            appendBtn.setOnAction(e -> {
                if (onAppend != null && lastParsed.length > 0) {
                    onAppend.accept(crcToHex(row, lastParsed));
                }
            });
            Button copyBtn = new Button(null, new FontIcon("mdi2c-content-copy"));
            copyBtn.setOnAction(e -> {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(row.resultLabel().getText());
                Clipboard.getSystemClipboard().setContent(cc);
            });
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            r.getChildren().addAll(nameLabel, row.resultLabel(), spacer, appendBtn, copyBtn);
            getChildren().add(r);
        }
    }

    private void updateAll(byte[] data) {
        for (AlgRow r : rows) updateLabel(r, data);
    }

    private void updateLabel(AlgRow r, byte[] data) {
        if (data.length == 0) {
            r.resultLabel().setText("—");
            return;
        }
        long val = r.crcFn().applyAsLong(data);
        if (r.byteCount() <= 2) {
            r.resultLabel().setText(String.format("0x%0" + (r.byteCount() * 2) + "X", val));
        } else {
            r.resultLabel().setText(String.format("0x%08X", val));
        }
    }

    /** Convert CRC value to space-separated HEX string for appending */
    private String crcToHex(AlgRow r, byte[] data) {
        long val = r.crcFn().applyAsLong(data);
        StringBuilder sb = new StringBuilder();
        for (int i = r.byteCount() - 1; i >= 0; i--) {
            sb.append(String.format("%02X ", (val >> (i * 8)) & 0xFF));
        }
        return sb.toString().trim();
    }

    private byte[] parseHex(String text) {
        if (text == null || text.isBlank()) return new byte[0];
        String cleaned = text.replaceAll("[^0-9a-fA-F]", "");
        int len = cleaned.length();
        if (len % 2 != 0) len--;
        byte[] data = new byte[len / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) Integer.parseInt(cleaned.substring(i * 2, i * 2 + 2), 16);
        }
        return data;
    }
}