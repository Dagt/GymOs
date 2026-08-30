package com.gymfort.connector.provider;

import java.util.List;

public record ProviderPage<T>(List<T> items, String nextCursor, boolean hasMore) {
    public ProviderPage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static <T> ProviderPage<T> empty(String cursor) {
        return new ProviderPage<>(List.of(), cursor, false);
    }
}
