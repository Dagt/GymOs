package com.gymos.common.security;

import static org.assertj.core.api.Assertions.*;

import com.gymos.common.api.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrongPasswordPolicyTests {
    private final StrongPasswordPolicy policy = new StrongPasswordPolicy();

    @Test void acceptsStrongPasswordsAtTheActualBcryptByteLimit() {
        assertThatCode(() -> policy.validate("Aa1!" + "a".repeat(68))).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("Aa1!" + "ó".repeat(34))).doesNotThrowAnyException();
    }

    @Test void rejectsTooManyBytesWithoutEchoingThePassword() {
        for (String password : List.of("Aa1!" + "a".repeat(69), "Aa1!" + "ó".repeat(35), "Aa1!" + "🔒".repeat(18))) {
            assertThatThrownBy(() -> policy.validate(password)).isInstanceOfSatisfying(BusinessException.class, error -> {
                assertThat(error.code()).isEqualTo("PASSWORD_POLICY_NOT_MET");
                assertThat(error.getMessage()).doesNotContain(password);
            });
        }
    }

    @Test void keepsTheExistingComplexityAndWhitespaceRules() {
        for (String password : List.of("Short1!", "ONLYUPPERCASE123!", "onlylowercase123!", "NoDigitsHere!", "NoSymbolsHere123", "Contains space123!")) {
            assertThatThrownBy(() -> policy.validate(password)).isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> policy.validate(null)).isInstanceOf(BusinessException.class);
    }
}
