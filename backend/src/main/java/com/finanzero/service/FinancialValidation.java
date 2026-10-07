package com.finanzero.service;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class FinancialValidation {
    private FinancialValidation() {}

    public static BigDecimal money(BigDecimal value, String field, boolean allowZero) {
        if (value == null || value.signum() < 0 || (!allowZero && value.signum() == 0)
                || value.stripTrailingZeros().scale() > 2 || value.abs().compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException(field + " deve ser " + (allowZero ? "não negativo" : "maior que zero") + " e ter até duas casas decimais.");
        }
        return value;
    }

    public static void newEntity(Long id) {
        if (id != null) throw new IllegalArgumentException("Não envie ID ao criar um registro.");
    }

    public static void conflict(String message) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
