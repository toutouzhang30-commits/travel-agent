package com.xingwuyou.travelagent.chat.rag.ingest.web.repository;

import com.xingwuyou.travelagent.chat.rag.ingest.web.model.WebSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class WebSourceRepository {

    private final JdbcTemplate jdbcTemplate;


    public WebSourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    //找数据
    public List<WebSource> findEnabledSources() {
        return jdbcTemplate.query("""
                
                        select
                                              s.id,
                                              s.city,
                                              s.topic,
                                              s.source_name,
                                              s.source_url,
                                              s.confidence_level,
                                              v.content_hash as last_content_hash,
                                              s.crawl_depth,
                                              s.crawl_purpose,
                                              s.parent_source_id,
                                              s.canonical_url
                                          from web_sources s
                                          left join web_source_versions v
                                              on v.source_id = s.id
                                             and v.active = true
                                          where s.enabled = true
                                          order by s.crawl_depth, s.city, s.topic, s.id
                """,
                (rs, rowNum) -> new WebSource(
                        rs.getLong("id"),
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
                )
        );
    }

    //根据id查找父来源
    public WebSource findById(Long id) {
        return jdbcTemplate.queryForObject("""
            select
                s.id, s.city, s.topic, s.source_name, s.source_url, s.confidence_level,
                v.content_hash as last_content_hash,
                s.crawl_depth, s.crawl_purpose, s.parent_source_id, s.canonical_url
            from web_sources s
            left join web_source_versions v
                on v.source_id = s.id
               and v.active = true
            where s.id = ?
            """,
                (rs, rowNum) -> new WebSource(
                        rs.getLong("id"),
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
                ),
                id
        );
    }

    //插入自动提升的source
    public Long insertPromotedSource(
            WebSource parent,
            String sourceUrl,
            String canonicalUrl,
            String topic,
            int crawlDepth,
            String crawlPurpose
    ) {
        return jdbcTemplate.queryForObject("""
            insert into web_sources (
                city, topic, source_name, source_url, confidence_level,
                enabled, crawl_depth, crawl_purpose, parent_source_id, canonical_url
            )
            values (?, ?, ?, ?, ?, true, ?, ?, ?, ?)
              on conflict (canonical_url) do update set
                       topic = excluded.topic,
                       source_name = excluded.source_name,
                       confidence_level = excluded.confidence_level,
                       enabled = true,
                       crawl_depth = least(web_sources.crawl_depth, excluded.crawl_depth),
                       crawl_purpose = excluded.crawl_purpose,
                       updated_at = now()
                   returning id
            """,
                Long.class,
                parent.city(),
                topic,
                parent.sourceName(),
                sourceUrl,
                parent.confidenceLevel(),
                crawlDepth,
                crawlPurpose,
                parent.id(),
                canonicalUrl
        );
    }

    public List<WebSource> findEnabledSourcesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(
                ",",
                java.util.Collections.nCopies(ids.size(), "?")
        );

        return jdbcTemplate.query("""
            select
                s.id,
                s.city,
                s.topic,
                s.source_name,
                s.source_url,
                s.confidence_level,
                v.content_hash as last_content_hash,
                s.crawl_depth,
                s.crawl_purpose,
                s.parent_source_id,
                s.canonical_url
            from web_sources s
            left join web_source_versions v
                on v.source_id = s.id
               and v.active = true
            where s.enabled = true
              and s.id in (%s)
            order by s.crawl_depth, s.city, s.topic, s.id
            """.formatted(placeholders),
                (rs, rowNum) -> new WebSource(
                        rs.getLong("id"),
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
                ),
                ids.toArray()
        );
    }

    public List<WebSource> findEnabledSourcesWithoutActiveVersion(int limit) {
        return jdbcTemplate.query("""
            select
                s.id,
                s.city,
                s.topic,
                s.source_name,
                s.source_url,
                s.confidence_level,
                v.content_hash as last_content_hash,
                s.crawl_depth,
                s.crawl_purpose,
                s.parent_source_id,
                s.canonical_url
            from web_sources s
            left join web_source_versions v
                on v.source_id = s.id
               and v.active = true
            where s.enabled = true
              and s.crawl_depth > 0
              and v.id is null
            order by s.crawl_depth, s.id
            limit ?
            """,
                (rs, rowNum) -> new WebSource(
                        rs.getLong("id"),
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
                ),
                limit
        );
    }

    public void disablePromotedSourcesForRecrawl() {
        jdbcTemplate.update("""
            update web_sources
            set enabled = false,
                updated_at = now()
            where parent_source_id is not null
              and crawl_depth > 0
            """);
    }

    public List<WebSource> findEnabledDiscoverySources(int limit) {
        return jdbcTemplate.query("""
            select
                s.id, s.city, s.topic, s.source_name, s.source_url, s.confidence_level,
                v.content_hash as last_content_hash,
                s.crawl_depth, s.crawl_purpose, s.parent_source_id, s.canonical_url
            from web_sources s
            left join web_source_versions v
                on v.source_id = s.id and v.active = true
            where s.enabled = true
              and (s.crawl_depth = 0 or s.crawl_purpose = 'DISCOVER_LINKS_ONLY')
            order by s.crawl_depth, s.id
            limit ?
            """, rowMapper(), limit);
    }

    private RowMapper<WebSource> rowMapper() {
        return (rs, rowNum) -> new WebSource(
                rs.getLong("id"),
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
    }
    //标记抓取成功
    public void markFetched(Long id) {
        jdbcTemplate.update("""
                update web_sources
                set last_fetched_at = now(), last_error = null, updated_at = now()
                where id = ?
                """, id);
    }

    //标价抓取失败
    public void markFailed(Long id, String error) {
        jdbcTemplate.update("""
                update web_sources
                set last_error = ?, updated_at = now()
                where id = ?
                """, error, id);
    }
}