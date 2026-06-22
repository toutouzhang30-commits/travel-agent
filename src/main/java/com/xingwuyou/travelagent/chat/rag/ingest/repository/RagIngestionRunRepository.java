package com.xingwuyou.travelagent.chat.rag.ingest.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

//数据库进行操作，进行入库批次判断
@Repository
public class RagIngestionRunRepository {
    private final JdbcTemplate jdbcTemplate;

    public RagIngestionRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void markStarted(String runId, String namespace, String contentHash, String pipelineHash) {
        jdbcTemplate.update("""
                insert into rag_ingestion_runs (
                    run_id, namespace, status, content_hash, pipeline_hash, started_at
                )
                values (?, ?, 'IN_PROGRESS', ?, ?, now())
                """, runId, namespace, contentHash, pipelineHash);
    }

    public void markCompleted(String runId, int documentCount, int chunkCount) {
        jdbcTemplate.update("""
                update rag_ingestion_runs
                set status = 'COMPLETED',
                    document_count = ?,
                    chunk_count = ?,
                    completed_at = now(),
                    error_message = null
                where run_id = ?
                """, documentCount, chunkCount, runId);
    }

    public void markFailed(String runId, String errorMessage) {
        jdbcTemplate.update("""
                update rag_ingestion_runs
                set status = 'FAILED',
                    completed_at = now(),
                    error_message = ?
                where run_id = ?
                """, errorMessage, runId);
    }
}
