package com.adam_stegienko.stacc_api_rest.repositories;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.adam_stegienko.stacc_api_rest.model.PlannerBook;

@Repository
public interface PlannerBookRepository extends JpaRepository<PlannerBook, UUID> {

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO plannerbooks (id, campaign, action, execution_date) VALUES (:id, :campaign, :action, :executionDate)", nativeQuery = true)
    void insertPlannerBook(@Param("id") UUID id, @Param("campaign") String campaign, @Param("action") Integer action, @Param("executionDate") LocalDateTime executionDate);
}
