package com.finanzero.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FinanceTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @NotNull
    @Column(nullable = false)
    private LocalDate date;

    private LocalDate dueDate;
    private LocalDate receivedAt;
    private String paymentMethod;
    private String notes;

    @Builder.Default
    @Column(nullable = false)
    private boolean reimbursable = false;

    private String reimbursementCompany;
    private String reimbursementEmail;

    private String receiptFileName;
    private String receiptOriginalName;
    private String receiptContentType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReimbursementStatus reimbursementStatus = ReimbursementStatus.NOT_REIMBURSABLE;

    private LocalDate reimbursementSentAt;
    private LocalDate reimbursementReceivedAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @ManyToOne
    private Category category;

    @ManyToOne
    private WalletAccount account;

    @JsonIgnore
    @ManyToOne
    private AppUser owner;
}
