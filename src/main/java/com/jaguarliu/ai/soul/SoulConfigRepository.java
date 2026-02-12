package com.jaguarliu.ai.soul;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SoulConfigRepository extends JpaRepository<SoulConfigEntity, Long> {

    /**
     * 获取已启用的配置
     */
    Optional<SoulConfigEntity> findFirstByEnabledTrueOrderByUpdatedAtDesc();
}
