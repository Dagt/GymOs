package com.gymfort.connector.provider;

public record ProviderPerson(
        String externalPersonRef,
        String displayName,
        String email,
        String phone,
        String employeeNumber
) {
}
