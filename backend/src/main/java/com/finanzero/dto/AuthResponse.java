package com.finanzero.dto;

public record AuthResponse(
        String token,
        String name,
        String email,
        boolean verified,
        String role,
        String message,
        String debugVerificationCode
) {}
