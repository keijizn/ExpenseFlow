package com.finanzero.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Limits this server instance; use a shared gateway/Redis limiter before scaling horizontally. */
@Component
public class AuthRateLimiter {
    private record Window(long expires, int count) {}
    private final Map<String, Window> windows = new HashMap<>();

    public synchronized void check(String key, int limit, Duration duration) {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> entry.getValue().expires() <= now);
        Window previous = windows.get(key);
        if ((previous != null && previous.count() >= limit) || (previous == null && windows.size() >= 10000)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Aguarde antes de tentar novamente.");
        }
        windows.put(key, new Window(previous == null ? now + duration.toMillis() : previous.expires(), previous == null ? 1 : previous.count() + 1));
    }
}
