package io.github.serialdebug.ui.controller;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles file-based sending: FileChooser, text/binary modes, progress, cancel.
 * Dependencies on SendController for the actual send pipeline.
 */
public class FileSendController {

    private final Button fileSendButton;
    private final Label fileSendProgress;
    private final Button cancelFileSendButton;
    private final Stage stage;
    private final SendController sendController;
    private final Runnable onSendComplete;

    private volatile boolean fileSendCancelled;
    private final ExecutorService fileSendExecutor;

    public FileSendController(
            Button fileSendButton,
            Label fileSendProgress,
            Button cancelFileSendButton,
            Stage stage,
            SendController sendController,
            Runnable onSendComplete) {
        this.fileSendButton = fileSendButton;
        this.fileSendProgress = fileSendProgress;
        this.cancelFileSendButton = cancelFileSendButton;
        this.stage = stage;
        this.sendController = sendController;
        this.onSendComplete = onSendComplete;
        this.fileSendExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "file-send");
            t.setDaemon(true);
            return t;
        });
    }

    public void setPortOpen(boolean open) {
        fileSendButton.setDisable(!open);
    }

    public void shutdown() {
        fileSendExecutor.shutdownNow();
    }

    public void onFileSend() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select file to send");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("Binary", "*.bin"),
                new FileChooser.ExtensionFilter("HEX", "*.hex"),
                new FileChooser.ExtensionFilter("Text", "*.txt")
        );
        File file = fileChooser.showOpenDialog(stage);
        if (file == null) return;

        boolean binaryMode = !file.getName().toLowerCase().endsWith(".txt");

        fileSendCancelled = false;
        fileSendButton.setDisable(true);
        cancelFileSendButton.setDisable(false);
        cancelFileSendButton.setVisible(true);
        fileSendProgress.setText("Starting...");

        fileSendExecutor.submit(() -> {
            try {
                if (binaryMode) {
                    sendBinaryFile(file);
                } else {
                    sendTextFile(file);
                }
            } catch (IOException e) {
                Platform.runLater(() -> UiHelper.showError("File send failed", e));
            }
            Platform.runLater(() -> {
                fileSendButton.setDisable(false);
                cancelFileSendButton.setDisable(true);
                cancelFileSendButton.setVisible(false);
                if (!fileSendCancelled) {
                    fileSendProgress.setText("Complete");
                } else {
                    fileSendProgress.setText("Cancelled");
                }
                if (onSendComplete != null) {
                    onSendComplete.run();
                }
            });
        });
    }

    public void onCancelFileSend() {
        fileSendCancelled = true;
    }

    private void sendTextFile(File file) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        int total = lines.size();
        for (int i = 0; i < total; i++) {
            if (fileSendCancelled) return;
            String line = lines.get(i).strip();
            if (line.isEmpty()) continue;
            // Snapshot FX properties before background thread (thread-safe)
            final boolean hexMode = false; // text mode — send as ASCII
            final String lineEnding = null; // line endings already in file
            byte[] data = sendController.prepareSendData(line, hexMode, lineEnding);
            if (data != null) {
                sendController.doSend(data);
            }
            final int current = i + 1;
            Platform.runLater(() -> fileSendProgress.setText(current + "/" + total));
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void sendBinaryFile(File file) throws IOException {
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        int total = fileBytes.length;
        int chunkSize = 1024;
        int chunks = (total + chunkSize - 1) / chunkSize;

        for (int i = 0; i < chunks; i++) {
            if (fileSendCancelled) return;
            int offset = i * chunkSize;
            int length = Math.min(chunkSize, total - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(fileBytes, offset, chunk, 0, length);
            sendController.doSend(chunk);
            final int pct = (int) ((i + 1) * 100L / chunks);
            final int pkts = i + 1;
            Platform.runLater(() -> fileSendProgress.setText(pct + "% (" + pkts + "/" + chunks + " pkts)"));
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
