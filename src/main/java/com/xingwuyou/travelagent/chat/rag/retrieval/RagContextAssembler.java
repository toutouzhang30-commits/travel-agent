package com.xingwuyou.travelagent.chat.rag.retrieval;


import com.xingwuyou.travelagent.chat.dto.SourceReferenceDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

//把 RagRetrievalService 返回的结果拼成模型可消费的上下文
@Component
public class RagContextAssembler {
    public String buildContext(List<RagRetrievalResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        //检索的旅游知识循环遍历，贴上标签
        //因为相似度检查只是检查像不像，并没有逻辑，需要贴标签让ai知道
        String body = results.stream()
                .map(result -> """
                        [城市=%s][主题=%s]
                        %s
                        """.formatted(result.city(), result.topic(), result.content()))
                .collect(Collectors.joining("\n\n"));

        return "以下是检索到的旅游知识，请优先参考这些内容回答：\n\n" + body;
    }
    //负责处理前端展示逻辑
    public List<SourceReferenceDto> toSources(List<RagRetrievalResult> results) {
        return results.stream()
                .map(result -> SourceReferenceDto.rag(
                        result.city(),
                        result.topic(),
                        result.sourceName(),
                        result.sourceUrl(),
                        result.verifiedAt()
                ))
                .toList();
    }
}
