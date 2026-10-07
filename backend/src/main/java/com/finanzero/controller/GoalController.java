package com.finanzero.controller;

import com.finanzero.model.AppUser;
import com.finanzero.model.Goal;
import com.finanzero.repository.GoalRepository;
import com.finanzero.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@org.springframework.transaction.annotation.Transactional
@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {
    private final GoalRepository repository;
    private final CurrentUserService currentUserService;

    @GetMapping public List<Goal> list(){ return repository.findByOwnerOrderByIdDesc(currentUserService.requiredUser()); }

    @PostMapping public Goal create(@RequestBody @Valid Goal item){
        com.finanzero.service.FinancialValidation.newEntity(item.getId());
        item.setOwner(currentUserService.lockUser());
        normalize(item);
        return repository.save(item);
    }

    @PutMapping("/{id}") public Goal update(@PathVariable Long id, @RequestBody @Valid Goal item){
        AppUser owner = currentUserService.lockUser();
        repository.findByIdAndOwner(id, owner).orElseThrow(() -> new IllegalArgumentException("Meta não encontrada"));
        item.setId(id);
        item.setOwner(owner);
        normalize(item);
        return repository.save(item);
    }

    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id){
        AppUser owner = currentUserService.lockUser();
        Goal item = repository.findByIdAndOwner(id, owner).orElseThrow(() -> new IllegalArgumentException("Meta não encontrada"));
        repository.delete(item);
        return ResponseEntity.noContent().build();
    }

    private void normalize(Goal item) {
        if (item.getTargetAmount() == null) item.setTargetAmount(BigDecimal.ZERO);
        if (item.getCurrentAmount() == null) item.setCurrentAmount(BigDecimal.ZERO);
        com.finanzero.service.FinancialValidation.money(item.getTargetAmount(), "Valor da meta", false);
        com.finanzero.service.FinancialValidation.money(item.getCurrentAmount(), "Valor atual", true);
        if (item.getCurrentAmount().compareTo(item.getTargetAmount()) > 0) throw new IllegalArgumentException("O valor atual não pode ultrapassar a meta.");
    }
}
