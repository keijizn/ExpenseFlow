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
            throw new IllegalArgumentException("Faça login para acessar o sistema.");
        }
        return users.findByAuthToken(token)
                .filter(AppUser::isVerified)
                .orElseThrow(() -> new IllegalArgumentException("Sessão inválida ou e-mail ainda não verificado."));
    }

    private String tokenFromRequest() {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        return request.getHeader("X-Auth-Token");
    }
}
