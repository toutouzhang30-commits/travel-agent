package com.xingwuyou.travelagent.chat.rag.document;


//准备入库的知识文档
//内容和元数据
public record RagKnowledgeDocument(String content,
                                   RagDocumentMetadata metadata) {
}
