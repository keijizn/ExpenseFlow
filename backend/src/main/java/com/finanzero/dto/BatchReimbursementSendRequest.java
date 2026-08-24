package com.finanzero.dto;

import java.util.List;

public record BatchReimbursementSendRequest(
        List<Long> transactionIds,
        String email,
        String company
) {}
