package com.finanzero.dto;

public record SendReimbursementRequest(
        String email,
        String company
) {}
