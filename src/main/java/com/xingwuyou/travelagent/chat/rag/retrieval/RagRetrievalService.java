package com.xingwuyou.travelagent.chat.rag.retrieval;

import com.xingwuyou.travelagent.chat.rag.document.RagKnowledgeDocument;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

//根据问题去做相似度搜索
@Service
public class RagRetrievalService {
    private final VectorStore vectorStore;

    public RagRetrievalService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }
    public List<RagRetrievalResult> retrieve(RagQuery query) {
        //这个是干嘛？
        SearchRequest request=SearchRequest.builder()
                //告诉数据库搜什么语义
                .query(query.userQuestion())
                .topK(query.topK())
                //前置条件
                .filterExpression(buildFilter(query))
                .build();

        //数据库的相似度检查
        List<Document> documents = vectorStore.similaritySearch(request);

        return documents.stream()
                .map(this::toResult)
                .toList();
    }


    //精准过滤
    private  String buildFilter(RagQuery query) {
        if (query.city() == null || query.city().isBlank()) {
            return "";
        }
        return "city == '" + query.city() + "'";
    }

    //这是干什么？value函数在干什么？
    //官方对象翻译成给前端的我的对象
    private RagRetrievalResult toResult(Document document) {
        return new RagRetrievalResult(
                document.getText(),
                value(document, "city"),
                value(document, "topic"),
                value(document, "sourceName"),
                value(document, "sourceUrl"),
                value(document, "verifiedAt"),
                0.0
        );
    }

    private String value(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? null : value.toString();
    }
}
