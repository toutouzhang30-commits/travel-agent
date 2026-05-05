package com.xingwuyou.travelagent.chat.rag.ingest.service;

import com.xingwuyou.travelagent.chat.rag.ingest.RagIngestionProperties;
import com.xingwuyou.travelagent.chat.rag.ingest.dto.RagRawDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Component
//为数据和处理逻辑生成“唯一身份证”
public class RagIngestionFingerprintService {
    private static final String CONVERTER_VERSION = "rag-document-converter-v1";
    private static final String METADATA_VERSION = "metadata-v1";

    private final RagIngestionProperties properties;
    private final String embeddingModel;
    private final String embeddingDimensions;

    public RagIngestionFingerprintService(
            RagIngestionProperties properties,
            @Value("${spring.ai.zhipuai.embedding.options.model:unknown}") String embeddingModel,
            @Value("${spring.ai.zhipuai.embedding.options.dimensions:unknown}") String embeddingDimensions
    ) {
        this.properties = properties;
        this.embeddingModel = embeddingModel;
        this.embeddingDimensions = embeddingDimensions;
    }

    //这个是内容的身份证，将文件名和内容按顺序拼在一起，跑一遍SHA-256生成长字符串
    public String contentHash(List<RagRawDocument> documents) {
        StringBuilder builder = new StringBuilder();
        for (RagRawDocument document : documents) {
            builder.append("FILE:")
                    .append(document.filename())
                    .append('\n')
                    .append(document.content())
                    .append('\n');
        }
        return sha256(builder.toString());
    }

    //你用了什么方法处理这个数据，处理方法变了，旧数据也不对
    public String pipelineHash() {
        return sha256("""
                pipelineVersion=%s
                converterVersion=%s
                metadataVersion=%s
                embeddingModel=%s
                embeddingDimensions=%s
                """.formatted(
                properties.getPipelineVersion(),
                CONVERTER_VERSION,
                METADATA_VERSION,
                embeddingModel,
                embeddingDimensions
        ));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate SHA-256", e);
        }
    }
}
