package com.xingwuyou.travelagent.chat.rag.ingest.web.service;

import com.xingwuyou.travelagent.chat.rag.ingest.web.model.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class WebLinkPromotionClassifier {
    private final ChatClient chatClient;

    public WebLinkPromotionClassifier(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public WebLinkPromotionDecision classify(
            WebSource parent,
            WebDiscoveredLink link,
            WebPagePreview preview
    ) {
        return chatClient.prompt()
                .system("""
                    你是旅游网页入库前的链接提升分类器。
                    你不能只根据 link_text 判断，必须结合目标页片段判断。
                    ARTICLE_DETAIL 必须是具体旅游知识正文页，包含景点、玩法、城市、交通、开放说明、游览建议等稳定信息。
                    LIST_PAGE/NAV_PAGE 只能用于继续发现链接，不能入库正文。
                    MEDIA_PAGE/UNSUPPORTED 必须拒绝。
                    输出 WebLinkPromotionDecision。
                    """)
                .user("""
                    父来源城市：%s
                    父来源主题：%s
                    链接文本：%s
                    图片 alt：%s
                    URL：%s

                    目标页标题：%s
                    目标页类型提示：%s
                    contentScore：%.2f
                    linkDensity：%.4f
                    是否渲染抓取：%s

                    目标页正文片段：
                    %s
                    """.formatted(
                        parent.city(),
                        parent.topic(),
                        link.linkText(),
                        link.imageAlt(),
                        link.url(),
                        preview.title(),
                        preview.pageTypeHint(),
                        preview.contentScore(),
                        preview.linkDensity(),
                        preview.rendered(),
                        preview.snippet()
                ))
                .call()
                .entity(WebLinkPromotionDecision.class);
    }
}
