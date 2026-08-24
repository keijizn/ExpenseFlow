package com.finanzero.dto;

import java.math.BigDecimal;

public record DebtPaymentRequest(
        Long accountId,
        BigDecimal amount
) {}
