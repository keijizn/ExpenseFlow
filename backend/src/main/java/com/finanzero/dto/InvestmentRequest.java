package com.finanzero.dto;

import java.math.BigDecimal;

public record InvestmentRequest(
        String name,
        String investmentType,
        BigDecimal amount,
        BigDecimal profitabilityPercent,
        Long accountId
) {}
