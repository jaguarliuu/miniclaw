package com.jaguarliu.ai.storage.repository;

import com.jaguarliu.ai.storage.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, String> {

    List<MessageEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<MessageEntity> findByRunIdOrderByCreatedAtAsc(String runId);

    void deleteBySessionId(String sessionId);
}
