package com.xingwuyou.travelagent.chat.rag.ingest;


import com.xingwuyou.travelagent.chat.rag.document.RagKnowledgeDocument;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

//最小防重复策略
//编排入库流程
@Service
public class RagIngestionService {
    private final RagDocumentLoader loader;
    private final RagDocumentConverter converter;
    private final VectorStore vectorStore;

    //这里有防止重复逻辑，但是只在本线程
    //加一个本地布尔开关
    private volatile boolean ingested=false;

    public RagIngestionService(RagDocumentLoader loader,
                               RagDocumentConverter converter,
                               VectorStore vectorStore) {
        this.loader = loader;
        this.converter = converter;
        this.vectorStore = vectorStore;
    }

    public synchronized void ingestAll(){
        //如果已经注入，就返回
        if(ingested){
            return;
        }
        //读取文档
        List<String> rawDocuments=loader.loadAllMarkdown();
        //元数据
        List<RagKnowledgeDocument> knowledgeDocuments=converter.convert(rawDocuments);
        //自定义对象翻译成官方格式
        List<Document> documents=knowledgeDocuments.stream()
                .map(this::toSpringAiDocument)
                .toList();
        //入库
        vectorStore.add(documents);
        ingested=true;
    }
    private Document toSpringAiDocument(RagKnowledgeDocument knowledgeDocument) {
        return new Document(
                //正文
                knowledgeDocument.content(),
                //填入元数据
                Map.of(
                        "city", nullSafe(knowledgeDocument.metadata().city()),
                        "topic", nullSafe(knowledgeDocument.metadata().topic()),
                        "sourceName", nullSafe(knowledgeDocument.metadata().sourceName()),
                        "sourceUrl", nullSafe(knowledgeDocument.metadata().sourceUrl()),
                        "verifiedAt", nullSafe(knowledgeDocument.metadata().verifiedAt()),
                        "confidenceLevel", nullSafe(knowledgeDocument.metadata().confidenceLevel())
                )
        );
    }
    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
