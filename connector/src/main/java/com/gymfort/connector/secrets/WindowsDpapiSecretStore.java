package com.gymfort.connector.secrets;

import com.sun.jna.platform.win32.Crypt32Util;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Optional;

public final class WindowsDpapiSecretStore implements SecretStore {
    private final Path directory;

    public WindowsDpapiSecretStore(Path stateDirectory) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            throw new IllegalStateException("DPAPI sólo está disponible en Windows.");
        }
        this.directory = stateDirectory.resolve("secrets");
    }

    @Override
    public Optional<char[]> read(String name) {
        Path path = path(name);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            byte[] encrypted = Files.readAllBytes(path);
            byte[] plain = Crypt32Util.cryptUnprotectData(encrypted);
            try {
                return Optional.of(new String(plain, StandardCharsets.UTF_8).toCharArray());
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        } catch (RuntimeException | IOException exception) {
            throw new IllegalStateException("No fue posible leer el secreto protegido " + name + ".", exception);
        }
    }

    @Override
    public void write(String name, char[] value) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException("El secreto no puede estar vacío.");
        }
        byte[] plain = new String(value).getBytes(StandardCharsets.UTF_8);
        try {
            Files.createDirectories(directory);
            byte[] encrypted = Crypt32Util.cryptProtectData(plain);
            Path target = path(name);
            Path temporary = Files.createTempFile(directory, ".secret-", ".tmp");
            try {
                Files.write(temporary, encrypted);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
                Arrays.fill(encrypted, (byte) 0);
            }
        } catch (RuntimeException | IOException exception) {
            throw new IllegalStateException("No fue posible guardar el secreto protegido " + name + ".", exception);
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    private Path path(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9.-]{1,80}")) {
            throw new IllegalArgumentException("Nombre de secreto inválido.");
        }
        return directory.resolve(name + ".dpapi");
    }
}
