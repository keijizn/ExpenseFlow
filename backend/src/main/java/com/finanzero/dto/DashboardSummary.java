package com.finanzero.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardSummary(
        BigDecimal income,
        BigDecimal fixedExpenses,
        BigDecimal variableExpenses,
        BigDecimal debtsOpen,
        BigDecimal investments,
        BigDecimal availableBalance,
        List<CategoryUsage> categoryUsage,
        Map<String, BigDecimal> monthlyIncome,
        Map<String, BigDecimal> monthlyExpenses
) {}
