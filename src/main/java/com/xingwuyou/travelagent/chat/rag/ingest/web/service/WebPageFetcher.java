package com.xingwuyou.travelagent.chat.rag.ingest.web.service;

import com.xingwuyou.travelagent.chat.rag.ingest.web.model.WebFetchedPage;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;

//输入：一个网页 URL
//输出：WebFetchedPage 对象
@Service
public class WebPageFetcher {
    //框架实例，浏览器实例，保证多线程下的可见性
    private volatile com.microsoft.playwright.Playwright playwright;
    private volatile com.microsoft.playwright.Browser browser;

    //优先渲染抓取，失败静态抓取
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(WebPageFetcher.class);

    public WebFetchedPage fetch(String url) throws java.io.IOException {
        try {
            WebFetchedPage page = fetchRendered(url);
            log.info("Rendered fetch success. url={}, finalUrl={}", url, page.finalUrl());
            return page;
        } catch (Exception ex) {
            log.warn("Rendered fetch failed, fallback to static fetch. url={}, error={}", url, ex.toString());
            return fetchStatic(url);
        }
    }

    private WebFetchedPage fetchRendered(String url) {
        try (var context = browser().newContext(
                new com.microsoft.playwright.Browser.NewContextOptions()
                        .setUserAgent("Mozilla/5.0")
                        .setViewportSize(1365, 900)
        )) {
            var page = context.newPage();
            //访问url
            page.navigate(url, new com.microsoft.playwright.Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(8000));

            //等待网络空闲
            try {
                page.waitForLoadState(
                        com.microsoft.playwright.options.LoadState.NETWORKIDLE,
                        new com.microsoft.playwright.Page.WaitForLoadStateOptions().setTimeout(2000)
                );
            } catch (Exception ignored) {
            }

            //模拟下滑页面
            page.evaluate("""
                    async () => {
                      for (let i = 0; i < 2; i++) {
                        window.scrollBy(0, window.innerHeight);
                        await new Promise(r => setTimeout(r, 300));
                      }
                    }
                    """);

            String html = page.content();
            String finalUrl = page.url();
            var document = org.jsoup.Jsoup.parse(html, finalUrl);

            return new WebFetchedPage(
                    url,
                    finalUrl,
                    document.title(),
                    html,
                    document,
                    true,
                    java.time.OffsetDateTime.now()
            );
        }
    }

    //初始化浏览器
    private synchronized com.microsoft.playwright.Browser browser() {
        if (playwright == null) {
            playwright = com.microsoft.playwright.Playwright.create();
            browser = playwright.chromium().launch(
                    new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(true)
            );
        }
        return browser;
    }

    //静态抓取
    private WebFetchedPage fetchStatic(String url) throws java.io.IOException {
        var response = org.jsoup.Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .followRedirects(true)
                .execute();

        //解析HTML
        var document = org.jsoup.Jsoup.parse(
                new java.io.ByteArrayInputStream(response.bodyAsBytes()),
                null,
                response.url().toString()
        );

        return new WebFetchedPage(
                url,
                response.url().toString(),
                document.title(),
                document.outerHtml(),
                document,
                false,
                java.time.OffsetDateTime.now()
        );
    }

    @jakarta.annotation.PreDestroy
    public void close() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }
}
