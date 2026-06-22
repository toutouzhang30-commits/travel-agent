package com.xingwuyou.travelagent.chat.rag.ingest.web.service;

import com.xingwuyou.travelagent.chat.rag.ingest.dto.RagRawDocument;
import com.xingwuyou.travelagent.chat.rag.ingest.web.model.*;
import com.xingwuyou.travelagent.chat.rag.ingest.web.repository.WebSourceLinkRepository;
import com.xingwuyou.travelagent.chat.rag.ingest.web.repository.WebSourceRepository;
import com.xingwuyou.travelagent.chat.rag.ingest.web.repository.WebSourceVersionRepository;

import org.springframework.stereotype.Service;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class WebRagDocumentLoader {
    //数据库访问对象
    private final WebSourceRepository repository;
    //HTTP请求对象
    private final WebSourceVersionRepository versionRepository;
    private final WebPageExtractor extractor;
    private final WebSourceLinkRepository linkRepository;
    private final WebKnowledgeClassifier classifier;
    private final WebSourcePromotionService promotionService;
    private final WebPageFetcher pageFetcher;

    //构造器注入
    public WebRagDocumentLoader(WebSourceRepository repository,
                                WebSourceVersionRepository versionRepository,
                                WebPageExtractor extractor,
                                WebSourceLinkRepository linkRepository,
                                WebKnowledgeClassifier classifier,
                                WebSourcePromotionService promotionService,
                                WebPageFetcher pageFetcher) {
        this.repository = repository;
        this.versionRepository = versionRepository;
        this.linkRepository=linkRepository;
        this.extractor=extractor;
        this.classifier=classifier;
        this.promotionService = promotionService;
        this.pageFetcher=pageFetcher;
    }

    //加载所有启用网页
    //只抓取成功之后的新页面
    public List<RagRawDocument> loadEnabledWebSources() {
        repository.findEnabledDiscoverySources(5)
                .forEach(this::fetchOneForVersionAndDiscovery);

        List<Long> promotedSourceIds = promotionService.promoteDiscoveredLinks();

        java.util.Set<Long> sourceIdsToFetch = new java.util.LinkedHashSet<>(promotedSourceIds);
        repository.findEnabledSourcesWithoutActiveVersion(5)
                .forEach(source -> sourceIdsToFetch.add(source.id()));

        repository.findEnabledSourcesByIds(sourceIdsToFetch.stream().toList())
                .forEach(this::fetchOneForVersionAndDiscovery);

        return versionRepository.findAllActiveEnabledVersions().stream()
                .map(active -> toRawDocument(active.source(), active.version()))
                .toList();
    }

    //不让入库任务崩掉，记录失败原因，然后跳过
    private void fetchOneForVersionAndDiscovery(WebSource source) {
        try {
            WebFetchedPage fetchedPage = pageFetcher.fetch(source.sourceUrl());
            WebPageExtraction page = extractor.extract(fetchedPage, source.depth());

            linkRepository.upsertLinks(source.id(), page.links());

            if ("DISCOVER_LINKS_ONLY".equals(source.crawlPurpose())) {
                repository.markFetched(source.id());
                return;
            }

            WebKnowledgeMetadata knowledge = classifier.classify(source, page);

            if (knowledge.pageType() != WebPageType.ARTICLE_DETAIL
                    || !knowledge.suitableForRag()
                    || !knowledge.containsCoreInfo()) {
                repository.markFailed(source.id(), knowledge.rejectReason());
                return;
            }

            if (page.mainText() == null || page.mainText().length() < 300) {
                repository.markFailed(source.id(), "Article content is too short");
                return;
            }

            String contentHash = sha256(page.mainText());

            if (contentHash.equals(source.lastContentHash())) {
                repository.markFetched(source.id());
                return;
            }

            versionRepository.insertNewVersion(
                    source,
                    page.title(),
                    page.mainText(),
                    contentHash,
                    knowledge.primaryTopic(),
                    knowledge.pageType().name()
            );

            repository.markFetched(source.id());
        } catch (Exception ex) {
            repository.markFailed(source.id(), ex.getMessage());
        }
    }



    private RagRawDocument toRawDocument(WebSource source, WebSourceVersion version) {
        String category = version.primaryTopic() == null || version.primaryTopic().isBlank()
                ? source.topic()
                : version.primaryTopic();

        String pageType = version.pageType() == null || version.pageType().isBlank()
                ? "ARTICLE_DETAIL"
                : version.pageType();

        String content = """
    ---
    city: "%s"
    category: "%s"
    sourceTopic: "%s"
    source: "%s"
    sourceUrl: "%s"
    sourceId: "%s"
    sourceVersionId: "%s"
    contentHash: "%s"
    fetchedAt: "%s"
    confidenceLevel: "%s"
    sourceType: "WEB"
    pageType: "%s"
    ---
    %s
    """.formatted(
                source.city(),
                category,
                source.topic(),
                source.sourceName(),
                source.sourceUrl(),
                source.id(),
                version.id(),
                version.contentHash(),
                version.fetchedAt(),
                source.confidenceLevel(),
                pageType,
                version.cleanedContent()
        );

        return new RagRawDocument("web:" + source.id() + ":" + version.id(), content);
    }
    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash web content", e);
        }
    }
}