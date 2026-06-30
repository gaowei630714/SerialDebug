package io.github.serialdebug.core.log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

public class FileLogService implements LogService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final long DEFAULT_SPLIT_THRESHOLD = 100L * 1024 * 1024; // 100 MB

    private final long splitThreshold;
    private final ReentrantLock lock = new ReentrantLock();

    private BufferedWriter writer;
    private LogFormat format;
    private Path currentFile;
    private Path baseFile;
    private int splitIndex;
    private long bytesLogged;

    public FileLogService() {
        this(DEFAULT_SPLIT_THRESHOLD);
    }

    public FileLogService(long splitThreshold) {
        this.splitThreshold = splitThreshold;
    }

    @Override
    public void start(Path file, LogFormat format) throws IOException {
        lock.lock();
        try {
            this.baseFile = file;
            this.splitIndex = 0;
            this.format = format;
            this.bytesLogged = 0;
            this.currentFile = file;
            this.writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stop() {
        lock.lock();
        try {
            if (writer != null) {
                try {
                    writer.flush();
                    writer.close();
                } catch (IOException ignored) {
                    // best effort on close
                }
                writer = null;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isLogging() {
        lock.lock();
        try {
            return writer != null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void log(byte[] data, int offset, int length, Direction direction) {
        if (data == null || length <= 0) {
            return;
        }
        lock.lock();
        try {
            if (writer == null) {
                return;
            }
            String timestamp = LocalTime.now().format(TIME_FORMATTER);
            String payload = switch (format) {
                case HEX -> encodeHex(data, offset, length);
                case ASCII -> encodeAscii(data, offset, length);
            };
            String line = "[" + timestamp + " " + direction + "] " + payload;
            writer.write(line);
            writer.newLine();
            writer.flush();
            bytesLogged += line.length() + System.lineSeparator().length();
            checkAndSplit();
        } catch (IOException e) {
            // On write failure, stop logging to prevent further errors
            try {
                writer.close();
            } catch (IOException ignored) {
                // best effort
            }
            writer = null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long getBytesLogged() {
        lock.lock();
        try {
            return bytesLogged;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Path getCurrentFile() {
        lock.lock();
        try {
            return currentFile;
        } finally {
            lock.unlock();
        }
    }

    private void checkAndSplit() {
        try {
            if (currentFile != null && Files.exists(currentFile)
                    && Files.size(currentFile) >= splitThreshold) {
                writer.flush();
                writer.close();
                splitIndex++;
                currentFile = generateSplitPath(baseFile, splitIndex);
                writer = Files.newBufferedWriter(currentFile, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // If split fails, continue writing to current file
        }
    }

    private Path generateSplitPath(Path base, int index) {
        String fileName = base.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName;
        String extension;
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        } else {
            baseName = fileName;
            extension = "";
        }
        return base.resolveSibling(baseName + "_" + index + extension);
    }

    private String encodeHex(byte[] data, int offset, int length) {
        StringBuilder sb = new StringBuilder(length * 3);
        for (int i = offset; i < offset + length; i++) {
            if (i > offset) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", data[i]));
        }
        return sb.toString();
    }

    private String encodeAscii(byte[] data, int offset, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = offset; i < offset + length; i++) {
            int ub = data[i] & 0xFF;
            if (ub >= 0x20 && ub <= 0x7E) {
                sb.append((char) ub);
            } else {
                sb.append(String.format("\\x%02X", ub));
            }
        }
        return sb.toString();
    }
}
