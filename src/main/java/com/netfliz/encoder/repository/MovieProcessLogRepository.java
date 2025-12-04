package com.netfliz.encoder.repository;

import com.netfliz.encoder.entity.MovieProcessLogsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieProcessLogRepository extends JpaRepository<MovieProcessLogsEntity, Long> {
}
