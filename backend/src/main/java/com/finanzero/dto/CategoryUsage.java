package com.finanzero.dto;

import java.math.BigDecimal;

public record CategoryUsage(String category, String color, BigDecimal spent, BigDecimal limitValue, double percent) {}
