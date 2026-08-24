package com.finanzero.controller;

import com.finanzero.dto.InvestmentRequest;
import com.finanzero.model.Investment;
import com.finanzero.service.InvestmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
public class InvestmentController {
    private final InvestmentService service;

    @GetMapping
    public List<Investment> list() {
        return service.list();
    }

    @PostMapping
    public Investment create(@RequestBody InvestmentRequest item) {
        return service.create(item);
    }

    @PutMapping("/{id}")
    public Investment update(@PathVariable Long id, @RequestBody InvestmentRequest item) {
        return service.update(id, item);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
