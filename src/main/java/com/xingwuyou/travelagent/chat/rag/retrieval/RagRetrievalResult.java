package com.xingwuyou.travelagent.chat.rag.retrieval;

//检索结果
public record RagRetrievalResult( String content,
                                  String city,
                                  String topic,
                                  String sourceName,
                                  String sourceUrl,
                                  String verifiedAt,
                                  double score) {
}
