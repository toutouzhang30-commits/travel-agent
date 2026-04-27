package com.xingwuyou.travelagent.chat.rag.document;


//最小文档元数据
public record RagDocumentMetadata(String city,
                                  String topic,
                                  String sourceName,
                                  String sourceUrl,
                                  String verifiedAt,
                                  String confidenceLevel) {
}
