package io.github.serialdebug.core.log;

import java.io.IOException;
import java.nio.file.Path;

public interface LogService {

    /**
     * Start logging to the specified file.
     *
     * @param file   target file path
     * @param format log format (HEX or ASCII)
     * @throws IOException if the file cannot be created or opened
     */
    void start(Path file, LogFormat format) throws IOException;

    /** Stop logging and close the file stream. */
    void stop();

    /** Whether logging is currently active. */
    boolean isLogging();

    /**
     * Write one log entry.
     *
     * @param data      raw byte data
     * @param offset    start offset in data
     * @param length    number of valid bytes
     * @param direction RX or TX
     */
    void log(byte[] data, int offset, int length, Direction direction);

    /** Total bytes written to file (flushed). */
    long getBytesLogged();

    /** Path of the currently active log file. */
    Path getCurrentFile();
}
