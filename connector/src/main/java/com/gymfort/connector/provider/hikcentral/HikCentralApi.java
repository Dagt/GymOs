package com.gymfort.connector.provider.hikcentral;

import java.util.Set;

interface HikCentralApi extends AutoCloseable {
    String version();

    Set<String> accessLevelIds();

    boolean accessLevelContains(String accessLevelRef, String personRef);

    void addPerson(String accessLevelRef, String personRef);

    void removePerson(String accessLevelRef, String personRef);

    @Override
    default void close() {
    }
}
