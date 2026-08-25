package com.paypulse.repository;

import com.paypulse.model.WalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<WalletEntity, String> {
    Optional<WalletEntity> findByUserId(String userId);
}
// We are using userId instead of WalletId , because user is authenticated and repository already provides: findByUserId(userId)