package com.gymos.selfservice.application.command;

import java.time.LocalDate;

public record VerifySessionCommand(String identifier, LocalDate birthDate) {
}
