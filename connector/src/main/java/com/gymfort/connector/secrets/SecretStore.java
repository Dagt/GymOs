package com.gymfort.connector.secrets;

import java.util.Optional;

public interface SecretStore {
    Optional<char[]> read(String name);

    void write(String name, char[] value);
}
