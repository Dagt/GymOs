package com.gymfort.connector.secrets;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SecretStores {
    private SecretStores() {
    }

    public static SecretStore systemDefault(Path stateDirectory) {
        List<SecretStore> readers = new ArrayList<>();
        readers.add(new EnvironmentSecretStore());
        SecretStore writer = null;
        if (System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            writer = new WindowsDpapiSecretStore(stateDirectory);
            readers.add(writer);
        }
        return new CompositeSecretStore(readers, writer);
    }
}
