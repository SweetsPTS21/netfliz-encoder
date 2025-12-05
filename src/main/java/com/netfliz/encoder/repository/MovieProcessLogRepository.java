package com.netfliz.encoder.repository;

import com.netfliz.encoder.entity.MovieProcessLogsEntity;
import com.netfliz.encoder.entity.enums.ProcessLogsObjectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface MovieProcessLogRepository extends JpaRepository<MovieProcessLogsEntity, Long> {

    @Query("SELECT lp FROM MovieProcessLogsEntity lp WHERE lp.objectId = :objectId AND lp.objectType = :objectType ORDER BY lp.id DESC LIMIT 1")
    Optional<MovieProcessLogsEntity> getFirstStreamInfo(Long objectId, ProcessLogsObjectType objectType);
}
