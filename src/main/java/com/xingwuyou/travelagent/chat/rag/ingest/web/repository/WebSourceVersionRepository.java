package com.xingwuyou.travelagent.chat.rag.ingest.web.repository;

import com.xingwuyou.travelagent.chat.rag.ingest.web.model.WebActiveSourceVersion;
import com.xingwuyou.travelagent.chat.rag.ingest.web.model.WebSource;
import com.xingwuyou.travelagent.chat.rag.ingest.web.model.WebSourceVersion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class WebSourceVersionRepository {
    private final JdbcTemplate jdbcTemplate;

    public WebSourceVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Long> findActiveVersionId(Long sourceId, String contentHash) {
        return jdbcTemplate.query("""
                select id
                from web_source_versions
                where source_id = ?
                  and content_hash = ?
                  and active = true
                """,
                (rs, rowNum) -> rs.getLong("id"),
                sourceId,
                contentHash
        ).stream().findFirst();
    }

    public long insertNewVersion(WebSource source, String title, String cleanedContent,
                                 String contentHash, String primaryTopic, String pageType) {
        jdbcTemplate.update("""
            update web_source_versions
            set active = false
            where source_id = ?
            """, source.id());

        return jdbcTemplate.queryForObject("""
            insert into web_source_versions (
                source_id, content_hash, title, cleaned_content,
                primary_topic, page_type, fetched_at, active
            )
            values (?, ?, ?, ?, ?, ?, now(), true)
            returning id
            """,
                Long.class,
                source.id(), contentHash, title, cleanedContent, primaryTopic, pageType
        );
    }

    public Optional<WebSourceVersion> findActiveVersion(Long sourceId) {
        return jdbcTemplate.query("""
            select id, source_id, title, cleaned_content, content_hash, fetched_at,
                   primary_topic, page_type
            from web_source_versions
            where source_id = ?
              and active = true
            """,
                (rs, rowNum) -> new WebSourceVersion(
                        rs.getLong("id"),
                        rs.getLong("source_id"),
                        rs.getString("title"),
                        rs.getString("cleaned_content"),
                        rs.getString("content_hash"),
                        rs.getTimestamp("fetched_at").toInstant().toString(),
                        rs.getString("primary_topic"),
                        rs.getString("page_type")
                ),
                sourceId
        ).stream().findFirst();
    }

    public List<WebActiveSourceVersion> findAllActiveEnabledVersions() {
        return jdbcTemplate.query("""
            select
                s.id as source_id,
                s.city,
                s.topic,
                s.source_name,
                s.source_url,
                s.confidence_level,
                v.content_hash as last_content_hash,
                s.crawl_depth,
                s.crawl_purpose,
                s.parent_source_id,
                s.canonical_url,
                v.id as version_id,
                v.title,
                v.cleaned_content,
                v.content_hash,
                v.fetched_at,
                v.primary_topic,
                v.page_type
            from web_sources s
            join web_source_versions v
              on v.source_id = s.id
             and v.active = true
            where s.enabled = true
            order by s.city, s.topic, s.id
            """,
                (rs, rowNum) -> {
                    WebSource source = new WebSource(
                            rs.getLong("source_id"),
                            rs.getString("city"),
                            rs.getString("topic"),
                            rs.getString("source_name"),
                            rs.getString("source_url"),
                            rs.getString("confidence_level"),
                            rs.getString("last_content_hash"),
                            rs.getInt("crawl_depth"),
                            rs.getString("crawl_purpose"),
                            rs.getObject("parent_source_id", Long.class),
                            rs.getString("canonical_url")
                    );

                    WebSourceVersion version = new WebSourceVersion(
                            rs.getLong("version_id"),
                            rs.getLong("source_id"),
                            rs.getString("title"),
                            rs.getString("cleaned_content"),
                            rs.getString("content_hash"),
                            rs.getTimestamp("fetched_at").toInstant().toString(),
                            rs.getString("primary_topic"),
                            rs.getString("page_type")
                    );

                    return new WebActiveSourceVersion(source, version);
                }
        );
    }
}
