package io.github.serialdebug.ui.preset;

import java.util.List;

/**
 * Persistence contract for command presets.
 */
public interface PresetService {

    /**
     * Load all presets from storage. Returns an empty list if no presets exist
     * or the file is corrupt.
     */
    List<Preset> load();

    /**
     * Save all presets to storage, replacing any existing content.
     */
    void save(List<Preset> presets);
}
