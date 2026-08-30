package com.bino.dra.adapter.out.persistence;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

interface DisputeCaseClaimCrud extends Repository<DisputeCaseClaimRow, String> {

    @Modifying
    @Query("""
            INSERT INTO dispute_case (dispute_id, submitted_at)
            VALUES (:disputeId, :submittedAt)
            ON CONFLICT (dispute_id) DO NOTHING
            """)
    boolean insertIfAbsent(@Param("disputeId") String disputeId, @Param("submittedAt") Instant submittedAt);
}
