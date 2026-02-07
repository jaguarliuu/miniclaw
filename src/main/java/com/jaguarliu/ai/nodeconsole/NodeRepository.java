package com.jaguarliu.ai.nodeconsole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NodeRepository extends JpaRepository<NodeEntity, String> {

    Optional<NodeEntity> findByAlias(String alias);

    List<NodeEntity> findByConnectorType(String connectorType);

    List<NodeEntity> findAllByOrderByCreatedAtDesc();

    boolean existsByAlias(String alias);
}
