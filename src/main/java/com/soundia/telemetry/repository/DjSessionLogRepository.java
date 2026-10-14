package com.soundia.telemetry.repository;

import com.soundia.telemetry.model.DjSessionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DjSessionLogRepository extends JpaRepository<DjSessionLog, Long>{
}