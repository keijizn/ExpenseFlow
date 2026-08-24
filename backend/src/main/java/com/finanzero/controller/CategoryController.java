package com.finanzero.controller;

import com.finanzero.model.AppUser;
import com.finanzero.model.Category;
import com.finanzero.model.TransactionType;
import com.finanzero.repository.CategoryRepository;
import com.finanzero.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryRepository repository;
    private final CurrentUserService currentUserService;

    @GetMapping
    public List<Category> list(@RequestParam(required = false) TransactionType type) {
        AppUser owner = currentUserService.requiredUser();
        return type == null ? repository.findByOwnerOrderByName(owner) : repository.findByOwnerAndTypeOrderByName(owner, type);
    }

    @PostMapping
    public Category create(@RequestBody @Valid Category category) {
        category.setOwner(currentUserService.requiredUser());
        normalize(category);
        return repository.save(category);
    }

    @PutMapping("/{id}")
    public Category update(@PathVariable Long id, @RequestBody @Valid Category category) {
        AppUser owner = currentUserService.requiredUser();
        repository.findByIdAndOwner(id, owner).orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada"));
        category.setId(id);
        category.setOwner(owner);
        normalize(category);
        return repository.save(category);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        AppUser owner = currentUserService.requiredUser();
        Category category = repository.findByIdAndOwner(id, owner).orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada"));
        repository.delete(category);
        return ResponseEntity.noContent().build();
    }

    private void normalize(Category category) {
        if (category.getMonthlyLimit() == null) category.setMonthlyLimit(BigDecimal.ZERO);
        if (category.getType() == null) category.setType(TransactionType.VARIABLE_EXPENSE);
    }
}
