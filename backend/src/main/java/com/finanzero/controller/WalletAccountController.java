package com.finanzero.controller;

import com.finanzero.model.AppUser;
import com.finanzero.model.WalletAccount;
import com.finanzero.repository.WalletAccountRepository;
import com.finanzero.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class WalletAccountController {
    private final WalletAccountRepository repository;
    private final CurrentUserService currentUserService;

    @GetMapping
    public List<WalletAccount> list() { return repository.findByOwnerOrderByName(currentUserService.requiredUser()); }

    @PostMapping
    public WalletAccount create(@RequestBody @Valid WalletAccount item) {
        item.setOwner(currentUserService.requiredUser());
        normalize(item);
        return repository.save(item);
    }

    @PutMapping("/{id}")
    public WalletAccount update(@PathVariable Long id, @RequestBody @Valid WalletAccount item) {
        AppUser owner = currentUserService.requiredUser();
        repository.findByIdAndOwner(id, owner).orElseThrow(() -> new IllegalArgumentException("Conta não encontrada"));
        item.setId(id);
        item.setOwner(owner);
        normalize(item);
        return repository.save(item);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        AppUser owner = currentUserService.requiredUser();
        WalletAccount account = repository.findByIdAndOwner(id, owner).orElseThrow(() -> new IllegalArgumentException("Conta não encontrada"));
        repository.delete(account);
        return ResponseEntity.noContent().build();
    }

    private void normalize(WalletAccount item) {
        if (item.getBalance() == null) item.setBalance(BigDecimal.ZERO);
        if (item.getCardLimit() == null) item.setCardLimit(BigDecimal.ZERO);
    }
}
