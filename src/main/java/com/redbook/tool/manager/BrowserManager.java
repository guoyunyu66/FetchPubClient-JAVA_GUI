package com.redbook.tool.manager;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;

import lombok.extern.slf4j.Slf4j;

/**
 * 浏览器管理器，用于创建和管理Playwright浏览器实例
 */
@Slf4j
@Component
public class BrowserManager {
    
    /**
     * 浏览器类型枚举
     */
    public enum BrowserEnum {
        CHROMIUM,
        FIREFOX,
        WEBKIT,
        EDGE
    }
    
    private final Playwright playwright;
    private final Map<BrowserEnum, Browser> browsers = new HashMap<>();
    private final Map<BrowserEnum, BrowserContext> contexts = new HashMap<>();
    private volatile boolean closingBySystem = false;
    
    /**
     * 构造函数，初始化Playwright实例
     */
    public BrowserManager() {
        log.info("初始化BrowserManager...");
        this.playwright = Playwright.create();
        log.info("Playwright实例创建成功");
    }
    
    /**
     * 获取浏览器上下文
     * 
     * @param browserType 浏览器类型
     * @return 浏览器上下文
     */
    public BrowserContext getBrowserContext(BrowserEnum browserType) {
        switch (browserType) {
            case FIREFOX:
                return createBrowserContext(playwright.firefox(), false);
            case WEBKIT:
                return createBrowserContext(playwright.webkit(), false);
            case EDGE:
                Browser edgeBrowser = playwright.chromium().launch(
                        new BrowserType.LaunchOptions()
                            .setChannel("msedge")
                            .setHeadless(false));
                return edgeBrowser.newContext(getDefaultContextOptions());
            case CHROMIUM:
            default:
                return createBrowserContext(playwright.chromium(), false);
        }
    }
    
    /**
     * 创建浏览器上下文
     * 
     * @param browserType 浏览器类型
     * @param headless 是否无头模式
     * @return 浏览器上下文
     */
    private BrowserContext createBrowserContext(BrowserType browserType, boolean headless) {
        log.info("创建浏览器上下文: {}, 有头模式: {}", browserType.name(), !headless);
        // 明确设置为非无头模式（确保传入headless参数）
        Browser browser = browserType.launch(new BrowserType.LaunchOptions().setHeadless(headless));
        return browser.newContext(getDefaultContextOptions());
    }
    
    /**
     * 获取默认的上下文选项
     * 
     * @return 上下文选项
     */
    private Browser.NewContextOptions getDefaultContextOptions() {
        return new Browser.NewContextOptions()
                .setViewportSize(1280, 800)
                .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36");
    }
    
    /**
     * 创建并打开页面
     * 
     * @param context 浏览器上下文
     * @param url 目标URL
     * @return 页面实例
     */
    public Page newPage(BrowserContext context, String url) {
        log.info("创建新页面并导航至: {}", url);
        Page page = context.newPage();
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        if (url != null && !url.isEmpty()) {
            page.navigate(url);
        }
        return page;
    }
    
    /**
     * 打开指定URL的页面
     * 
     * @param url 目标URL
     * @param browserType 浏览器类型，默认为Chromium
     * @return 页面实例
     */
    public Page openPage(String url, BrowserEnum browserType) {
        browserType = browserType != null ? browserType : BrowserEnum.CHROMIUM;
        BrowserContext context = getBrowserContext(browserType);
        return newPage(context, url);
    }
    
    /**
     * 使用默认浏览器(Chromium)打开指定URL的页面
     * 
     * @param url 目标URL
     * @return 页面实例
     */
    public Page openPage(String url) {
        return openPage(url, BrowserEnum.CHROMIUM);
    }
    
    /**
     * 关闭页面
     * @param page 要关闭的页面
     */
    public void closePage(Page page) {
        if (page != null && !page.isClosed()) {
            try {
                log.info("关闭页面");
                closingBySystem = true;
                page.close();
            } finally {
                closingBySystem = false;
            }
        }
    }
    
    /**
     * 关闭浏览器上下文
     * @param context 要关闭的浏览器上下文
     */
    public void closeContext(BrowserContext context) {
        if (context != null) {
            try {
                log.info("关闭浏览器上下文");
                closingBySystem = true;
                context.close();
            } finally {
                closingBySystem = false;
            }
        }
    }
    
    /**
     * 检查当前是否是系统正在关闭浏览器
     * @return 如果是系统关闭则返回true，否则返回false
     */
    public boolean isClosingBySystem() {
        return closingBySystem;
    }
    
    /**
     * 关闭Playwright实例，释放所有资源
     * 应在应用程序关闭时调用
     */
    public void close() {
        log.info("关闭Playwright实例");
        playwright.close();
    }
} 