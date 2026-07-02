package io.github.serialdebug.ui.crc;

import io.github.serialdebug.core.crc.CrcEngine;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;

/**
 * CRC calculator panel. HEX input → real-time results for 5 algorithms.
 * Supports appending result to send area and copying to clipboard.
 */
public class CrcPanel extends VBox {

    private static record AlgRow(String name, Label resultLabel,
                                  java.util.function.Function<byte[], String> formatter) {
    }

    private final TextField hexField;
    private final Consumer<String> onAppend;
    private final java.util.List<AlgRow> rows = new java.util.ArrayList<>();

    public CrcPanel(Consumer<String> onAppend) {
        this.onAppend = onAppend;
        setSpacing(8);
        setPadding(new Insets(12));
        getStyleClass().add("crc-panel");

        // HEX input row
        Label hexLabel = new Label("HEX 输入:");
        hexField = new TextField();
        hexField.setPromptText("例: 01 03 00 00 00 01");
        HBox.setHgrow(hexField, Priority.ALWAYS);
        HBox inputRow = new HBox(8, hexLabel, hexField);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        // Real-time listener
        hexField.textProperty().addListener((obs, old, val) -> updateAll(val));

        // Algorithm rows
        rows.add(new AlgRow("CRC-8/Dallas", new Label("—"),
                d -> String.format("0x%02X", CrcEngine.crc8Dallas(d))));
        rows.add(new AlgRow("CRC-16/Modbus", new Label("—"),
                d -> String.format("0x%04X", CrcEngine.crc16Modbus(d))));
        rows.add(new AlgRow("CRC-32", new Label("—"),
                d -> String.format("0x%08X", CrcEngine.crc32(d))));
        rows.add(new AlgRow("SUM-8", new Label("—"),
                d -> String.format("0x%02X", CrcEngine.sum8(d))));
        rows.add(new AlgRow("SUM-16", new Label("—"),
                d -> String.format("0x%04X", CrcEngine.sum16(d))));

        getChildren().add(inputRow);
        for (AlgRow row : rows) {
            HBox r = new HBox(8);
            r.setAlignment(Pos.CENTER_LEFT);
            Label nameLabel = new Label(row.name() + ":");
            nameLabel.setPrefWidth(120);
            row.resultLabel().setStyle("-fx-font-family: Consolas, monospace; -fx-font-weight: bold;");
            Button appendBtn = new Button("追加", new FontIcon("mdi2p-plus"));
            appendBtn.setOnAction(e -> {
                String hex = hexField.getText();
                if (hex != null && !hex.isBlank() && onAppend != null) {
                    onAppend.accept(row.formatter().apply(parseHex(hex)));
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

    private void updateAll(String hexText) {
        if (hexText == null || hexText.isBlank()) {
            for (AlgRow r : rows) r.resultLabel().setText("—");
            return;
        }
        byte[] data = parseHex(hexText);
        for (AlgRow r : rows) {
            try {
                r.resultLabel().setText(r.formatter().apply(data));
            } catch (Exception e) {
                r.resultLabel().setText("ERR");
            }
        }
    }

    private byte[] parseHex(String text) {
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
