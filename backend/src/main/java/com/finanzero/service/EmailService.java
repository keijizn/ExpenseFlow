package com.finanzero.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmailService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.emailjs.service-id:}")
    private String emailJsServiceId;

    @Value("${app.mail.emailjs.template-id:}")
    private String emailJsTemplateId;

    @Value("${app.mail.emailjs.public-key:}")
    private String emailJsPublicKey;

    @Value("${app.mail.emailjs.private-key:}")
    private String emailJsPrivateKey;

    @Value("${app.mail.emailjs.base-url:https://api.emailjs.com/api/v1.0/email/send}")
    private String emailJsBaseUrl;

    @Value("${app.mail.from-name:FinanZero}")
    private String fromName;

    @Value("${app.mail.reply-to:noreply@finanzero.local}")
    private String replyTo;

    public boolean send(String to, String subject, String body) {
        if (!mailEnabled) {
            printSimulatedEmail(to, subject, body);
            return false;
        }

        validateEmailJsConfig();
        return sendWithEmailJs(to, subject, body);
    }

    private boolean sendWithEmailJs(String to, String subject, String body) {
        String code = extractCode(body);
        String json = "{"
                + "\"service_id\":\"" + jsonEscape(emailJsServiceId) + "\","
                + "\"template_id\":\"" + jsonEscape(emailJsTemplateId) + "\","
                + "\"user_id\":\"" + jsonEscape(emailJsPublicKey) + "\","
                + optionalAccessTokenJson()
                + "\"template_params\":{"
                + "\"to_email\":\"" + jsonEscape(to) + "\","
                + "\"to_name\":\"" + jsonEscape(extractNameFromEmail(to)) + "\","
                + "\"from_name\":\"" + jsonEscape(fromName) + "\","
                + "\"reply_to\":\"" + jsonEscape(replyTo) + "\","
                + "\"subject\":\"" + jsonEscape(subject) + "\","
                + "\"message\":\"" + jsonEscape(body) + "\","
                + "\"message_html\":\"" + jsonEscape(body) + "\","
                + "\"code\":\"" + jsonEscape(code) + "\","
                + "\"app_name\":\"FinanZero\""
                + "}"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(emailJsBaseUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("E-mail enviado via EmailJS para " + to + ". Resposta: " + response.body());
                return true;
            }

            throw new IllegalStateException("Erro ao enviar e-mail via EmailJS. HTTP "
                    + response.statusCode() + ": " + response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Falha de conexão ao enviar e-mail via EmailJS: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envio de e-mail via EmailJS interrompido.", e);
        }
    }

    private void validateEmailJsConfig() {
        if (isBlank(emailJsServiceId)) {
            throw new IllegalStateException("EmailJS não configurado. Informe app.mail.emailjs.service-id ou EMAILJS_SERVICE_ID.");
        }
        if (isBlank(emailJsTemplateId)) {
            throw new IllegalStateException("EmailJS não configurado. Informe app.mail.emailjs.template-id ou EMAILJS_TEMPLATE_ID.");
        }
        if (isBlank(emailJsPublicKey)) {
            throw new IllegalStateException("EmailJS não configurado. Informe app.mail.emailjs.public-key ou EMAILJS_PUBLIC_KEY.");
        }
    }

    private String optionalAccessTokenJson() {
        if (isBlank(emailJsPrivateKey)) return "";
        return "\"accessToken\":\"" + jsonEscape(emailJsPrivateKey) + "\",";
    }

    private void printSimulatedEmail(String to, String subject, String body) {
        System.out.println("\n===== EMAIL SIMULADO FINANZERO =====");
        System.out.println("Para: " + to);
        System.out.println("Assunto: " + subject);
        System.out.println(body);
        System.out.println("===== FIM EMAIL SIMULADO =====\n");
    }

    private String extractCode(String body) {
        if (body == null) return "";
        Matcher matcher = Pattern.compile("\\b\\d{6}\\b").matcher(body);
        return matcher.find() ? matcher.group() : "";
    }

    private String extractNameFromEmail(String email) {
        if (email == null || email.isBlank()) return "Usuário";
        int at = email.indexOf('@');
        if (at <= 0) return email.trim();
        String name = email.substring(0, at).replace('.', ' ').replace('_', ' ').replace('-', ' ').trim();
        return name.isBlank() ? "Usuário" : name;
    }

    private String jsonEscape(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
