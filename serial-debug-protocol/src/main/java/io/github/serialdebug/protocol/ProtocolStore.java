package io.github.serialdebug.protocol;

import java.util.List;
import java.util.Optional;

/** File-based persistence for protocol definitions. */
public interface ProtocolStore {
    Optional<Protocol> load(String name);
    void save(String name, Protocol protocol);
    List<String> listNames();
    void delete(String name);
}
