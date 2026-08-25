package com.paypulse.repository;

import com.paypulse.model.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {

    @Modifying              //This is not a SELECT. This query modifies the database.
    @Query("update RefreshTokenEntity r set r.revoked = true where r.token = :token")
    void revokeByToken(@Param("token") String token);

    @Modifying
    @Query("update RefreshTokenEntity r set r.revoked = true where r.userId = :userId and r.revoked = false")
    void revokeAllByUserId(@Param("userId") String userId);
}
//JPQL works with entity class that is why we are writing update RefreshTokenEntity , not refresh_tokens