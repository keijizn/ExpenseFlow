package com.finanzero.service;

import com.finanzero.dto.*;
import com.finanzero.model.*;
import com.finanzero.repository.AppUserRepository;
import com.finanzero.repository.CategoryRepository;
import com.finanzero.repository.WalletAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AppUserRepository users;
    private final CategoryRepository categories;
    private final WalletAccountRepository accounts;
    private final EmailService emailService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${app.mail.enabled:false}")
    private boolean realMailEnabled;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Já existe uma conta cadastrada com esse e-mail.");
        }
        String code = generateCode();
        AppUser user = AppUser.builder()
                .name(request.name().trim())
                .email(email)
                .passwordHash(encoder.encode(request.password()))
                .verified(false)
                .role("USER")
                .verificationCode(code)
                .verificationExpiresAt(LocalDateTime.now().plusMinutes(15))
                .build();
        users.save(user);
        createStarterData(user);
        sendVerificationEmail(user, code);
        return new AuthResponse(null, user.getName(), user.getEmail(), false, user.getRole(), "Cadastro criado. Verifique seu e-mail com o código enviado.", debugCode(code));
    }

    @Transactional
    public AuthResponse verify(VerifyEmailRequest request) {
        AppUser user = users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        if (user.isVerified()) {
            return issueSession(user, "E-mail já estava verificado.");
        }
        if (user.getVerificationCode() == null || !user.getVerificationCode().equals(request.code().trim())) {
            throw new IllegalArgumentException("Código de verificação inválido.");
        }
        if (user.getVerificationExpiresAt() != null && user.getVerificationExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Código expirado. Peça um novo código.");
        }
        user.setVerified(true);
        user.setVerificationCode(null);
        user.setVerificationExpiresAt(null);
        return issueSession(user, "E-mail verificado com sucesso.");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        AppUser user = users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new IllegalArgumentException("E-mail ou senha inválidos."));
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("E-mail ou senha inválidos.");
        }
        if (!user.isVerified()) {
            String code = generateCode();
            user.setVerificationCode(code);
            user.setVerificationExpiresAt(LocalDateTime.now().plusMinutes(15));
            users.save(user);
            sendVerificationEmail(user, code);
            return new AuthResponse(null, user.getName(), user.getEmail(), false, user.getRole(), "E-mail ainda não verificado. Enviamos um novo código.", debugCode(code));
        }
        return issueSession(user, "Login realizado com sucesso.");
    }


    @Transactional
    public AuthMessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        users.findByEmailIgnoreCase(email).ifPresent(user -> {
            String code = generateCode();
            user.setPasswordResetCode(code);
            user.setPasswordResetExpiresAt(LocalDateTime.now().plusMinutes(15));
            users.save(user);
            sendPasswordResetEmail(user, code);
        });
        return new AuthMessageResponse("Se o e-mail estiver cadastrado, enviamos um código para redefinir sua senha.");
    }

    @Transactional
    public AuthMessageResponse resetPassword(ResetPasswordRequest request) {
        AppUser user = users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new IllegalArgumentException("Código inválido ou expirado."));
        if (user.getPasswordResetCode() == null || !user.getPasswordResetCode().equals(request.code().trim())) {
            throw new IllegalArgumentException("Código inválido ou expirado.");
        }
        if (user.getPasswordResetExpiresAt() == null || user.getPasswordResetExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Código expirado. Peça uma nova redefinição de senha.");
        }
        if (request.newPassword() == null || request.newPassword().trim().length() < 6) {
            throw new IllegalArgumentException("A nova senha precisa ter pelo menos 6 caracteres.");
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setPasswordResetCode(null);
        user.setPasswordResetExpiresAt(null);
        user.setAuthToken(null);
        user.setVerified(true);
        users.save(user);
        return new AuthMessageResponse("Senha redefinida com sucesso. Faça login novamente.");
    }

    @Transactional
    public AuthResponse resend(String email) {
        AppUser user = users.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        if (user.isVerified()) return new AuthResponse(null, user.getName(), user.getEmail(), true, user.getRole(), "E-mail já verificado.", null);
        String code = generateCode();
        user.setVerificationCode(code);
        user.setVerificationExpiresAt(LocalDateTime.now().plusMinutes(15));
        users.save(user);
        createStarterData(user);
        sendVerificationEmail(user, code);
        return new AuthResponse(null, user.getName(), user.getEmail(), false, user.getRole(), "Novo código enviado.", debugCode(code));
    }

    private AuthResponse issueSession(AppUser user, String message) {
        user.setAuthToken(UUID.randomUUID().toString());
        users.save(user);
        return new AuthResponse(user.getAuthToken(), user.getName(), user.getEmail(), user.isVerified(), user.getRole(), message, null);
    }

    private void createStarterData(AppUser user) {
        if (!categories.findByOwnerOrderByName(user).isEmpty()) return;
        accounts.save(WalletAccount.builder().name("Minha conta").balance(BigDecimal.ZERO).cardLimit(BigDecimal.ZERO).owner(user).build());
        categories.save(Category.builder().name("Salário").icon("💼").color("#a3e635").type(TransactionType.INCOME).monthlyLimit(BigDecimal.ZERO).owner(user).build());
        categories.save(Category.builder().name("Renda extra").icon("🚀").color("#c084fc").type(TransactionType.INCOME).monthlyLimit(BigDecimal.ZERO).owner(user).build());
        categories.save(Category.builder().name("Casa").icon("🏠").color("#38bdf8").type(TransactionType.FIXED_EXPENSE).monthlyLimit(BigDecimal.ZERO).owner(user).build());
        categories.save(Category.builder().name("Saúde").icon("💊").color("#fb7185").type(TransactionType.FIXED_EXPENSE).monthlyLimit(BigDecimal.ZERO).owner(user).build());
        categories.save(Category.builder().name("Mercado").icon("🛒").color("#f97316").type(TransactionType.VARIABLE_EXPENSE).monthlyLimit(BigDecimal.ZERO).owner(user).build());
        categories.save(Category.builder().name("Transporte").icon("🚗").color("#60a5fa").type(TransactionType.VARIABLE_EXPENSE).monthlyLimit(BigDecimal.ZERO).owner(user).build());
        categories.save(Category.builder().name("Lazer").icon("🎮").color("#e879f9").type(TransactionType.VARIABLE_EXPENSE).monthlyLimit(BigDecimal.ZERO).owner(user).build());
    }


    private void sendPasswordResetEmail(AppUser user, String code) {
        String body = "Olá, " + user.getName() + "!\n\n" +
                "Seu código para redefinir a senha do FinanZero é: " + code + "\n\n" +
                "Ele expira em 15 minutos. Se você não solicitou isso, ignore este e-mail.";
        emailService.send(user.getEmail(), "Redefinição de senha - FinanZero", body);
    }

    private void sendVerificationEmail(AppUser user, String code) {
        String body = "Olá, " + user.getName() + "!\n\n" +
                "Seu código de verificação do FinanZero é: " + code + "\n\n" +
                "Ele expira em 15 minutos.\n\n" +
                "Se você não criou essa conta, ignore este e-mail.";
        emailService.send(user.getEmail(), "Código de verificação - FinanZero", body);
    }

    private String debugCode(String code) {
        return realMailEnabled ? null : code;
    }

    private String generateCode() {
        return String.format("%06d", new Random().nextInt(1_000_000));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
