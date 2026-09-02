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

    public List<Debt> list() {
        AppUser owner = currentUserService.requiredUser();
        List<Debt> debts = debtRepository.findByOwnerOrderByIdDesc(owner);
        debts.forEach(this::normalizeStatusOnly);
        return debts;
    }

    @Transactional
    public Debt create(Debt debt) {
        AppUser owner = currentUserService.requiredUser();
        debt.setOwner(owner);
        attachAccountIfPresent(debt, owner);
        normalize(debt);
        return debtRepository.save(debt);
    }

    @Transactional
    public Debt update(Long id, Debt debt) {
        AppUser owner = currentUserService.requiredUser();
        Debt current = debtRepository.findByIdAndOwner(id, owner).orElseThrow(() -> new IllegalArgumentException("Dívida não encontrada"));
        debt.setId(current.getId());
        debt.setOwner(owner);
        attachAccountIfPresent(debt, owner);
        normalize(debt);
        return debtRepository.save(debt);
    }

    @Transactional
    public void delete(Long id) {
        AppUser owner = currentUserService.requiredUser();
        Debt debt = debtRepository.findByIdAndOwner(id, owner).orElseThrow(() -> new IllegalArgumentException("Dívida não encontrada"));
        debtRepository.delete(debt);
    }

    @Transactional
    public Debt pay(Long id, DebtPaymentRequest request) {
        AppUser owner = currentUserService.requiredUser();
        Debt debt = debtRepository.findByIdAndOwner(id, owner).orElseThrow(() -> new IllegalArgumentException("Dívida não encontrada"));
        BigDecimal amount = request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0 ? nvl(debt.getMonthlyPayment()) : request.amount();
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
        account.setBalance(nvl(account.getBalance()).subtract(amount));
        accountRepository.save(account);
        debt.setPaidAmount(nvl(debt.getPaidAmount()).add(amount));
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
        if (debt.getTotalInstallments() == null || debt.getTotalInstallments() <= 0) debt.setTotalInstallments(1);
        if (debt.getPaidAmount().compareTo(debt.getTotalAmount()) >= 0 && debt.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            debt.setPaidAmount(debt.getTotalAmount());
            debt.setStatus(PaymentStatus.PAID);
        } else if (debt.getNextDueDate() != null && debt.getNextDueDate().isBefore(LocalDate.now())) {
            debt.setStatus(PaymentStatus.OVERDUE);
        } else if (debt.getStatus() == null || debt.getStatus() == PaymentStatus.PAID) {
            debt.setStatus(PaymentStatus.PENDING);
        }
    }

    private void normalizeStatusOnly(Debt debt) {
        if (debt.getStatus() != PaymentStatus.PAID && debt.getNextDueDate() != null && debt.getNextDueDate().isBefore(LocalDate.now())) debt.setStatus(PaymentStatus.OVERDUE);
        if (nvl(debt.getPaidAmount()).compareTo(nvl(debt.getTotalAmount())) >= 0 && nvl(debt.getTotalAmount()).compareTo(BigDecimal.ZERO) > 0) debt.setStatus(PaymentStatus.PAID);
        debtRepository.save(debt);
    }

    private BigDecimal nvl(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
