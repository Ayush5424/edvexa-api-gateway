package com.edvexa.gateway.security.apikey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, String> {

    Optional<ApiKeyEntity> findByKeyHash(String keyHash);

    List<ApiKeyEntity> findByKeyPrefix(String keyPrefix);

    List<ApiKeyEntity> findAllByOrderByCreatedAtDesc();
}
