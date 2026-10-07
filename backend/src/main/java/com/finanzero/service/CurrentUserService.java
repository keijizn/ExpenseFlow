package com.finanzero.service;

import com.finanzero.model.AppUser;
import com.finanzero.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final HttpServletRequest request;
    private final AppUserRepository users;

    public AppUser requiredUser() {
        String token = tokenFromRequest();
        if (token == null || token.isBlank()) {
            throw unauthorized();
        }
        return users.findByAuthToken(TokenHash.of(token))
                .filter(AppUser::isVerified)
                .filter(user -> user.getAuthTokenExpiresAt() != null && user.getAuthTokenExpiresAt().isAfter(java.time.LocalDateTime.now()))
                .orElseThrow(this::unauthorized);
    }

    /** Call inside a transaction before reading any financial entity being mutated. */
    public AppUser lockUser() {
        return users.findLockedById(requiredUser().getId()).orElseThrow(this::unauthorized);
    }

    private org.springframework.web.server.ResponseStatusException unauthorized() {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Sessão inválida ou expirada. Faça login novamente.");
    }

    private String tokenFromRequest() {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        return request.getHeader("X-Auth-Token");
    }
}
