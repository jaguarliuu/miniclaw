package com.jaguarliu.ai.storage.repository;

import com.jaguarliu.ai.storage.entity.RunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RunRepository extends JpaRepository<RunEntity, String> {

    List<RunEntity> findBySessionIdOrderByCreatedAtDesc(String sessionId);

    List<RunEntity> findByStatus(String status);

    void deleteBySessionId(String sessionId);

    /**
     * 查询指定父运行的所有子代理运行
     */
    List<RunEntity> findByParentRunIdOrderByCreatedAtDesc(String parentRunId);

    /**
     * 按 runKind 查询
     */
    List<RunEntity> findByRunKindOrderByCreatedAtDesc(String runKind);

    /**
     * 按 lane 和 status 统计数量（用于并发控制）
     */
    long countByLaneAndStatus(String lane, String status);

    /**
     * 按 lane 和 status 查询（用于队列调度）
     */
    List<RunEntity> findByLaneAndStatusOrderByCreatedAtAsc(String lane, String status);

    /**
     * 查询指定 requesterSessionId 的运行
     */
    List<RunEntity> findByRequesterSessionIdOrderByCreatedAtDesc(String requesterSessionId);
}
