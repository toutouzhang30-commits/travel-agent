package com.xingwuyou.travelagent.chat.rag.ingest.service;


import com.xingwuyou.travelagent.chat.rag.document.RagKnowledgeDocument;
import com.xingwuyou.travelagent.chat.rag.ingest.RagIngestionManifestRepository;
import com.xingwuyou.travelagent.chat.rag.ingest.RagIngestionProperties;
import com.xingwuyou.travelagent.chat.rag.ingest.component.RagDocumentConverter;
import com.xingwuyou.travelagent.chat.rag.ingest.component.RagDocumentLoader;
import com.xingwuyou.travelagent.chat.rag.ingest.dto.RagIngestionManifest;
import com.xingwuyou.travelagent.chat.rag.ingest.dto.RagRawDocument;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.UUID;

//最小防重复策略
//编排入库流程
//先算 hash，manifest 一致则跳过；否则新 run 入库，成功后切换 active run
@Service
public class RagIngestionService {
    private final RagDocumentLoader loader;
    private final RagDocumentConverter converter;
    private final VectorStore vectorStore;
    private final RagIngestionProperties properties;
    private final RagIngestionFingerprintService fingerprintService;
    private final RagIngestionManifestRepository manifestRepository;

    public RagIngestionService(
            RagDocumentLoader loader,
            RagDocumentConverter converter,
            VectorStore vectorStore,
            RagIngestionProperties properties,
            RagIngestionFingerprintService fingerprintService,
            RagIngestionManifestRepository manifestRepository
    ) {
        this.loader = loader;
        this.converter = converter;
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.fingerprintService = fingerprintService;
        this.manifestRepository = manifestRepository;
    }

    //加锁，保证同一时间只有一个线程在执行入库
    public synchronized void ingestAll(){
        if (!properties.isEnabled()) {
            return;
        }

        //核心保护机制，通过postgreSQL的咨询锁实现分布式锁
        String namespace = properties.getNamespace();
        manifestRepository.ensureManifestTable();

        boolean locked = manifestRepository.tryAcquireLock(namespace);
        if (!locked) {
            return;
        }

        //生成唯一以恶搞runId
        String runId = UUID.randomUUID().toString();

        try {
            //读文件
            List<RagRawDocument> rawDocuments = loader.loadAllMarkdown();
            //计算数据和逻辑的身份证
            String contentHash = fingerprintService.contentHash(rawDocuments);
            String pipelineHash = fingerprintService.pipelineHash();

            //最关键的跳转逻辑：是不是使用相同的模型处理过相同的内容
            var existingManifest = manifestRepository.find(namespace);
            //之前的是如果manifest有数据就跳过，不管入库了
            if (existingManifest.isPresent()
                    && existingManifest.get().isCompletedFor(contentHash, pipelineHash)) {

                String activeRunId = existingManifest.get().activeRunId();
                int activeDocumentCount = manifestRepository.countRunDocuments(namespace, activeRunId);

                if (activeDocumentCount > 0) {
                    return;
                }

                manifestRepository.markFailed(
                        namespace,
                        "Manifest completed but active run has no vector documents"
                );
            }


            if (!properties.isAutoRebuild() && existingManifest.isPresent()) {
                return;
            }

            existingManifest
                    .map(RagIngestionManifest::pendingRunId)
                    .ifPresent(pendingRunId -> manifestRepository.deleteRun(namespace, pendingRunId));

            //数据加工和入库
            manifestRepository.markInProgress(namespace, contentHash, pipelineHash, runId);

            //切分
            //新 run 没有真实写入数据时，不会 mark completed，也不会清理旧数据。
            List<RagKnowledgeDocument> knowledgeDocuments = converter.convert(rawDocuments);
            List<Document> documents = knowledgeDocuments.stream()
                    .map(document -> toSpringAiDocument(document, namespace, runId, contentHash, pipelineHash))
                    .toList();

            if (documents.isEmpty()) {
                throw new IllegalStateException("RAG ingestion produced 0 documents; abort rebuild to protect existing vectors");
            }

            vectorStore.add(documents);

            int currentRunCount = manifestRepository.countRunDocuments(namespace, runId);
            if (currentRunCount <= 0) {
                throw new IllegalStateException("RAG vectorStore.add completed but current run has 0 vector documents");
            }

            manifestRepository.markCompleted(namespace, rawDocuments.size(), currentRunCount);
            manifestRepository.deleteInactiveRuns(namespace, runId);

        } catch (Exception e) {
            //出现坏的状况，删除这次的半成品数据
            manifestRepository.deleteRun(namespace, runId);
            manifestRepository.markFailed(namespace, e.getMessage());
            throw e;
        } finally {
            manifestRepository.releaseLock(namespace);
        }
    }
    private Document toSpringAiDocument(
            RagKnowledgeDocument knowledgeDocument,
            String namespace,
            String runId,
            String contentHash,
            String pipelineHash
    ) {
        return new Document(
                knowledgeDocument.content(),
                Map.of(
                        "city", nullSafe(knowledgeDocument.metadata().city()),
                        "topic", nullSafe(knowledgeDocument.metadata().topic()),
                        "sourceName", nullSafe(knowledgeDocument.metadata().sourceName()),
                        "sourceUrl", nullSafe(knowledgeDocument.metadata().sourceUrl()),
                        "verifiedAt", nullSafe(knowledgeDocument.metadata().verifiedAt()),
                        "confidenceLevel", nullSafe(knowledgeDocument.metadata().confidenceLevel()),
                        "ingestionNamespace", namespace,
                        "ingestionRunId", runId,
                        "contentHash", contentHash,
                        "pipelineHash", pipelineHash
                )
        );
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
