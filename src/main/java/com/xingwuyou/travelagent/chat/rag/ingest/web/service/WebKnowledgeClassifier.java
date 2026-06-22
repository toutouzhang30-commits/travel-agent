package com.xingwuyou.travelagent.chat.rag.ingest.web.service;

import com.xingwuyou.travelagent.chat.rag.ingest.web.model.WebKnowledgeMetadata;
import com.xingwuyou.travelagent.chat.rag.ingest.web.model.WebPageExtraction;
import com.xingwuyou.travelagent.chat.rag.ingest.web.model.WebSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class WebKnowledgeClassifier {
    private final ChatClient chatClient;

    public WebKnowledgeClassifier(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public WebKnowledgeMetadata classify(WebSource source, WebPageExtraction page) {
        String text = page.mainText() == null ? "" : page.mainText();
        String clippedText = text.length() > 3000 ? text.substring(0, 3000) : text;

        return chatClient.prompt()
                .system("""
                        你是旅游 RAG 入库裁判。
                        你必须根据页面标题、正文片段、正文密度指标判断页面类型。
                        只有 ARTICLE_DETAIL 且包含稳定旅游核心信息时才允许入库。
                        栏目页、导航页、搜索页、纯标题列表、轮播图集合不能入库正文。
                        实时票价、余票、天气、今日活动、临时展览不允许进入静态 RAG。
                        输出 WebKnowledgeMetadata。
                        """)
                .user("""
                        城市：%s
                        来源主题：%s
                        标题：%s
                        抽取类型提示：%s
                        contentScore：%.2f
                        linkDensity：%.4f
                        textDensity：%.2f
                        是否渲染抓取：%s
                        
                        正文片段：
                        %s
                        """.formatted(
                        source.city(),
                        source.topic(),
                        page.title(),
                        page.pageType(),
                        page.contentScore(),
                        page.linkDensity(),
                        page.textDensity(),
                        page.rendered(),
                        page.preview(3000)
                ))
                .call()
                .entity(WebKnowledgeMetadata.class);
    }
}
