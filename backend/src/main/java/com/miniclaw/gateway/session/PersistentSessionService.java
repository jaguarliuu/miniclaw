package com.miniclaw.gateway.session;

import com.miniclaw.gateway.session.persistence.SessionEntity;
import com.miniclaw.gateway.session.persistence.SessionEntityRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class PersistentSessionService {

    private final InMemorySessionRegistry runtimeRegistry;
    private final SessionEntityRepository repository;

    public PersistentSessionService(InMemorySessionRegistry runtimeRegistry, SessionEntityRepository repository) {
        this.runtimeRegistry = runtimeRegistry;
        this.repository = repository;
    }

    public GatewaySession create(String connectionId) {
        GatewaySession session = runtimeRegistry.create(connectionId);
        repository.save(toEntity(session));
        return session;
    }

    public Optional<GatewaySession> find(String sessionId) {
        return runtimeRegistry.find(sessionId)
                .or(() -> repository.findById(sessionId).map(this::toDomain));
    }

    public GatewaySession save(GatewaySession session) {
        repository.save(toEntity(session));
        return session;
    }

    private SessionEntity toEntity(GatewaySession session) {
        Instant closedAt = session.getState() == SessionState.CLOSED ? Instant.now() : null;
        return SessionEntity.builder()
                .id(session.getSessionId())
                .ownerId(null)
                .title(null)
                .status(session.getState())
                .createdAt(session.getCreatedAt())
                .updatedAt(Instant.now())
                .closedAt(closedAt)
                .build();
    }

    private GatewaySession toDomain(SessionEntity entity) {
        return new GatewaySession(
                entity.getId(),
                null,
                entity.getCreatedAt(),
                entity.getStatus()
        );
    }
}
