package com.finanzero.controller;

import com.finanzero.dto.DebtPaymentRequest;
import com.finanzero.model.Debt;
import com.finanzero.service.DebtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/debts")
@RequiredArgsConstructor
public class DebtController {
    private final DebtService service;

    @GetMapping
    public List<Debt> list() {
        return service.list();
    }

    @PostMapping
    public Debt create(@RequestBody @Valid Debt item) {
        return service.create(item);
    }

    @PutMapping("/{id}")
    public Debt update(@PathVariable Long id, @RequestBody @Valid Debt item) {
        return service.update(id, item);
    }

    @PostMapping("/{id}/pay")
    public Debt pay(@PathVariable Long id, @RequestBody DebtPaymentRequest request) {
        return service.pay(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
