package com.finanzero.controller;

import com.finanzero.dto.TransactionRequest;
import com.finanzero.model.AppUser;
import com.finanzero.model.FinanceTransaction;
import com.finanzero.model.TransactionType;
import com.finanzero.repository.FinanceTransactionRepository;
import com.finanzero.service.CurrentUserService;
import com.finanzero.service.TransactionService;
import com.finanzero.service.ReceiptStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService service;
    private final FinanceTransactionRepository repository;
    private final CurrentUserService currentUserService;
    private final ReceiptStorageService receiptStorageService;

    @GetMapping
    public List<FinanceTransaction> list(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) { return service.list(type, start, end); }

    @GetMapping("/{id}")
    public ResponseEntity<FinanceTransaction> get(@PathVariable Long id) {
        AppUser owner = currentUserService.requiredUser();
        return repository.findByIdAndOwner(id, owner).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping public FinanceTransaction create(@RequestBody @Valid TransactionRequest request) { return service.create(request); }
    @PutMapping("/{id}") public FinanceTransaction update(@PathVariable Long id, @RequestBody @Valid TransactionRequest request) { return service.update(id, request); }

    @PostMapping("/{id}/receipt")
    public FinanceTransaction uploadReceipt(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        AppUser owner = currentUserService.requiredUser();
        FinanceTransaction transaction = repository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new IllegalArgumentException("Transação não encontrada"));
        ReceiptStorageService.StoredReceipt receipt = receiptStorageService.store(file);
        receiptStorageService.deleteQuietly(transaction.getReceiptFileName());
        transaction.setReceiptFileName(receipt.fileName());
        transaction.setReceiptOriginalName(receipt.originalName());
        transaction.setReceiptContentType(receipt.contentType());
        return repository.save(transaction);
    }

    @DeleteMapping("/{id}/receipt")
    public FinanceTransaction deleteReceipt(@PathVariable Long id) {
        AppUser owner = currentUserService.requiredUser();
        FinanceTransaction transaction = repository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new IllegalArgumentException("Transação não encontrada"));
        receiptStorageService.deleteQuietly(transaction.getReceiptFileName());
        transaction.setReceiptFileName(null);
        transaction.setReceiptOriginalName(null);
        transaction.setReceiptContentType(null);
        return repository.save(transaction);
    }

    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id) { service.delete(id); return ResponseEntity.noContent().build(); }
}
