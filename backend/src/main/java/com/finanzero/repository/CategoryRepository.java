package com.finanzero.repository;

import com.finanzero.model.AppUser;
import com.finanzero.model.Category;
import com.finanzero.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByOwnerOrderByName(AppUser owner);
    List<Category> findByOwnerAndTypeOrderByName(AppUser owner, TransactionType type);
    Optional<Category> findByIdAndOwner(Long id, AppUser owner);
}
