package com.xingwuyou.travelagent.chat.rag.ingest;

import com.xingwuyou.travelagent.chat.rag.document.RagDocumentMetadata;
import com.xingwuyou.travelagent.chat.rag.document.RagKnowledgeDocument;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

//把Mrakdown转为最小知识对象
//手动语义切分
@Component
public class RagDocumentConverter {
    //聚合搬运工
    public List<RagKnowledgeDocument> convert(List<String> rawDocuments) {
        return rawDocuments.stream()
                .flatMap(raw -> convertSingle(raw).stream())
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
            String topic = extractValue(metadataBlock, "category");
            String source = extractValue(metadataBlock, "source");

            RagDocumentMetadata metadata = new RagDocumentMetadata(
                    city,
                    topic,
                    source,
                    null,
                    null,
                    "medium"
            );

            documents.add(new RagKnowledgeDocument(contentBlock, metadata));
        }

        return documents;
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
