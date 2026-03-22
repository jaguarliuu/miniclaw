package com.miniclaw.gateway.session.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionEntityRepository extends JpaRepository<SessionEntity, String> {
}
