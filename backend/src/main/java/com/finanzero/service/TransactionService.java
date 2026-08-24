package com.finanzero.service;

import com.finanzero.dto.TransactionRequest;
import com.finanzero.model.*;
import com.finanzero.repository.CategoryRepository;
import com.finanzero.repository.FinanceTransactionRepository;
import com.finanzero.repository.WalletAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {
    private final FinanceTransactionRepository repository;
    private final CategoryRepository categoryRepository;
    private final WalletAccountRepository accountRepository;
    private final CurrentUserService currentUserService;
    private final ReceiptStorageService receiptStorageService;

    @Transactional
    public List<FinanceTransaction> list(TransactionType type, LocalDate start, LocalDate end) {
        AppUser owner = currentUserService.requiredUser();
        List<FinanceTransaction> result;
        if (start != null && end != null && type != null) {
            result = repository.findByOwnerAndTypeAndDateBetweenOrderByDateDesc(owner, type, start, end);
        } else if (start != null && end != null) {
            result = repository.findByOwnerAndDateBetweenOrderByDateDesc(owner, start, end);
        } else {
            result = repository.findByOwnerOrderByDateDesc(owner);
        }
        result.forEach(this::markOverdueIfNeeded);
        return result;
    }

    @Transactional
    public FinanceTransaction create(TransactionRequest request) {
        return createForUser(currentUserService.requiredUser(), request);
    }

    @Transactional
    public FinanceTransaction createForUser(AppUser owner, TransactionRequest request) {
        FinanceTransaction entity = toEntity(new FinanceTransaction(), request, owner);
        entity.setOwner(owner);
        normalize(entity);
        FinanceTransaction saved = repository.save(entity);
        applyBalanceImpact(saved);
        return saved;
    }

    @Transactional
    public FinanceTransaction update(Long id, TransactionRequest request) {
        AppUser owner = currentUserService.requiredUser();
        FinanceTransaction entity = repository.findByIdAndOwner(id, owner).orElseThrow(() -> new IllegalArgumentException("Transação não encontrada"));
        reverseBalanceImpact(entity);
        toEntity(entity, request, owner);
        normalize(entity);
        FinanceTransaction saved = repository.save(entity);
        applyBalanceImpact(saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        AppUser owner = currentUserService.requiredUser();
        FinanceTransaction entity = repository.findByIdAndOwner(id, owner).orElseThrow(() -> new IllegalArgumentException("Transação não encontrada"));
        reverseBalanceImpact(entity);
        receiptStorageService.deleteQuietly(entity.getReceiptFileName());
        repository.delete(entity);
    }

    private FinanceTransaction toEntity(FinanceTransaction entity, TransactionRequest r, AppUser owner) {
        entity.setDescription(r.description());
        entity.setType(r.type());
        entity.setAmount(nvl(r.amount()));
        entity.setDate(r.date());
        entity.setDueDate(r.dueDate());
        entity.setReceivedAt(r.receivedAt());
        entity.setPaymentMethod(r.paymentMethod());
        entity.setNotes(r.notes());
        entity.setReimbursable(Boolean.TRUE.equals(r.reimbursable()));
        entity.setReimbursementCompany(r.reimbursementCompany());
        entity.setReimbursementEmail(r.reimbursementEmail());
        entity.setReimbursementStatus(r.reimbursementStatus());
        entity.setStatus(r.status());
        entity.setCategory(r.categoryId() == null ? null : categoryRepository.findByIdAndOwner(r.categoryId(), owner).orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada")));
        entity.setAccount(r.accountId() == null ? null : accountRepository.findByIdAndOwner(r.accountId(), owner).orElseThrow(() -> new IllegalArgumentException("Conta não encontrada")));
        entity.setOwner(owner);
        return entity;
    }

    private void markOverdueIfNeeded(FinanceTransaction entity) {
        if (entity.getType() == TransactionType.FIXED_EXPENSE
                && entity.getStatus() == PaymentStatus.PENDING
                && entity.getDueDate() != null
                && entity.getDueDate().isBefore(LocalDate.now())) {
            entity.setStatus(PaymentStatus.OVERDUE);
            repository.save(entity);
        }
    }

    private void normalize(FinanceTransaction entity) {
        if (entity.getType() == TransactionType.INCOME) {
            entity.setStatus(PaymentStatus.RECEIVED);
            entity.setReimbursable(false);
            entity.setReimbursementStatus(ReimbursementStatus.NOT_REIMBURSABLE);
            if (entity.getReceivedAt() == null) entity.setReceivedAt(entity.getDate());
            if (isBlank(entity.getPaymentMethod())) entity.setPaymentMethod("Pix");
        }

        if (entity.getType() == TransactionType.VARIABLE_EXPENSE) {
            entity.setStatus(PaymentStatus.PAID);
            if (isBlank(entity.getPaymentMethod())) entity.setPaymentMethod("Pix");
            normalizeReimbursement(entity);
        }

        if (entity.getType() == TransactionType.FIXED_EXPENSE) {
            if (entity.getDueDate() == null) entity.setDueDate(entity.getDate());
            if (entity.getStatus() == null) entity.setStatus(PaymentStatus.PENDING);
            if (entity.getStatus() == PaymentStatus.PENDING && entity.getDueDate() != null && entity.getDueDate().isBefore(LocalDate.now())) {
                entity.setStatus(PaymentStatus.OVERDUE);
            }
            if (isBlank(entity.getPaymentMethod())) entity.setPaymentMethod("Débito");
            normalizeReimbursement(entity);
        }
    }

    private void normalizeReimbursement(FinanceTransaction entity) {
        if (!entity.isReimbursable()) {
            entity.setReimbursementStatus(ReimbursementStatus.NOT_REIMBURSABLE);
            entity.setReimbursementCompany(null);
            entity.setReimbursementEmail(null);
            entity.setReimbursementSentAt(null);
            entity.setReimbursementReceivedAt(null);
            return;
        }
        if (entity.getReimbursementStatus() == null || entity.getReimbursementStatus() == ReimbursementStatus.NOT_REIMBURSABLE) {
            entity.setReimbursementStatus(ReimbursementStatus.TO_SEND);
        }
    }

    private void applyBalanceImpact(FinanceTransaction transaction) { applyAccountImpact(transaction, 1); }
    private void reverseBalanceImpact(FinanceTransaction transaction) { applyAccountImpact(transaction, -1); }

    private void applyAccountImpact(FinanceTransaction transaction, int direction) {
        if (transaction == null || transaction.getAccount() == null) return;
        BigDecimal balanceDelta = balanceDelta(transaction).multiply(BigDecimal.valueOf(direction));
        BigDecimal cardLimitDelta = cardLimitDelta(transaction).multiply(BigDecimal.valueOf(direction));
        if (balanceDelta.compareTo(BigDecimal.ZERO) == 0 && cardLimitDelta.compareTo(BigDecimal.ZERO) == 0) return;
        WalletAccount account = transaction.getAccount();
        account.setBalance(nvl(account.getBalance()).add(balanceDelta));
        account.setCardLimit(nvl(account.getCardLimit()).add(cardLimitDelta));
        accountRepository.save(account);
    }

    private BigDecimal balanceDelta(FinanceTransaction transaction) {
        if (transaction == null || transaction.getType() == null) return BigDecimal.ZERO;
        BigDecimal amount = nvl(transaction.getAmount());
        if (transaction.getType() == TransactionType.INCOME) return amount;
        if (transaction.getType() == TransactionType.VARIABLE_EXPENSE) return impactsBalanceImmediately(transaction.getPaymentMethod()) ? amount.negate() : BigDecimal.ZERO;
        if (transaction.getType() == TransactionType.FIXED_EXPENSE) return transaction.getStatus() == PaymentStatus.PAID && impactsBalanceImmediately(transaction.getPaymentMethod()) ? amount.negate() : BigDecimal.ZERO;
        return BigDecimal.ZERO;
    }

    private BigDecimal cardLimitDelta(FinanceTransaction transaction) {
        if (transaction == null || transaction.getType() == null) return BigDecimal.ZERO;
        BigDecimal amount = nvl(transaction.getAmount());
        if (transaction.getType() == TransactionType.VARIABLE_EXPENSE && impactsCardLimit(transaction.getPaymentMethod())) return amount.negate();
        if (transaction.getType() == TransactionType.FIXED_EXPENSE && transaction.getStatus() == PaymentStatus.PAID && impactsCardLimit(transaction.getPaymentMethod())) return amount.negate();
        return BigDecimal.ZERO;
    }

    private boolean impactsBalanceImmediately(String paymentMethod) {
        if (paymentMethod == null) return true;
        String method = paymentMethod.trim().toLowerCase();
        return method.equals("pix") || method.equals("débito") || method.equals("debito");
    }

    private boolean impactsCardLimit(String paymentMethod) {
        if (paymentMethod == null) return false;
        String method = paymentMethod.trim().toLowerCase();
        return method.equals("crédito") || method.equals("credito");
    }

    private BigDecimal nvl(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
}
