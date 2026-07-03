package io.github.serialdebug.ui.at;

import java.util.List;

/**
 * Persistence contract for AT command templates.
 */
public interface AtCommandService {
    List<AtCommand> load();
    void save(List<AtCommand> commands);
}
