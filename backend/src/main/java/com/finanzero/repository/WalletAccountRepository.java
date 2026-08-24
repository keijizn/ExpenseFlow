package com.finanzero.repository;

import com.finanzero.model.AppUser;
import com.finanzero.model.WalletAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WalletAccountRepository extends JpaRepository<WalletAccount, Long> {
    List<WalletAccount> findByOwnerOrderByName(AppUser owner);
    Optional<WalletAccount> findByIdAndOwner(Long id, AppUser owner);
}
