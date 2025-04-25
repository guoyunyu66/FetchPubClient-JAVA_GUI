package com.redbook.tool.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.redbook.tool.dto.SearchResultDTO;
import com.redbook.tool.entity.NoteInfo;
import com.redbook.tool.entity.UserInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文章爬取服务，处理小红书文章的搜索和爬取
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleCrawlService {

    private static final String SEARCH_URL_BASE = "https://www.xiaohongshu.com/search_result?keyword=";
    
    // 登录状态检测选择器 - 简化版，不使用data-v属性
    private static final String LOGGED_IN_SELECTOR = "li.user.side-bar-component";
    private static final String LOGGED_OUT_SELECTOR = "div.side-bar-component.login-btn";
    // 备用选择器 - 更宽松的选择器
    private static final String LOGGED_IN_SELECTOR_BACKUP = "li.user";
    private static final String LOGGED_OUT_SELECTOR_BACKUP = "div.login-btn";
    private static final String LOGIN_TEXT_SELECTOR = "text='登录'";
    
    // 检查超时设置
    private static final int LOGIN_CHECK_TIMEOUT = 10000; // 总超时时间10秒
    
    private final UserService userService;
    
    // 定义搜索结果状态
    public enum SearchResult {
        SUCCESS,          // 搜索成功
        FAILED,           // 搜索失败
        INTERRUPTED,      // 搜索被中断(用户手动关闭浏览器)
        LOGIN_EXPIRED     // 登录状态已失效
    }
    
    /**
     * 小红书域名基础URL
     */
    private static final String REDBOOK_BASE_URL = "https://www.xiaohongshu.com";
    
    /**
     * 笔记项选择器 - 一篇完整的笔记元素
     */
    private static final String NOTE_ITEM_SELECTOR = "section.note-item";
    private static final String NOTE_ITEM_BACKUP_SELECTOR = "section[class*='note-item']";
    
    /**
     * 笔记容器选择器
     */
    private static final String FEEDS_CONTAINER_SELECTOR = "div.feeds-container";
    private static final String FEEDS_CONTAINER_BACKUP_SELECTOR = "div[class*='feeds-container']";
    
    /**
     * 笔记链接选择器
     */
    private static final String COVER_LINK_SELECTOR = "a.cover";
    private static final String COVER_LINK_BACKUP_SELECTOR = "a[class*='cover']";
    
    /**
     * 笔记标题选择器
     */
    private static final String TITLE_SELECTOR = "a.title span";
    private static final String TITLE_BACKUP_SELECTOR = "a[class*='title'] span";
    
    /**
     * 作者选择器
     */
    private static final String AUTHOR_SELECTOR = "a.author";
    private static final String AUTHOR_BACKUP_SELECTOR = "a[class*='author']";
    
    /**
     * 作者名称选择器
     */
    private static final String AUTHOR_NAME_SELECTOR = "span.name";
    private static final String AUTHOR_NAME_BACKUP_SELECTOR = "span[class*='name']";
    
    /**
     * 点赞数选择器
     */
    private static final String LIKE_COUNT_SELECTOR = "span.count";
    private static final String LIKE_COUNT_BACKUP_SELECTOR = "span[class*='count']";
    
    /**
     * 笔记ID提取正则表达式
     */
    private static final Pattern NOTE_ID_PATTERN = Pattern.compile("/search_result/([^?]+)");
    
    // 用于状态回调的函数式接口
    @FunctionalInterface
    public interface LogCallback {
        void log(String message);
    }
    
    // 用于进度更新的函数式接口
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int current, int total, String message);
    }
    
    /**
     * 使用指定用户的cookies搜索关键词，支持实时获取笔记和状态更新
     * 
     * @param userId 要使用的用户ID
     * @param keyword 搜索关键词
     * @param noteConsumer 笔记消费者回调，用于实时获取爬取到的笔记
     * @param logCallback 日志回调，用于实时获取日志信息
     * @param progressCallback 进度回调，用于实时获取进度信息
     * @return CompletableFuture<SearchResultDTO> 表示搜索操作的结果及数据
     */
    public CompletableFuture<SearchResultDTO> searchWithUserCookies(
            String userId, 
            String keyword, 
            Consumer<NoteInfo> noteConsumer,
            LogCallback logCallback,
            ProgressCallback progressCallback) {
        
        if (userId == null || userId.isEmpty() || keyword == null || keyword.isEmpty()) {
            log.warn("用户ID或关键词为空，无法执行搜索");
            if (logCallback != null) {
                logCallback.log("用户ID或关键词为空，无法执行搜索");
            }
            return CompletableFuture.completedFuture(SearchResultDTO.failed(userId, keyword, SearchResult.FAILED));
        }
        
        return CompletableFuture.supplyAsync(() -> {
            // 用于标记是否是用户主动关闭浏览器
            AtomicBoolean browserClosedByUser = new AtomicBoolean(false);
            
            try {
                log.info("开始使用用户[{}]搜索关键词: {}", userId, keyword);
                if (logCallback != null) {
                    logCallback.log("开始使用用户[" + userId + "]搜索关键词: " + keyword);
                }
                
                // 加载用户信息
                UserInfo user = userService.getUserById(userId);
                if (user == null || user.getCookies() == null || user.getCookies().isEmpty()) {
                    log.warn("未找到用户[{}]的信息或cookies为空", userId);
                    if (logCallback != null) {
                        logCallback.log("未找到用户[" + userId + "]的信息或cookies为空");
                    }
                    return SearchResultDTO.failed(userId, keyword, SearchResult.FAILED);
                }
                
                if (progressCallback != null) {
                    progressCallback.onProgress(0, 100, "初始化浏览器...");
                }
                
                // 使用try-with-resources确保资源正确关闭
                try (Playwright playwright = Playwright.create()) {
                    // 创建浏览器实例 - 使用Chromium
                    BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                            .setHeadless(true)  // 非无头模式，可以看到界面
                            .setSlowMo(100);     // 减缓操作速度，便于观察
                    
                    try (Browser browser = playwright.chromium().launch(launchOptions)) {
                        // 创建浏览器上下文
                        try (BrowserContext context = browser.newContext()) {
                            // 添加用户的cookies
                context.addCookies(user.getCookies());
                
                if (logCallback != null) {
                    logCallback.log("浏览器初始化完成，正在加载cookies");
                }
                
                // 构建搜索URL
                String encodedKeyword = java.net.URLEncoder.encode(keyword, "UTF-8");
                String searchUrl = SEARCH_URL_BASE + encodedKeyword;
                
                if (progressCallback != null) {
                    progressCallback.onProgress(10, 100, "正在打开搜索页面...");
                }
                
                // 打开搜索页面
                log.info("导航到搜索页面: {}", searchUrl);
                if (logCallback != null) {
                    logCallback.log("正在导航到搜索页面: " + searchUrl);
                }
                            
                            // 创建新页面并导航到URL
                            Page page = context.newPage();
                
                // 设置页面关闭事件监听器
                            page.onClose(p -> {
                    log.info("检测到页面关闭");
                                // 页面被关闭但不是由我们的代码关闭的，视为用户手动关闭
                        log.info("页面被用户手动关闭");
                        if (logCallback != null) {
                            logCallback.log("页面被用户手动关闭，搜索已中断");
                        }
                        browserClosedByUser.set(true);
                });
                
                            // 导航到URL并等待加载
                            page.navigate(searchUrl);
                            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                            
                log.info("搜索页面加载完成");
                if (logCallback != null) {
                    logCallback.log("搜索页面加载完成");
                }
                
                if (progressCallback != null) {
                    progressCallback.onProgress(20, 100, "检查登录状态...");
                }
                
                            // 检查登录状态
                log.info("开始检查登录状态...");
                if (logCallback != null) {
                    logCallback.log("正在检查登录状态...");
                }
                            
                            Boolean isLoginExpired = checkLoginStatus(page, userId);
                
                // 如果无法确定状态，默认认为登录有效
                if (isLoginExpired == null) {
                    log.warn("无法确定用户[{}]登录状态，默认认为登录有效", userId);
                    if (logCallback != null) {
                        logCallback.log("无法确定用户登录状态，默认认为登录有效");
                    }
                    isLoginExpired = false;
                }
                
                // 如果登录已失效，则标记用户并返回
                if (isLoginExpired) {
                    // 调用UserService方法标记用户登录失效
                    userService.markUserLoginExpired(user);
                    if (logCallback != null) {
                        logCallback.log("用户登录已失效，请重新登录");
                    }
                    return SearchResultDTO.failed(userId, keyword, SearchResult.LOGIN_EXPIRED);
                }
                
                if (progressCallback != null) {
                    progressCallback.onProgress(30, 100, "开始爬取搜索结果...");
                }
                
                // 爬取搜索结果
                log.info("开始爬取搜索结果...");
                if (logCallback != null) {
                    logCallback.log("开始爬取搜索结果...");
                }
                
                // 创建计数器跟踪笔记数量，用于进度更新
                AtomicInteger noteCounter = new AtomicInteger(0);
                
                // 爬取前估计结果数量（用于进度计算）
                            final int estimatedTotal = estimateResultCount(page);
                
                            List<NoteInfo> noteList = crawlSearchResults(page, note -> {
                    // 每当获取到一条笔记时，更新计数器和进度
                    int count = noteCounter.incrementAndGet();
                    
                    // 将笔记传递给消费者回调
                    if (noteConsumer != null) {
                        noteConsumer.accept(note);
                    }
                    
                    // 更新进度
                    if (progressCallback != null) {
                        // 进度从30%到90%，按笔记数量比例计算
                        int progress = 30 + (int)((count / (double)Math.max(estimatedTotal, 1)) * 60);
                        progressCallback.onProgress(
                            Math.min(progress, 90), 
                            100, 
                            "已获取 " + count + " 条笔记..."
                        );
                    }
                    
                    // 每5条笔记记录一次日志
                    if (count % 5 == 0 && logCallback != null) {
                        logCallback.log("已获取 " + count + " 条笔记，继续搜索中...");
                    }
                });
                
                log.info("爬取到 {} 条笔记信息", noteList.size());
                if (logCallback != null) {
                    logCallback.log("爬取完成，共获取到 " + noteList.size() + " 条笔记");
                }
                
                if (progressCallback != null) {
                    progressCallback.onProgress(100, 100, "搜索完成");
                }
                
                // 预留一些时间查看页面状态
                try {
                                page.waitForTimeout(2000);
                } catch (PlaywrightException e) {
                    if (e.getMessage().contains("Target page, context or browser has been closed")) {
                        log.info("浏览器在等待期间被关闭");
                        if (browserClosedByUser.get()) {
                            return SearchResultDTO.failed(userId, keyword, SearchResult.INTERRUPTED);
                        }
                    }
                    throw e;
                }
                
                return SearchResultDTO.success(userId, keyword, noteList);
                        }
                    }
                }
            } catch (PlaywrightException e) {
                // 检查是否是浏览器被手动关闭的异常
                if (e.getMessage().contains("Target page, context or browser has been closed")) {
                    if (browserClosedByUser.get()) {
                        log.info("搜索过程被用户中断");
                        return SearchResultDTO.failed(userId, keyword, SearchResult.INTERRUPTED);
                    } else {
                        log.error("浏览器被意外关闭: {}", e.getMessage());
                        if (logCallback != null) {
                            logCallback.log("浏览器被意外关闭: " + e.getMessage());
                        }
                        return SearchResultDTO.failed(userId, keyword, SearchResult.FAILED);
                    }
                } else {
                    log.error("搜索过程中发生Playwright错误: {}", e.getMessage(), e);
                    if (logCallback != null) {
                        logCallback.log("搜索过程中发生错误: " + e.getMessage());
                    }
                    return SearchResultDTO.failed(userId, keyword, SearchResult.FAILED);
                }
            } catch (Exception e) {
                log.error("执行搜索过程中发生错误: {}", e.getMessage(), e);
                if (logCallback != null) {
                    logCallback.log("执行搜索过程中发生错误: " + e.getMessage());
                }
                return SearchResultDTO.failed(userId, keyword, SearchResult.FAILED);
            }
        });
    }
    
    /**
     * 兼容原有方法，不使用日志和进度回调
     */
    public CompletableFuture<SearchResultDTO> searchWithUserCookies(
            String userId, 
            String keyword, 
            Consumer<NoteInfo> noteConsumer) {
        return searchWithUserCookies(userId, keyword, noteConsumer, null, null);
    }
    
    /**
     * 检查用户登录状态
     * 
     * @param page Playwright页面对象
     * @param userId 用户ID，用于日志
     * @return Boolean 登录是否已失效，如果无法确定则返回null
     */
    private Boolean checkLoginStatus(Page page, String userId) {
        // 检查时间起点
        final long startTime = System.currentTimeMillis();
        final long endTime = startTime + LOGIN_CHECK_TIMEOUT;
        
        // 检查结果
        boolean loggedInFound = false;
        boolean loggedOutFound = false;
        
        try {
            
            // 循环检查，直到找到结果或超时
            while (System.currentTimeMillis() < endTime) {
                // 检查是否存在已登录元素 - 依次尝试两个选择器
                try {
                    // 检查主选择器
                    if (page.isVisible(LOGGED_IN_SELECTOR)) {
                        log.info("检测到已登录状态元素: {}", LOGGED_IN_SELECTOR);
                        loggedInFound = true;
                        break;
                    }
                } catch (PlaywrightException e) {
                    // 忽略元素未找到异常，继续尝试备用选择器
                    try {
                        // 检查备用选择器
                        if (page.isVisible(LOGGED_IN_SELECTOR_BACKUP)) {
                            log.info("检测到已登录状态元素(备用): {}", LOGGED_IN_SELECTOR_BACKUP);
                            loggedInFound = true;
                            break;
                        }
                    } catch (PlaywrightException ex) {
                        // 忽略元素未找到异常，继续检查未登录元素
                    }
                }
                
                // 检查是否存在未登录元素 - 依次尝试两个选择器和文本
                try {
                    // 检查主选择器
                    if (page.isVisible(LOGGED_OUT_SELECTOR)) {
                        log.info("检测到未登录状态元素: {}", LOGGED_OUT_SELECTOR);
                        loggedOutFound = true;
                        break;
                    }
                } catch (PlaywrightException e) {
                    // 忽略元素未找到异常，尝试备用选择器
                }
                
                try {
                    // 检查备用选择器
                    if (page.isVisible(LOGGED_OUT_SELECTOR_BACKUP)) {
                        log.info("检测到未登录状态元素(备用): {}", LOGGED_OUT_SELECTOR_BACKUP);
                        loggedOutFound = true;
                        break;
                    }
                } catch (PlaywrightException e) {
                    // 忽略元素未找到异常，尝试文本检查
                }
                
                try {
                    // 检查登录文本
                    if (page.isVisible(LOGIN_TEXT_SELECTOR)) {
                        log.info("检测到'登录'文本，可能是未登录状态");
                        loggedOutFound = true;
                        break;
                    }
                } catch (PlaywrightException e) {
                    // 忽略元素未找到异常，继续下一轮检查
                }
                
                // 短暂等待后继续检查
                page.waitForTimeout(200);
            }
            
            // 判断结果
            if (loggedOutFound) {
                log.warn("用户[{}]的登录状态已失效", userId);
                return true;
            } else if (loggedInFound) {
                log.info("用户[{}]的登录状态有效", userId);
                return false;
            } else {
                // 超时，无法确定状态
                log.warn("无法确定用户[{}]的登录状态，可能是页面结构已变更", userId);
                return null;
            }
        } catch (Exception e) {
            log.error("检查登录状态时发生错误: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 爬取搜索结果页面的笔记信息
     *
     * @param page Playwright页面对象
     * @param noteConsumer 笔记消费者回调，可为null
     * @return 爬取到的笔记信息列表
     */
    private List<NoteInfo> crawlSearchResults(Page page, Consumer<NoteInfo> noteConsumer) {
        List<NoteInfo> noteList = new ArrayList<>();
        
        try {
            // 等待笔记容器加载 - 尝试主选择器，然后是备用选择器
            log.info("等待笔记容器加载...");
            try {
                page.waitForSelector(FEEDS_CONTAINER_SELECTOR, new Page.WaitForSelectorOptions().setTimeout(5000));
                log.info("使用主选择器找到笔记容器");
            } catch (PlaywrightException e) {
                log.warn("使用主选择器未找到笔记容器，尝试备用选择器");
                page.waitForSelector(FEEDS_CONTAINER_BACKUP_SELECTOR, new Page.WaitForSelectorOptions().setTimeout(5000));
                log.info("使用备用选择器找到笔记容器");
            }
            
            // 等待笔记项加载 - 尝试主选择器，然后是备用选择器
            log.info("等待笔记项加载...");
            List<ElementHandle> noteItems = null;
            try {
                page.waitForSelector(NOTE_ITEM_SELECTOR, new Page.WaitForSelectorOptions().setTimeout(5000));
                noteItems = page.querySelectorAll(NOTE_ITEM_SELECTOR);
                log.info("使用主选择器找到 {} 个笔记项", noteItems.size());
            } catch (PlaywrightException e) {
                log.warn("使用主选择器未找到笔记项，尝试备用选择器");
                page.waitForSelector(NOTE_ITEM_BACKUP_SELECTOR, new Page.WaitForSelectorOptions().setTimeout(5000));
                noteItems = page.querySelectorAll(NOTE_ITEM_BACKUP_SELECTOR);
                log.info("使用备用选择器找到 {} 个笔记项", noteItems.size());
            }
            
            if (noteItems == null || noteItems.isEmpty()) {
                log.warn("未找到任何笔记项，可能是页面结构已变更或搜索结果为空");
                return noteList;
            }
            
            // 遍历并解析每个笔记项
            for (ElementHandle noteItem : noteItems) {
                try {
                    NoteInfo noteInfo = extractNoteInfo(noteItem);
                    if (noteInfo != null) {
                        noteList.add(noteInfo);
                        
                        // 如果有消费者回调，实时通知新的笔记信息
                        if (noteConsumer != null) {
                            noteConsumer.accept(noteInfo);
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析笔记项时发生错误: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("爬取搜索结果时发生错误: {}", e.getMessage(), e);
        }
        
        return noteList;
    }
    
    /**
     * 从笔记项元素中提取笔记信息，即使缺少封面也能继续提取其他信息
     */
    private NoteInfo extractNoteInfo(ElementHandle noteItem) {
        try {
            String href = "";
            String coverImageUrl = "";
            
            // 提取笔记链接和ID - 如果没有找到封面链接，尝试从其他元素获取
            ElementHandle coverLink = null;
            try {
                coverLink = noteItem.querySelector(COVER_LINK_SELECTOR);
                if (coverLink != null) {
                    href = coverLink.getAttribute("href");
                    
                    // 提取封面图片
                    ElementHandle coverImg = coverLink.querySelector("img");
                    if (coverImg != null) {
                        coverImageUrl = coverImg.getAttribute("src");
                    }
                }
            } catch (Exception e) {
                try {
                    coverLink = noteItem.querySelector(COVER_LINK_BACKUP_SELECTOR);
                    if (coverLink != null) {
                        href = coverLink.getAttribute("href");
                        
                        // 提取封面图片
                        ElementHandle coverImg = coverLink.querySelector("img");
                        if (coverImg != null) {
                            coverImageUrl = coverImg.getAttribute("src");
                        }
                    }
                } catch (Exception ex) {
                    log.debug("无法找到封面链接: {}", ex.getMessage());
                }
            }
            
            // 如果找不到href，尝试从整个笔记项中找到任何链接
            if (href == null || href.isEmpty()) {
                List<ElementHandle> allLinks = noteItem.querySelectorAll("a");
                for (ElementHandle link : allLinks) {
                    String linkHref = link.getAttribute("href");
                    if (linkHref != null && !linkHref.isEmpty() && linkHref.contains("/search_result/")) {
                        href = linkHref;
                        break;
                    }
                }
            }
            
            // 如果仍然找不到href，则无法确定笔记ID，返回null
            if (href == null || href.isEmpty()) {
                log.warn("无法找到笔记链接，跳过此笔记");
                return null;
            }
            
            String noteUrl = REDBOOK_BASE_URL + href;
            
            // 提取笔记ID
            String noteId = "";
            Matcher matcher = NOTE_ID_PATTERN.matcher(href);
            if (matcher.find()) {
                noteId = matcher.group(1);
            }
            
            // 提取标题
            ElementHandle titleElement = null;
            String title = "未获取到标题";
            try {
                titleElement = noteItem.querySelector(TITLE_SELECTOR);
                if (titleElement != null) {
                    title = titleElement.textContent();
                }
            } catch (Exception e) {
                try {
                    titleElement = noteItem.querySelector(TITLE_BACKUP_SELECTOR);
                    if (titleElement != null) {
                        title = titleElement.textContent();
                    }
                } catch (Exception ex) {
                    log.debug("无法找到标题: {}", ex.getMessage());
                }
            }
            
            // 提取作者信息
            ElementHandle authorElement = null;
            String authorHref = "";
            String authorUrl = "";
            String authorId = "";
            String authorName = "未获取到作者";
            
            try {
                authorElement = noteItem.querySelector(AUTHOR_SELECTOR);
            } catch (Exception e) {
                try {
                    authorElement = noteItem.querySelector(AUTHOR_BACKUP_SELECTOR);
                } catch (Exception ex) {
                    log.debug("无法找到作者元素: {}", ex.getMessage());
                }
            }
            
            if (authorElement != null) {
                authorHref = authorElement.getAttribute("href");
                if (authorHref != null && !authorHref.isEmpty()) {
                    authorUrl = REDBOOK_BASE_URL + authorHref;
                    
                    // 提取作者ID
                    Pattern authorIdPattern = Pattern.compile("/user/profile/([^?]+)");
                    Matcher authorMatcher = authorIdPattern.matcher(authorHref);
                    if (authorMatcher.find()) {
                        authorId = authorMatcher.group(1);
                    }
                }
                
                // 提取作者名称
                ElementHandle nameElement = null;
                try {
                    nameElement = authorElement.querySelector(AUTHOR_NAME_SELECTOR);
                    if (nameElement != null) {
                        authorName = nameElement.textContent();
                    }
                } catch (Exception e) {
                    try {
                        nameElement = authorElement.querySelector(AUTHOR_NAME_BACKUP_SELECTOR);
                        if (nameElement != null) {
                            authorName = nameElement.textContent();
                        }
                    } catch (Exception ex) {
                        log.debug("无法找到作者名称: {}", ex.getMessage());
                    }
                }
            }
            
            // 提取点赞数
            ElementHandle likeElement = null;
            String likeCount = "0";
            try {
                likeElement = noteItem.querySelector(LIKE_COUNT_SELECTOR);
                if (likeElement != null) {
                    likeCount = likeElement.textContent();
                }
            } catch (Exception e) {
                try {
                    likeElement = noteItem.querySelector(LIKE_COUNT_BACKUP_SELECTOR);
                    if (likeElement != null) {
                        likeCount = likeElement.textContent();
                    }
                } catch (Exception ex) {
                    log.debug("无法找到点赞数: {}", ex.getMessage());
                }
            }
            
            // 构建并返回笔记信息对象
            return NoteInfo.builder()
                    .noteId(noteId)
                    .noteUrl(noteUrl)
                    .title(title)
                    .coverImageUrl(coverImageUrl)
                    .authorId(authorId)
                    .authorUrl(authorUrl)
                    .authorName(authorName)
                    .likeCount(likeCount)
                    .build();
        } catch (Exception e) {
            log.warn("提取笔记信息时发生错误: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 估计搜索结果的数量，用于更准确的进度计算
     */
    private int estimateResultCount(Page page) {
        try {
            // 尝试查找分页信息或者结果计数元素
            String[] selectors = {
                "div.page-count", // 常见的分页计数元素
                "span.result-count", // 结果数量元素
                "div.pagination"  // 分页容器
            };
            
            for (String selector : selectors) {
                try {
                    ElementHandle element = page.querySelector(selector);
                    if (element != null) {
                        String text = element.textContent();
                        // 尝试从文本中提取数字
                        if (text != null && !text.isEmpty()) {
                            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d+");
                            java.util.regex.Matcher matcher = pattern.matcher(text);
                            if (matcher.find()) {
                                return Integer.parseInt(matcher.group());
                            }
                        }
                    }
                } catch (Exception e) {
                    // 忽略单个选择器的错误，继续尝试下一个
                }
            }
            
            // 如果无法确定，默认估计20条结果
            return 20;
        } catch (Exception e) {
            log.debug("估计结果数量失败: {}", e.getMessage());
            return 20; // 默认值
        }
    }
} 