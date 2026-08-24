package com.finanzero.dto;

import java.time.LocalDate;

public record ReimbursementReceiveRequest(
        Long accountId,
        LocalDate receivedAt
) {}
