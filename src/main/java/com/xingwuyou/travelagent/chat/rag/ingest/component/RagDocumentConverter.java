package com.xingwuyou.travelagent.chat.rag.ingest.component;

import com.xingwuyou.travelagent.chat.rag.document.RagDocumentMetadata;
import com.xingwuyou.travelagent.chat.rag.document.RagKnowledgeDocument;
import com.xingwuyou.travelagent.chat.rag.ingest.dto.RagRawDocument;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

//把Mrakdown转为最小知识对象
//手动语义切分
@Component
public class RagDocumentConverter {
    private static final int MAX_CHUNK_CHARS = 1200;
    private static final int CHUNK_OVERLAP_CHARS = 120;
    private static final int MIN_CHUNK_CHARS = 80;
    //聚合搬运工
    public List<RagKnowledgeDocument> convert(List<RagRawDocument> rawDocuments) {
        return rawDocuments.stream()
                .flatMap(raw -> convertSingle(raw.content()).stream())
                .toList();
    }


    //返回的（元数据+内容）的列表
    private List<RagKnowledgeDocument> convertSingle(String raw) {
        //按照markdown里面的分割符---
        String[] segments = raw.split("(?m)^---\\s*$");
        List<RagKnowledgeDocument> documents = new ArrayList<>();

        //循环切分得到元数据块，正文块
        for (int i = 1; i + 1 < segments.length; i += 2) {
            String metadataBlock = segments[i].trim();
            String contentBlock = segments[i + 1].trim();

            if (!StringUtils.hasText(contentBlock)) {
                continue;
            }

            String city = extractValue(metadataBlock, "city");
            String category = extractValue(metadataBlock, "category");
            String topic = extractValue(metadataBlock, "topic");

            if (!StringUtils.hasText(topic)) {
                topic = category;
            }

            String source = extractValue(metadataBlock, "source");

            String sectionTitle = extractValue(metadataBlock, "sectionTitle");
            String sectionSummary = extractValue(metadataBlock, "sectionSummary");
            String pageStart = extractValue(metadataBlock, "pageStart");
            String pageEnd = extractValue(metadataBlock, "pageEnd");

            String sourceUrl = extractValue(metadataBlock, "sourceUrl");
            String verifiedAt = extractValue(metadataBlock, "verifiedAt");
            String confidenceLevel = extractValue(metadataBlock, "confidenceLevel");
            String sourceType = extractValue(metadataBlock, "sourceType");
            String sourceId = extractValue(metadataBlock, "sourceId");
            String sourceVersionId = extractValue(metadataBlock, "sourceVersionId");
            String fetchedAt = extractValue(metadataBlock, "fetchedAt");
            String documentContentHash = extractValue(metadataBlock, "contentHash");
            String sourceTopic = extractValue(metadataBlock, "sourceTopic");
            String pageType = extractValue(metadataBlock, "pageType");

            RagDocumentMetadata metadata = new RagDocumentMetadata(
                    city,
                    category,
                    topic,
                    source,
                    sourceUrl,
                    verifiedAt,
                    confidenceLevel == null ? "medium" : confidenceLevel,
                    sourceType == null ? "MARKDOWN" : sourceType,
                    sourceId,
                    sourceVersionId,
                    fetchedAt,
                    documentContentHash,
                    sourceTopic,
                    pageType,
                    sectionTitle,
                    sectionSummary,
                    pageStart,
                    pageEnd
            );

            List<String> chunks = splitIntoChunks(contentBlock);

            for (String chunk : chunks) {
                documents.add(new RagKnowledgeDocument(chunk, metadata));
            }
        }

        return documents;
    }

    private List<String> splitIntoChunks(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }

        String normalized = content
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();

        List<String> paragraphs = normalized.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (paragraph.length() > MAX_CHUNK_CHARS) {
                flushChunk(chunks, current);
                chunks.addAll(splitLongParagraph(paragraph));
                continue;
            }

            if (current.length() + paragraph.length() + 1 > MAX_CHUNK_CHARS) {
                flushChunk(chunks, current);
            }

            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(paragraph);
        }

        flushChunk(chunks, current);
        return chunks;
    }

    private void flushChunk(List<String> chunks, StringBuilder current) {
        if (current.isEmpty()) {
            return;
        }

        String chunk = current.toString().trim();
        current.setLength(0);

        if (chunk.length() >= MIN_CHUNK_CHARS) {
            chunks.add(chunk);
        }
    }

    //长段落兜底划分
    private List<String> splitLongParagraph(String paragraph) {
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < paragraph.length()) {
            int end = Math.min(start + MAX_CHUNK_CHARS, paragraph.length());
            String chunk = paragraph.substring(start, end).trim();

            if (chunk.length() >= MIN_CHUNK_CHARS) {
                chunks.add(chunk);
            }

            if (end == paragraph.length()) {
                break;
            }

            start = Math.max(end - CHUNK_OVERLAP_CHARS, start + 1);
        }

        return chunks;
    }

    //微雕工具，找到这些英语单词后面具体的内容
    private String extractValue(String raw, String key) {
        String prefix = key + ":";
        return raw.lines()
                .map(String::trim)
                .filter(line -> line.startsWith(prefix))
                .findFirst()
                .map(line -> line.substring(line.indexOf(':') + 1).trim().replace("\"", ""))
                .orElse(null);
    }
}
