package com.finanzero.dto;

import com.finanzero.model.PaymentStatus;
import com.finanzero.model.ReimbursementStatus;
import com.finanzero.model.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TransactionRequest {
    @NotBlank
    private String description;

    @NotNull
    private TransactionType type;

    @NotNull
    private BigDecimal amount;

    @NotNull
    private LocalDate date;

    private LocalDate dueDate;
    private LocalDate receivedAt;
    private String paymentMethod;
    private String notes;
    private Boolean reimbursable;
    private String reimbursementCompany;
    private String reimbursementEmail;
    private ReimbursementStatus reimbursementStatus;

    @NotNull
    private PaymentStatus status;

    private Long categoryId;
    private Long accountId;

    public TransactionRequest() {
    }

    public TransactionRequest(String description, TransactionType type, BigDecimal amount, LocalDate date, LocalDate dueDate,
                              LocalDate receivedAt, String paymentMethod, String notes, Boolean reimbursable,
                              String reimbursementCompany, String reimbursementEmail, ReimbursementStatus reimbursementStatus,
                              PaymentStatus status, Long categoryId, Long accountId) {
        this.description = description;
        this.type = type;
        this.amount = amount;
        this.date = date;
        this.dueDate = dueDate;
        this.receivedAt = receivedAt;
        this.paymentMethod = paymentMethod;
        this.notes = notes;
        this.reimbursable = reimbursable;
        this.reimbursementCompany = reimbursementCompany;
        this.reimbursementEmail = reimbursementEmail;
        this.reimbursementStatus = reimbursementStatus;
        this.status = status;
        this.categoryId = categoryId;
        this.accountId = accountId;
    }

    public String description() { return description; }
    public TransactionType type() { return type; }
    public BigDecimal amount() { return amount; }
    public LocalDate date() { return date; }
    public LocalDate dueDate() { return dueDate; }
    public LocalDate receivedAt() { return receivedAt; }
    public String paymentMethod() { return paymentMethod; }
    public String notes() { return notes; }
    public Boolean reimbursable() { return reimbursable; }
    public String reimbursementCompany() { return reimbursementCompany; }
    public String reimbursementEmail() { return reimbursementEmail; }
    public ReimbursementStatus reimbursementStatus() { return reimbursementStatus; }
    public PaymentStatus status() { return status; }
    public Long categoryId() { return categoryId; }
    public Long accountId() { return accountId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public LocalDate getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDate receivedAt) { this.receivedAt = receivedAt; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Boolean getReimbursable() { return reimbursable; }
    public void setReimbursable(Boolean reimbursable) { this.reimbursable = reimbursable; }

    public String getReimbursementCompany() { return reimbursementCompany; }
    public void setReimbursementCompany(String reimbursementCompany) { this.reimbursementCompany = reimbursementCompany; }

    public String getReimbursementEmail() { return reimbursementEmail; }
    public void setReimbursementEmail(String reimbursementEmail) { this.reimbursementEmail = reimbursementEmail; }

    public ReimbursementStatus getReimbursementStatus() { return reimbursementStatus; }
    public void setReimbursementStatus(ReimbursementStatus reimbursementStatus) { this.reimbursementStatus = reimbursementStatus; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
}
