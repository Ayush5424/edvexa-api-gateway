package com.edvexa.gateway.registry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, String> {

    Optional<ServiceEntity> findByName(String name);

    Optional<ServiceEntity> findByPathPrefix(String pathPrefix);

    boolean existsByName(String name);

    boolean existsByPathPrefix(String pathPrefix);

    List<ServiceEntity> findByEnabledTrue();
}
