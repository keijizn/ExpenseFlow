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

    @GetMapping("/{fileName}")
    public ResponseEntity<Void> get(@PathVariable String fileName) {
        String temporaryUrl = storageService.temporaryUrl(fileName);
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, temporaryUrl)
                .location(URI.create(temporaryUrl))
                .build();
    }
}
