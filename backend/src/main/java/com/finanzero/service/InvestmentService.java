package com.finanzero.service;

import com.finanzero.dto.InvestmentRequest;
import com.finanzero.model.AppUser;
import com.finanzero.model.Investment;
import com.finanzero.model.WalletAccount;
import com.finanzero.repository.InvestmentRepository;
import com.finanzero.repository.WalletAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvestmentService {
    private final InvestmentRepository investmentRepository;
    private final WalletAccountRepository accountRepository;
    private final CurrentUserService currentUserService;

    public List<Investment> list() { return investmentRepository.findByOwnerOrderByIdDesc(currentUserService.requiredUser()); }

    @Transactional
    public Investment create(InvestmentRequest request) {
        AppUser owner = currentUserService.requiredUser();
        WalletAccount account = accountRepository.findByIdAndOwner(request.accountId(), owner).orElseThrow(() -> new IllegalArgumentException("Conta de origem não encontrada"));
        BigDecimal amount = nvl(request.amount());
        account.setBalance(nvl(account.getBalance()).subtract(amount));
        accountRepository.save(account);
        Investment inv = Investment.builder()
                .name(request.name())
                .investmentType(request.investmentType())
                .amount(amount)
                .profitabilityPercent(nvl(request.profitabilityPercent()))
                .account(account)
                .owner(owner)
                .build();
        return investmentRepository.save(inv);
    }

    @Transactional
    public Investment update(Long id, InvestmentRequest request) {
        AppUser owner = currentUserService.requiredUser();
        Investment inv = investmentRepository.findByIdAndOwner(id, owner).orElseThrow(() -> new IllegalArgumentException("Economia não encontrada"));
        WalletAccount oldAccount = inv.getAccount();
        WalletAccount newAccount = accountRepository.findByIdAndOwner(request.accountId(), owner).orElseThrow(() -> new IllegalArgumentException("Conta de origem não encontrada"));
        BigDecimal oldAmount = nvl(inv.getAmount());
        BigDecimal newAmount = nvl(request.amount());
        if (oldAccount != null && oldAccount.getId().equals(newAccount.getId())) {
            BigDecimal diff = newAmount.subtract(oldAmount);
            oldAccount.setBalance(nvl(oldAccount.getBalance()).subtract(diff));
            accountRepository.save(oldAccount);
        } else {
            if (oldAccount != null) {
                oldAccount.setBalance(nvl(oldAccount.getBalance()).add(oldAmount));
                accountRepository.save(oldAccount);
            }
            newAccount.setBalance(nvl(newAccount.getBalance()).subtract(newAmount));
            accountRepository.save(newAccount);
        }
        inv.setName(request.name());
        inv.setInvestmentType(request.investmentType());
        inv.setAmount(newAmount);
        inv.setProfitabilityPercent(nvl(request.profitabilityPercent()));
        inv.setAccount(newAccount);
        inv.setOwner(owner);
        return investmentRepository.save(inv);
    }

    @Transactional
    public void delete(Long id) {
        AppUser owner = currentUserService.requiredUser();
        Investment inv = investmentRepository.findByIdAndOwner(id, owner).orElseThrow(() -> new IllegalArgumentException("Economia não encontrada"));
        if (inv.getAccount() != null) {
            WalletAccount account = inv.getAccount();
            account.setBalance(nvl(account.getBalance()).add(nvl(inv.getAmount())));
            accountRepository.save(account);
        }
        investmentRepository.delete(inv);
    }

    private BigDecimal nvl(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
