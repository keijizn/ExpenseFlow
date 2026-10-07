package com.finanzero.controller;

import com.finanzero.service.ReceiptStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptController {
    private final ReceiptStorageService storageService;
    private final com.finanzero.service.CurrentUserService currentUser;
    private final com.finanzero.repository.FinanceTransactionRepository transactions;

    @GetMapping("/{fileName}")
    public ResponseEntity<java.util.Map<String, String>> get(@PathVariable String fileName) {
        transactions.findByReceiptFileNameAndOwner(fileName, currentUser.requiredUser())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Comprovante não encontrado."));
        String temporaryUrl = storageService.temporaryUrl(fileName);
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(java.util.Map.of("url", temporaryUrl));
    }
}
