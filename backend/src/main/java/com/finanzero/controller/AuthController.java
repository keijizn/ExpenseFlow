package com.finanzero.controller;

import com.finanzero.dto.*;
import com.finanzero.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService service;

    @PostMapping("/register")
    public AuthResponse register(@RequestBody @Valid RegisterRequest request) {
        return service.register(request);
    }

    @PostMapping("/verify")
    public AuthResponse verify(@RequestBody @Valid VerifyEmailRequest request) {
        return service.verify(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody @Valid LoginRequest request) {
        return service.login(request);
    }

    @PostMapping("/resend")
    public AuthResponse resend(@RequestBody Map<String, String> body) {
        return service.resend(body.get("email"));
    }

    @PostMapping("/forgot-password")
    public AuthMessageResponse forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        return service.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public AuthMessageResponse resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        return service.resetPassword(request);
    }
}
