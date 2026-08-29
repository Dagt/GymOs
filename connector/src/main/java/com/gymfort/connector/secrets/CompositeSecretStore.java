package com.gymfort.connector.secrets;

import java.util.List;
import java.util.Optional;

public final class CompositeSecretStore implements SecretStore {
    private final List<SecretStore> readers;
    private final SecretStore writer;

    public CompositeSecretStore(List<SecretStore> readers, SecretStore writer) {
        this.readers = List.copyOf(readers);
        this.writer = writer;
    }

    @Override
    public Optional<char[]> read(String name) {
        for (SecretStore reader : readers) {
            Optional<char[]> value = reader.read(name);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    @Override
    public void write(String name, char[] value) {
        if (writer == null) {
            throw new UnsupportedOperationException(
                    "Este sistema no ofrece un almacén persistente seguro. Utiliza variables de entorno.");
        }
        writer.write(name, value);
    }
}
