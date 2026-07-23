package com.gymos.memberships.application;

import java.time.LocalDate;

record MembershipActivationWindow(LocalDate startsOn, LocalDate endsOn, LocalDate graceEndsOn) {
}
