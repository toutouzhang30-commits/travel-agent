package com.xingwuyou.travelagent.chat.rag.ingest.web.service;

import com.xingwuyou.travelagent.chat.rag.ingest.web.model.*;
import com.xingwuyou.travelagent.chat.rag.ingest.web.repository.WebSourceLinkRepository;
import com.xingwuyou.travelagent.chat.rag.ingest.web.repository.WebSourceRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class WebSourcePromotionService {
    private static final int MAX_PROMOTIONS_PER_RUN = 5;
    private static final int MAX_DEPTH = 2;

    private final WebSourceLinkRepository linkRepository;
    private final WebSourceRepository sourceRepository;
    private final WebUrlNormalizer urlNormalizer;
    private final WebLinkPromotionClassifier classifier;
    private final WebPageFetcher pageFetcher;
    private final WebPageExtractor extractor;

    public WebSourcePromotionService(
            WebSourceLinkRepository linkRepository,
            WebSourceRepository sourceRepository,
            WebUrlNormalizer urlNormalizer,
            WebLinkPromotionClassifier classifier,
            WebPageFetcher pageFetcher,
            WebPageExtractor extractor
    ) {
        this.linkRepository = linkRepository;
        this.sourceRepository = sourceRepository;
        this.urlNormalizer = urlNormalizer;
        this.classifier = classifier;
        this.pageFetcher=pageFetcher;
        this.extractor=extractor;
    }

    public List<Long> promoteDiscoveredLinks(){
        List<Long> promotedSourceIds = new ArrayList<>();
        //循环处理每个候选链接
        for (WebSourceLinkCandidate candidate : linkRepository.findPromotableLinks(MAX_PROMOTIONS_PER_RUN)) {
            try {
                WebSource parent = sourceRepository.findById(candidate.parentSourceId());
                String canonicalUrl = urlNormalizer.canonicalize(candidate.discoveredUrl());

                if (canonicalUrl.isBlank()) {
                    linkRepository.markRejected(candidate.id(), "Invalid URL");
                    continue;
                }

                if (!urlNormalizer.sameHost(parent.sourceUrl(), canonicalUrl)) {
                    linkRepository.markRejected(candidate.id(), "External host");
                    continue;
                }

                if (!urlNormalizer.isHtmlCandidate(canonicalUrl)) {
                    linkRepository.markRejected(candidate.id(), "Non HTML resource");
                    continue;
                }

                if (candidate.discoveredDepth() > MAX_DEPTH) {
                    linkRepository.markRejected(candidate.id(), "Depth limit exceeded");
                    continue;
                }

                //数据库记录转换为对象，方便后续抓取和分类器处理
                WebDiscoveredLink link = new WebDiscoveredLink(
                        canonicalUrl,
                        candidate.linkText(),
                        candidate.imageAlt(),
                        candidate.linkType(),
                        candidate.discoveredDepth()
                );

                WebPagePreview preview;

                try {
                    //抓取目标页面概览
                    preview = previewTargetPage(link);
                } catch (Exception ex) {
                    linkRepository.markFailed(candidate.id(), "Preview fetch failed: " + ex.getMessage());
                    continue;
                }

                //先用preview规则过滤
                if (preview.pageTypeHint() != WebPageType.ARTICLE_DETAIL
                        && candidate.discoveredDepth() >= MAX_DEPTH) {
                    linkRepository.markRejected(candidate.id(), "Depth limit non article page");
                    continue;
                }

                if (preview.contentScore() < 500 || preview.snippet().length() < 200) {
                    linkRepository.markRejected(candidate.id(), "Low preview quality");
                    continue;
                }
                //调用分类器
                WebLinkPromotionDecision decision = classifier.classify(parent, link, preview);

                if (decision.pageKind() == WebPageType.MEDIA_PAGE || decision.pageKind() == WebPageType.UNSUPPORTED) {
                    linkRepository.markRejected(candidate.id(), decision.rejectReason());
                    continue;
                }

                //核心判断逻辑
                if (!decision.containsCoreInfo() && decision.pageKind() == WebPageType.ARTICLE_DETAIL) {
                    linkRepository.markRejected(candidate.id(), "Article candidate lacks core travel info");
                    continue;
                }

                if (decision.confidence() < 0.45) {
                    linkRepository.markRejected(candidate.id(), "Low promotion confidence");
                    continue;
                }

                if (candidate.discoveredDepth() == MAX_DEPTH && decision.pageKind() != WebPageType.ARTICLE_DETAIL) {
                    linkRepository.markRejected(candidate.id(), "Depth 2 non article page");
                    continue;
                }

                String purpose = decision.pageKind() == WebPageType.ARTICLE_DETAIL
                        ? "INGEST_OR_DISCOVER"
                        : "DISCOVER_LINKS_ONLY";

                //如果分类器没生成topic，沿用父类的
                String topic = decision.preliminaryTopic() == null || decision.preliminaryTopic().isBlank()
                        ? parent.topic()
                        : decision.preliminaryTopic();

                Long promotedSourceId = sourceRepository.insertPromotedSource(
                        parent,
                        candidate.discoveredUrl(),
                        canonicalUrl,
                        topic,
                        candidate.discoveredDepth(),
                        purpose
                );
                promotedSourceIds.add(promotedSourceId);

                //标记链接状态
                linkRepository.markPromoted(
                        candidate.id(),
                        promotedSourceId,
                        decision.pageKind().name(),
                        topic,
                        decision.confidence()
                );
            } catch (Exception ex) {
                linkRepository.markFailed(candidate.id(), ex.getMessage());
            }
        }
        return promotedSourceIds.stream()
                .distinct()
                .toList();
    }

    //抓取页面，抽取正文和评分
    private WebPagePreview previewTargetPage(WebDiscoveredLink link) throws java.io.IOException {
        WebFetchedPage fetchedPage = pageFetcher.fetch(link.url());
        WebPageExtraction extraction = extractor.extract(fetchedPage, link.discoveredDepth());

        return new WebPagePreview(
                fetchedPage.finalUrl(),
                extraction.title(),
                extraction.preview(1200),
                extraction.pageType(),
                extraction.contentScore(),
                extraction.linkDensity(),
                fetchedPage.rendered()
        );
    }
}