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
import java.security.SecureRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AppUserRepository users;
    private final CategoryRepository categories;
    private final WalletAccountRepository accounts;
    private final EmailService emailService;
    private final AuthRateLimiter limiter;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${app.mail.enabled:false}")
    private boolean realMailEnabled;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        validatePassword(request.password());
        limit("register", email, 3);
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
        limit("verify", request.email(), 5);
        AppUser user = users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        if (user.isVerified()) {
            return new AuthResponse(null, user.getName(), user.getEmail(), true, user.getRole(), "E-mail já verificado. Faça login com sua senha.", null);
        }
        if (user.getVerificationCode() == null || !user.getVerificationCode().equals(request.code().trim())) {
            throw new IllegalArgumentException("Código de verificação inválido.");
        }
        if (user.getVerificationExpiresAt() == null || !user.getVerificationExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Código expirado. Peça um novo código.");
        }
        user.setVerified(true);
        user.setVerificationCode(null);
        user.setVerificationExpiresAt(null);
        return issueSession(user, "E-mail verificado com sucesso.");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        limit("login", request.email(), 10);
        AppUser user = users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new IllegalArgumentException("E-mail ou senha inválidos."));
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("E-mail ou senha inválidos.");
        }
        if (!user.isVerified()) {
            mailCooldown(user.getEmail());
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
        limit("forgot", email, 3);
        mailCooldown(email);
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
        limit("reset", request.email(), 5);
        AppUser user = users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new IllegalArgumentException("Código inválido ou expirado."));
        if (user.getPasswordResetCode() == null || !user.getPasswordResetCode().equals(request.code().trim())) {
            throw new IllegalArgumentException("Código inválido ou expirado.");
        }
        if (user.getPasswordResetExpiresAt() == null || user.getPasswordResetExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Código expirado. Peça uma nova redefinição de senha.");
        }
        validatePassword(request.newPassword());
        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setPasswordResetCode(null);
        user.setPasswordResetExpiresAt(null);
        user.setAuthToken(null);
        user.setAuthTokenExpiresAt(null);
        user.setVerified(true);
        users.save(user);
        return new AuthMessageResponse("Senha redefinida com sucesso. Faça login novamente.");
    }

    @Transactional
    public AuthResponse resend(String email) {
        limit("resend", email, 3);
        mailCooldown(email);
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
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        user.setAuthToken(TokenHash.of(token));
        user.setAuthTokenExpiresAt(LocalDateTime.now().plusHours(24));
        users.save(user);
        return new AuthResponse(token, user.getName(), user.getEmail(), user.isVerified(), user.getRole(), message, null);
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
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void limit(String action, String email, int attempts) {
        limiter.check(action + ":" + normalizeEmail(email), attempts, java.time.Duration.ofMinutes(15));
    }

    private void mailCooldown(String email) {
        limiter.check("mail:" + normalizeEmail(email), 1, java.time.Duration.ofMinutes(1));
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha deve ter pelo menos 8 caracteres e no máximo 72 bytes.");
        }
    }
}
