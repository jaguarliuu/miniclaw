package com.jaguarliu.ai.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelRepository extends JpaRepository<ChannelEntity, String> {

    Optional<ChannelEntity> findByName(String name);

    List<ChannelEntity> findByTypeOrderByCreatedAtDesc(String type);

    List<ChannelEntity> findAllByOrderByCreatedAtDesc();

    boolean existsByName(String name);

    List<ChannelEntity> findByEnabledTrueAndType(String type);
}
