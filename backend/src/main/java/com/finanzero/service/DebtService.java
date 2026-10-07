package com.finanzero.service;

import com.finanzero.dto.DebtPaymentRequest;
import com.finanzero.model.AppUser;
import com.finanzero.model.Debt;
import com.finanzero.model.PaymentStatus;
import com.finanzero.model.WalletAccount;
import com.finanzero.repository.DebtRepository;
import com.finanzero.repository.WalletAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DebtService {
    private final DebtRepository debtRepository;
    private final WalletAccountRepository accountRepository;
    private final CurrentUserService currentUserService;
    private final TransactionService transactions;
    private final com.finanzero.repository.FinanceTransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public List<Debt> list() {
        AppUser owner = currentUserService.requiredUser();
        List<Debt> debts = debtRepository.findByOwnerOrderByIdDesc(owner);
        debts.forEach(this::normalizeStatusOnly);
        return debts;
    }

    @Transactional
    public Debt create(Debt debt) {
        AppUser owner = currentUserService.lockUser();
        FinancialValidation.newEntity(debt.getId());
        debt.setInstallmentProgress(BigDecimal.ZERO);
        debt.setOwner(owner);
        attachAccountIfPresent(debt, owner);
        normalize(debt);
        return debtRepository.save(debt);
    }

    @Transactional
    public Debt update(Long id, Debt debt) {
        AppUser owner = currentUserService.lockUser();
        Debt current = debtRepository.findByIdAndOwner(id, owner).orElseThrow(() -> new IllegalArgumentException("Dívida não encontrada"));
        if (nvl(debt.getPaidAmount()).compareTo(nvl(current.getPaidAmount())) != 0)
            FinancialValidation.conflict("Use Pagar parcela para alterar o valor pago.");
        if (nvl(debt.getTotalAmount()).compareTo(nvl(current.getPaidAmount())) < 0)
            throw new IllegalArgumentException("O total não pode ser menor que o valor pago.");
        debt.setInstallmentProgress(current.getInstallmentProgress());
        debt.setId(current.getId());
        debt.setOwner(owner);
        attachAccountIfPresent(debt, owner);
        normalize(debt);
        return debtRepository.save(debt);
    }

    @Transactional
    public void delete(Long id) {
        AppUser owner = currentUserService.lockUser();
        Debt debt = debtRepository.findByIdAndOwner(id, owner).orElseThrow(() -> new IllegalArgumentException("Dívida não encontrada"));
        if (transactionRepository.existsByDebtPaymentIdAndOwner(id, owner))
            FinancialValidation.conflict("Esta dívida possui pagamentos no histórico e não pode ser excluída.");
        debtRepository.delete(debt);
    }

    @Transactional
    public Debt pay(Long id, DebtPaymentRequest request) {
        AppUser owner = currentUserService.lockUser();
        Debt debt = debtRepository.findByIdAndOwner(id, owner).orElseThrow(() -> new IllegalArgumentException("Dívida não encontrada"));
        BigDecimal amount = FinancialValidation.money(request.amount() == null ? nvl(debt.getMonthlyPayment()) : request.amount(), "Pagamento", false);
        BigDecimal remaining = nvl(debt.getTotalAmount()).subtract(nvl(debt.getPaidAmount())).max(BigDecimal.ZERO);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            debt.setStatus(PaymentStatus.PAID);
            return debtRepository.save(debt);
        }
        if (amount.compareTo(remaining) > 0) throw new IllegalArgumentException("O valor informado é maior que o restante da dívida.");
        Long selectedAccountId = request.accountId();
        if (selectedAccountId == null && debt.getAccount() != null) {
            selectedAccountId = debt.getAccount().getId();
        }
        if (selectedAccountId == null) {
            throw new IllegalArgumentException("Selecione a conta usada para pagar a parcela.");
        }
        WalletAccount account = accountRepository.findByIdAndOwner(selectedAccountId, owner).orElseThrow(() -> new IllegalArgumentException("Conta não encontrada"));
        debt.setAccount(account);
        var payment = transactions.createForUser(owner, new com.finanzero.dto.TransactionRequest(
                "Parcela - " + debt.getName(), com.finanzero.model.TransactionType.VARIABLE_EXPENSE,
                amount, LocalDate.now(), null, null, "Débito", "Pagamento da dívida " + debt.getId(),
                false, null, null, com.finanzero.model.ReimbursementStatus.NOT_REIMBURSABLE,
                PaymentStatus.PAID, null, account.getId()));
        payment.setDebtPaymentId(debt.getId());
        transactionRepository.save(payment);
        debt.setPaidAmount(nvl(debt.getPaidAmount()).add(amount));
        BigDecimal progress = nvl(debt.getInstallmentProgress()).add(amount);
        BigDecimal installment = nvl(debt.getMonthlyPayment());
        if (installment.signum() > 0 && debt.getNextDueDate() != null) {
            long completed = progress.divideToIntegralValue(installment).longValueExact();
            debt.setNextDueDate(debt.getNextDueDate().plusMonths(completed));
            progress = progress.remainder(installment);
        }
        debt.setInstallmentProgress(progress);
        normalize(debt);
        return debtRepository.save(debt);
    }

    private void attachAccountIfPresent(Debt debt, AppUser owner) {
        if (debt.getAccount() == null || debt.getAccount().getId() == null) {
            debt.setAccount(null);
            return;
        }
        WalletAccount account = accountRepository.findByIdAndOwner(debt.getAccount().getId(), owner)
                .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada"));
        debt.setAccount(account);
    }

    private void normalize(Debt debt) {
        if (debt.getTotalAmount() == null) debt.setTotalAmount(BigDecimal.ZERO);
        if (debt.getPaidAmount() == null) debt.setPaidAmount(BigDecimal.ZERO);
        if (debt.getMonthlyPayment() == null) debt.setMonthlyPayment(BigDecimal.ZERO);
        FinancialValidation.money(debt.getTotalAmount(), "Total", false);
        FinancialValidation.money(debt.getPaidAmount(), "Valor pago", true);
        FinancialValidation.money(debt.getMonthlyPayment(), "Parcela", false);
        if (debt.getPaidAmount().compareTo(debt.getTotalAmount()) > 0) throw new IllegalArgumentException("Valor pago maior que o total.");
        if (debt.getTotalInstallments() == null || debt.getTotalInstallments() <= 0) debt.setTotalInstallments(1);
        if (debt.getPaidAmount().compareTo(debt.getTotalAmount()) >= 0 && debt.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            debt.setPaidAmount(debt.getTotalAmount());
            debt.setStatus(PaymentStatus.PAID);
        } else if (debt.getNextDueDate() != null && debt.getNextDueDate().isBefore(LocalDate.now())) {
            debt.setStatus(PaymentStatus.OVERDUE);
        } else {
            debt.setStatus(PaymentStatus.PENDING);
        }
    }

    private void normalizeStatusOnly(Debt debt) {
        if (debt.getStatus() != PaymentStatus.PAID && debt.getNextDueDate() != null && debt.getNextDueDate().isBefore(LocalDate.now())) debt.setStatus(PaymentStatus.OVERDUE);
        if (nvl(debt.getPaidAmount()).compareTo(nvl(debt.getTotalAmount())) >= 0 && nvl(debt.getTotalAmount()).compareTo(BigDecimal.ZERO) > 0) debt.setStatus(PaymentStatus.PAID);
    }

    private BigDecimal nvl(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
