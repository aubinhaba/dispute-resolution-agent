package com.bino.dra.adapter.out.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("dispute_case")
record DisputeCaseClaimRow(@Id String disputeId, Instant submittedAt) {
}
