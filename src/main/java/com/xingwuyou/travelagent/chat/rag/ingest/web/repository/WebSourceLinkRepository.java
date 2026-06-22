package com.xingwuyou.travelagent.chat.rag.ingest.web.repository;

import com.xingwuyou.travelagent.chat.rag.ingest.web.model.WebDiscoveredLink;
import com.xingwuyou.travelagent.chat.rag.ingest.web.model.WebSource;
import com.xingwuyou.travelagent.chat.rag.ingest.web.model.WebSourceLinkCandidate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

//新增连接
@Repository
public class WebSourceLinkRepository {
    private final JdbcTemplate jdbcTemplate;

    public WebSourceLinkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertLinks(Long parentSourceId, List<WebDiscoveredLink> links) {
        for (WebDiscoveredLink link : links) {
            jdbcTemplate.update("""
        insert into web_source_links (
            parent_source_id, discovered_url, link_text, image_alt, link_type, discovered_depth
        )
        values (?, ?, ?, ?, ?, ?)
        on conflict (parent_source_id, discovered_url) do update set
            link_text = excluded.link_text,
            image_alt = excluded.image_alt,
            link_type = excluded.link_type,
            discovered_depth = excluded.discovered_depth
        """,
                    parentSourceId,
                    link.url(),
                    link.linkText(),
                    link.imageAlt(),
                    link.linkType(),
                    link.discoveredDepth()
            );
        }
    }
    //查询可提升链接
    public List<WebSourceLinkCandidate> findPromotableLinks(int limit) {
        return jdbcTemplate.query("""
            select id, parent_source_id, discovered_url, link_text, image_alt, link_type, discovered_depth
            from web_source_links
            where status = 'DISCOVERED'
            and allowed = true
            and discovered_depth <= 2
              and (next_retry_at is null or next_retry_at <= now())
            order by id
            limit ?
            """,
                (rs, rowNum) -> new WebSourceLinkCandidate(
                        rs.getLong("id"),
                        rs.getLong("parent_source_id"),
                        rs.getString("discovered_url"),
                        rs.getString("link_text"),
                        rs.getString("image_alt"),
                        rs.getString("link_type"),
                        rs.getInt("discovered_depth")
                ),
                limit
        );
    }
    //新增状态更新方法
    public void markPromoted(Long linkId, Long sourceId, String pageKind, String topic, double confidence) {
        jdbcTemplate.update("""
            update web_source_links
            set status = 'PROMOTED',
                promoted_source_id = ?,
                page_kind = ?,
                preliminary_topic = ?,
                classifier_confidence = ?,
                updated_at = now()
            where id = ?
            """, sourceId, pageKind, topic, confidence, linkId);
    }

    public void markRejected(Long linkId, String reason) {
        jdbcTemplate.update("""
            update web_source_links
            set status = 'REJECTED',
                reject_reason = ?,
                updated_at = now()
            where id = ?
            """, reason, linkId);
    }

    public void markFailed(Long linkId, String error) {
        jdbcTemplate.update("""
            update web_source_links
            set status = case when retry_count + 1 >= 3 then 'FAILED' else status end,
                retry_count = retry_count + 1,
                last_attempt_at = now(),
                next_retry_at = now() + interval '10 minutes',
                last_error = ?,
                updated_at = now()
            where id = ?
            """, error, linkId);
    }

    public void clearAllLinksForFullRecrawl() {
        jdbcTemplate.update("delete from web_source_links");
    }
}