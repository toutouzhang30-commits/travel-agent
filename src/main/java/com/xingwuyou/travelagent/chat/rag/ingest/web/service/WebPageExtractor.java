package com.xingwuyou.travelagent.chat.rag.ingest.web.service;

import com.xingwuyou.travelagent.chat.rag.ingest.web.model.WebDiscoveredLink;
import com.xingwuyou.travelagent.chat.rag.ingest.web.model.WebFetchedPage;
import com.xingwuyou.travelagent.chat.rag.ingest.web.model.WebPageExtraction;
import com.xingwuyou.travelagent.chat.rag.ingest.web.model.WebPageType;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.util.List;

//结构规则
//页面标题，正文，页面类型
@Service
public class WebPageExtractor {
    private record ReadabilityCandidate(
            String selector,
            String text,
            int paragraphCount,
            double score,
            double linkDensity,
            double textDensity
    ) {}
    public WebPageExtraction extract(WebFetchedPage fetchedPage, int currentDepth) {
        var document = fetchedPage.document();

        //抽取页面的所有链接
        java.util.List<WebDiscoveredLink> links = extractLinks(document, currentDepth);

        //克隆document并删除干扰标签，删除脚本，样式，导航栏
        var contentDocument = document.clone();
        contentDocument.select("script,style,nav,footer,header,aside,form,noscript").remove();

        //找出最优正文块
        ReadabilityCandidate best = bestCandidate(contentDocument);
        //根据正文得分和链接数量
        WebPageType pageTypeHint = classifyByDensity(best, links.size());

        return new WebPageExtraction(
                fetchedPage.title(),
                best.text(),
                pageTypeHint,
                links,
                best.score(),
                best.linkDensity(),
                best.textDensity(),
                best.selector(),
                fetchedPage.rendered()
        );
    }
    private ReadabilityCandidate bestCandidate(org.jsoup.nodes.Document document) {
        java.util.List<org.jsoup.nodes.Element> candidates = new java.util.ArrayList<>(
                document.select("article,main,[role=main],.article,.article-content,.content,.main-content,.TRS_Editor,.detail,.detail-content,.news_content")
        );

        if (candidates.isEmpty()) {
            candidates.add(document.body());
        }

        return candidates.stream()
                .map(this::scoreCandidate)
                .max(java.util.Comparator.comparingDouble(ReadabilityCandidate::score))
                .orElse(new ReadabilityCandidate("body", "", 0, 0, 1, 0));
    }

    //给每个候选节点打分
    private ReadabilityCandidate scoreCandidate(org.jsoup.nodes.Element element) {
        //文本块提取
        java.util.List<String> blocks = element.select("h1,h2,h3,h4,p")
                .stream()
                .map(e -> e.text().trim())
                .filter(text -> !text.isBlank())
                .distinct()
                .toList();

        String text = String.join("\n", blocks);
        int textLength = text.length();
        int linkTextLength = element.select("a").stream()
                .mapToInt(a -> a.text().length())
                .sum();

        //链接密度，文本密度
        double linkDensity = linkTextLength / (double) Math.max(textLength, 1);
        double textDensity = textLength / (double) Math.max(element.getAllElements().size(), 1);
        int punctuationCount = countPunctuation(text);
        int paragraphCount = element.select("p").size();

        double score = textLength * (1.0 - Math.min(0.85, linkDensity))
                + paragraphCount * 80
                + punctuationCount * 6
                + textDensity * 2;

        return new ReadabilityCandidate(element.cssSelector(), text, paragraphCount, score, linkDensity, textDensity);
    }

    private int countPunctuation(String text) {
        int count = 0;
        for (char ch : text.toCharArray()) {
            if ("，。！？；：、".indexOf(ch) >= 0) {
                count++;
            }
        }
        return count;
    }

    //页面分类逻辑
    private WebPageType classifyByDensity(ReadabilityCandidate candidate, int linkCount) {
        if (candidate.score() >= 900
                && candidate.linkDensity() <= 0.35
                && candidate.paragraphCount() >= 3) {
            return WebPageType.ARTICLE_DETAIL;
        }

        if (linkCount >= 8 || candidate.linkDensity() > 0.45) {
            return WebPageType.LIST_PAGE;
        }

        return WebPageType.NAV_PAGE;
    }

    //链接抽取
    private java.util.List<WebDiscoveredLink> extractLinks(org.jsoup.nodes.Document document, int currentDepth) {
        return document.select("a[href]").stream()
                .map(link -> new WebDiscoveredLink(
                        link.absUrl("href"),
                        link.text().trim(),
                        link.select("img[alt]").attr("alt").trim(),
                        link.select("img").isEmpty() ? "ANCHOR" : "IMAGE_LINK",
                        currentDepth + 1
                ))
                .filter(link -> link.url() != null && !link.url().isBlank())
                .filter(link -> !link.url().startsWith("javascript:"))
                .filter(link -> !link.url().startsWith("mailto:"))
                .filter(link -> !link.url().startsWith("tel:"))
                .filter(link -> !link.url().endsWith("#"))
                .filter(link -> (link.linkText() != null && !link.linkText().isBlank())
                        || (link.imageAlt() != null && !link.imageAlt().isBlank()))
                .distinct()
                .toList();
    }
}