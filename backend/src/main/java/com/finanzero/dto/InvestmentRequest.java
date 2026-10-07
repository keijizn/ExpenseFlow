package com.finanzero.dto;

import java.math.BigDecimal;

public record InvestmentRequest(
        @jakarta.validation.constraints.NotBlank String name,
        String investmentType,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero BigDecimal amount,
        BigDecimal profitabilityPercent,
        @jakarta.validation.constraints.NotNull Long accountId
) {}
