package com.finanzero.dto;

import com.finanzero.model.AppUser;

public record UserResponse(Long id, String name, String email, Boolean emailVerified, String role) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.isVerified(), user.getRole());
    }
}
