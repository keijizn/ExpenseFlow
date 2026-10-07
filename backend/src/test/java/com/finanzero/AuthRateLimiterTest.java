package com.finanzero;

import com.finanzero.service.AuthRateLimiter;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class AuthRateLimiterTest {
    @Test void limitsAccountEvenWhenOtherKeysAreUsed() {
        var limiter = new AuthRateLimiter();
        for (int i = 0; i < 5; i++) limiter.check("verify:account", 5, Duration.ofMinutes(15));
        limiter.check("verify:other", 5, Duration.ofMinutes(15));
        assertThatThrownBy(() -> limiter.check("verify:account", 5, Duration.ofMinutes(15)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("429");
    }
}
