package com.xingwuyou.travelagent.chat.rag.ingest;

import com.xingwuyou.travelagent.chat.rag.ingest.dto.RagIngestionManifest;
import com.xingwuyou.travelagent.chat.rag.ingest.dto.RagIngestionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
//记录入库状态，防止并发冲突，并清理向量库中的旧数据
public class RagIngestionManifestRepository {
    private final JdbcTemplate jdbcTemplate;
    private final String vectorTable;

    public RagIngestionManifestRepository(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.ai.vectorstore.pgvector.schema-name:public}") String schemaName,
            @Value("${spring.ai.vectorstore.pgvector.table-name:travel_knowledge}") String tableName
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.vectorTable = quoteIdentifier(schemaName) + "." + quoteIdentifier(tableName);
    }

    //如果数据库没有rag_ingestion_manifest这张表就创建它
    public void ensureManifestTable() {
        jdbcTemplate.execute("""
                create table if not exists rag_ingestion_manifest (
                    namespace varchar(128) primary key,
                    content_hash varchar(128) not null,
                    pipeline_hash varchar(128) not null,
                    active_run_id varchar(128),
                    pending_run_id varchar(128),
                    status varchar(32) not null,
                    document_count integer not null default 0,
                    chunk_count integer not null default 0,
                    started_at timestamptz not null default now(),
                    updated_at timestamptz not null default now(),
                    error_message text
                )
                """);
    }

    //利用PostgreSQL的咨询锁
    public boolean tryAcquireLock(String namespace) {
        Boolean locked = jdbcTemplate.queryForObject(
                "select pg_try_advisory_lock(hashtext(?))",
                Boolean.class,
                namespace
        );
        return Boolean.TRUE.equals(locked);
    }

    public void releaseLock(String namespace) {
        jdbcTemplate.queryForObject(
                "select pg_advisory_unlock(hashtext(?))",
                Boolean.class,
                namespace
        );
    }

    //根据命名空间查出当前的清单记录
    public Optional<RagIngestionManifest> find(String namespace) {
        return jdbcTemplate.query("""
                        select namespace, content_hash, pipeline_hash, active_run_id, pending_run_id,
                               status, document_count, chunk_count, started_at, updated_at, error_message
                        from rag_ingestion_manifest
                        where namespace = ?
                        """,
                (rs, rowNum) -> mapManifest(rs),
                namespace
        ).stream().findFirst();
    }

    //准备开始入库
    public void markInProgress(String namespace, String contentHash, String pipelineHash, String pendingRunId) {
        jdbcTemplate.update("""
                insert into rag_ingestion_manifest (
                    namespace, content_hash, pipeline_hash, pending_run_id,
                    status, document_count, chunk_count, started_at, updated_at, error_message
                )
                values (?, ?, ?, ?, 'IN_PROGRESS', 0, 0, now(), now(), null)
                on conflict (namespace) do update set
                    content_hash = excluded.content_hash,
                    pipeline_hash = excluded.pipeline_hash,
                    pending_run_id = excluded.pending_run_id,
                    status = 'IN_PROGRESS',
                    started_at = now(),
                    updated_at = now(),
                    error_message = null
                """, namespace, contentHash, pipelineHash, pendingRunId);
    }

    //入库成功
    public void markCompleted(String namespace, int documentCount, int chunkCount) {
        jdbcTemplate.update("""
                update rag_ingestion_manifest
                set active_run_id = pending_run_id,
                    pending_run_id = null,
                    status = 'COMPLETED',
                    document_count = ?,
                    chunk_count = ?,
                    updated_at = now(),
                    error_message = null
                where namespace = ?
                """, documentCount, chunkCount, namespace);
    }

    //入库出错
    public void markFailed(String namespace, String errorMessage) {
        jdbcTemplate.update("""
                update rag_ingestion_manifest
                set status = 'FAILED',
                    updated_at = now(),
                    error_message = ?
                where namespace = ?
                """, errorMessage, namespace);
    }

    //删除某一个批次的数据
    public void deleteRun(String namespace, String runId) {
        if (runId == null || runId.isBlank()) {
            return;
        }

        jdbcTemplate.update(
                "delete from " + vectorTable
                        + " where metadata ->> 'ingestionNamespace' = ?"
                        + " and metadata ->> 'ingestionRunId' = ?",
                namespace,
                runId
        );
    }

    //清理垃圾，activeRunId
    public void deleteInactiveRuns(String namespace, String activeRunId) {
        if (activeRunId == null || activeRunId.isBlank()) {
            return;
        }

        jdbcTemplate.update(
                "delete from " + vectorTable
                        + " where metadata ->> 'ingestionNamespace' = ?"
                        + " and metadata ->> 'ingestionRunId' <> ?",
                namespace,
                activeRunId
        );
    }

    public Optional<String> findActiveRunId(String namespace) {
        return jdbcTemplate.query("""
                        select active_run_id
                        from rag_ingestion_manifest
                        where namespace = ?
                          and status = 'COMPLETED'
                          and active_run_id is not null
                        """,
                (rs, rowNum) -> rs.getString("active_run_id"),
                namespace
        ).stream().findFirst();
    }

    public int countRunDocuments(String namespace, String runId) {
        if (runId == null || runId.isBlank()) {
            return 0;
        }

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + vectorTable
                        + " where metadata ->> 'ingestionNamespace' = ?"
                        + " and metadata ->> 'ingestionRunId' = ?",
                Integer.class,
                namespace,
                runId
        );

        return count == null ? 0 : count;
    }


    private RagIngestionManifest mapManifest(ResultSet rs) throws java.sql.SQLException {
        return new RagIngestionManifest(
                rs.getString("namespace"),
                rs.getString("content_hash"),
                rs.getString("pipeline_hash"),
                rs.getString("active_run_id"),
                rs.getString("pending_run_id"),
                RagIngestionStatus.valueOf(rs.getString("status")),
                rs.getInt("document_count"),
                rs.getInt("chunk_count"),
                toOffsetDateTime(rs.getTimestamp("started_at")),
                toOffsetDateTime(rs.getTimestamp("updated_at")),
                rs.getString("error_message")
        );
    }

    private OffsetDateTime toOffsetDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().atOffset(ZoneOffset.UTC);
    }

    private String quoteIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        return "\"" + identifier + "\"";
    }

}
