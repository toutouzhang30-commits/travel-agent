package com.xingwuyou.travelagent.chat.rag.ingest.dto;

import java.time.OffsetDateTime;

//入库清单实体
public record RagIngestionManifest(String namespace,
                                   String contentHash,
                                   String pipelineHash,
                                   String activeRunId,
                                   String pendingRunId,
                                   RagIngestionStatus status,
                                   int documentCount,
                                   int chunkCount,
                                   OffsetDateTime startedAt,
                                   OffsetDateTime updatedAt,
                                   String errorMessage) {
    public boolean isCompletedFor(String expectedContentHash, String expectedPipelineHash) {
        return status == RagIngestionStatus.COMPLETED
                && expectedContentHash.equals(contentHash)
                && expectedPipelineHash.equals(pipelineHash)
                && activeRunId != null
                && !activeRunId.isBlank();
    }
}
