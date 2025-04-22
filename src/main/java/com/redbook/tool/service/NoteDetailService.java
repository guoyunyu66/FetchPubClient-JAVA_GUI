package com.redbook.tool.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.redbook.tool.dto.NoteDetailDTO;
import com.redbook.tool.entity.NoteInfo;
import com.redbook.tool.entity.UserInfo;
import com.redbook.tool.manager.BrowserManager;
import com.redbook.tool.service.ArticleCrawlService.LogCallback;
import com.redbook.tool.service.ArticleCrawlService.ProgressCallback;
import com.redbook.tool.service.ArticleCrawlService.SearchResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 笔记详情爬取服务，处理小红书笔记详情页的爬取
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteDetailService {

    // 登录状态检测选择器 - 与 ArticleCrawlService 保持一致
    private static final String LOGGED_IN_SELECTOR = "li.user.side-bar-component";
    private static final String LOGGED_OUT_SELECTOR = "div.side-bar-component.login-btn";
    // 备用选择器 - 更宽松的选择器
    private static final String LOGGED_IN_SELECTOR_BACKUP = "li.user";
    private static final String LOGGED_OUT_SELECTOR_BACKUP = "div.login-btn";
    private static final String LOGIN_TEXT_SELECTOR = "text='登录'";
    
    // 检查超时设置
    private static final int LOGIN_CHECK_TIMEOUT = 10000; // 总超时时间10秒
    
    // 笔记详情页元素选择器
    private static final String NOTE_TITLE_SELECTOR = "div#detail-title";
    private static final String NOTE_CONTENT_SELECTOR = "span.note-text";
    private static final String NOTE_TAG_SELECTOR = "span.note-text a.tag";
    private static final String NOTE_IMAGE_CONTAINER_SELECTOR = "div.swiper-slide";
    private static final String NOTE_IMAGE_SELECTOR = "img.note-slider-img";
    
    private final BrowserManager browserManager;
    private final UserService userService;
    
    /**
     * 使用指定用户的cookies爬取笔记详情，支持实时状态更新
     * 
     * @param userId 要使用的用户ID
     * @param noteUrl 笔记URL
     * @param logCallback 日志回调，用于实时获取日志信息
     * @param progressCallback 进度回调，用于实时获取进度信息
     * @return CompletableFuture<NoteDetailDTO> 表示爬取操作的结果及数据
     */
    public CompletableFuture<NoteDetailDTO> fetchNoteDetail(
            String userId, 
            String noteUrl, 
            LogCallback logCallback,
            ProgressCallback progressCallback) {
        
        if (userId == null || userId.isEmpty() || noteUrl == null || noteUrl.isEmpty()) {
            log.warn("用户ID或笔记URL为空，无法执行爬取");
            if (logCallback != null) {
                logCallback.log("用户ID或笔记URL为空，无法执行爬取");
            }
            return CompletableFuture.completedFuture(NoteDetailDTO.failed(userId, noteUrl, SearchResult.FAILED));
        }
        
        return CompletableFuture.supplyAsync(() -> {
            BrowserContext context = null;
            Page page = null;
            
            // 用于标记是否是用户主动关闭浏览器
            AtomicBoolean browserClosedByUser = new AtomicBoolean(false);
            
            try {
                log.info("开始使用用户[{}]爬取笔记详情: {}", userId, noteUrl);
                if (logCallback != null) {
                    logCallback.log("开始使用用户[" + userId + "]爬取笔记详情: " + noteUrl);
                }
                
                // 加载用户信息
                UserInfo user = userService.getUserById(userId);
                if (user == null || user.getCookies() == null || user.getCookies().isEmpty()) {
                    log.warn("未找到用户[{}]的信息或cookies为空", userId);
                    if (logCallback != null) {
                        logCallback.log("未找到用户[" + userId + "]的信息或cookies为空");
                    }
                    return NoteDetailDTO.failed(userId, noteUrl, SearchResult.FAILED);
                }
                
                if (progressCallback != null) {
                    progressCallback.onProgress(0, 100, "初始化浏览器...");
                }
                
                // 创建浏览器上下文并添加cookies
                context = browserManager.getBrowserContext(BrowserManager.BrowserEnum.CHROMIUM);
                context.addCookies(user.getCookies());
                
                if (logCallback != null) {
                    logCallback.log("浏览器初始化完成，正在加载cookies");
                }
                
                if (progressCallback != null) {
                    progressCallback.onProgress(10, 100, "正在打开笔记详情页...");
                }
                
                // 创建页面对象并导航到笔记URL
                page = context.newPage();
                
                // 添加页面关闭事件监听器
                Page finalPage = page;
                BrowserContext finalContext = context;
                page.onClose(p -> {
                    log.info("浏览器页面被关闭");
                    // 检查是否是由系统关闭的
                    if (!browserManager.isClosingBySystem()) {
                        log.info("检测到页面被用户手动关闭");
                        browserClosedByUser.set(true);
                    }
                });
                
                // 导航到笔记详情页
                log.info("导航到笔记详情页: {}", noteUrl);
                if (logCallback != null) {
                    logCallback.log("正在打开笔记页面: " + noteUrl);
                }
                page.navigate(noteUrl);
                
                try {
                    // 等待页面加载完成
                    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                    
                    if (progressCallback != null) {
                        progressCallback.onProgress(20, 100, "页面加载完成，检查登录状态...");
                    }
                } catch (PlaywrightException e) {
                    if (e.getMessage().contains("Target page, context or browser has been closed")) {
                        log.info("在页面加载过程中浏览器被关闭");
                        if (browserClosedByUser.get()) {
                            return NoteDetailDTO.failed(userId, noteUrl, SearchResult.INTERRUPTED);
                        }
                    }
                    throw e;
                }
                
                // 检查登录状态
                Boolean isLoggedIn = checkLoginStatus(page, userId);
                if (isLoggedIn == null) {
                    log.warn("登录状态检查超时或出错");
                    if (logCallback != null) {
                        logCallback.log("登录状态检查超时或出错，继续尝试爬取");
                    }
                } else if (!isLoggedIn) {
                    log.warn("检测到用户[{}]登录已失效", userId);
                    if (logCallback != null) {
                        logCallback.log("检测到用户登录已失效，无法爬取笔记详情");
                    }
                    // 标记用户登录状态为失效
                    userService.markUserLoginExpired(userId);
                    
                    // 关闭浏览器
                    try {
                        browserManager.closePage(page);
                        browserManager.closeContext(context);
                    } catch (Exception ex) {
                        log.error("关闭浏览器时发生错误: {}", ex.getMessage());
                    }
                    
                    return NoteDetailDTO.failed(userId, noteUrl, SearchResult.LOGIN_EXPIRED);
                }
                
                if (progressCallback != null) {
                    progressCallback.onProgress(40, 100, "登录状态验证成功，开始爬取笔记详情...");
                }
                
                if (logCallback != null) {
                    logCallback.log("开始爬取笔记详情...");
                }
                
                // 等待数据加载
                try {
                    page.waitForSelector(NOTE_TITLE_SELECTOR, new Page.WaitForSelectorOptions().setTimeout(5000));
                } catch (PlaywrightException e) {
                    if (e.getMessage().contains("Target page, context or browser has been closed")) {
                        if (browserClosedByUser.get()) {
                            return NoteDetailDTO.failed(userId, noteUrl, SearchResult.INTERRUPTED);
                        }
                    } else if (e.getMessage().contains("Timeout")) {
                        log.warn("等待标题元素超时，可能页面结构发生变化");
                        if (logCallback != null) {
                            logCallback.log("等待标题元素超时，可能页面结构发生变化");
                        }
                    }
                    // 对于超时错误，我们继续尝试爬取
                }
                
                // 提取笔记详情
                NoteInfo noteDetail = extractNoteDetail(page);
                
                // 设置基本属性
                noteDetail.setNoteUrl(noteUrl);
                // 从URL提取笔记ID
                String noteId = extractNoteIdFromUrl(noteUrl);
                noteDetail.setNoteId(noteId);
                
                if (progressCallback != null) {
                    progressCallback.onProgress(90, 100, "笔记详情爬取完成，准备结束...");
                }
                
                if (logCallback != null) {
                    logCallback.log("笔记详情爬取完成: " + noteDetail.getTitle());
                }
                
                // 关闭浏览器
                try {
                    browserManager.closePage(page);
                    browserManager.closeContext(context);
                } catch (Exception e) {
                    log.error("关闭浏览器时发生错误: {}", e.getMessage());
                }
                
                if (progressCallback != null) {
                    progressCallback.onProgress(100, 100, "爬取完成!");
                }
                
                return NoteDetailDTO.success(userId, noteUrl, noteDetail);
                
            } catch (PlaywrightException e) {
                // 检查是否是浏览器被手动关闭的异常
                if (e.getMessage().contains("Target page, context or browser has been closed")) {
                    if (browserClosedByUser.get()) {
                        log.info("爬取过程被用户中断");
                        return NoteDetailDTO.failed(userId, noteUrl, SearchResult.INTERRUPTED);
                    } else {
                        log.error("浏览器被意外关闭: {}", e.getMessage());
                        if (logCallback != null) {
                            logCallback.log("浏览器被意外关闭: " + e.getMessage());
                        }
                        return NoteDetailDTO.failed(userId, noteUrl, SearchResult.FAILED);
                    }
                } else {
                    log.error("爬取过程中发生Playwright错误: {}", e.getMessage(), e);
                    if (logCallback != null) {
                        logCallback.log("爬取过程中发生错误: " + e.getMessage());
                    }
                    return NoteDetailDTO.failed(userId, noteUrl, SearchResult.FAILED);
                }
            } catch (Exception e) {
                log.error("爬取过程中发生错误: {}", e.getMessage(), e);
                if (logCallback != null) {
                    logCallback.log("爬取过程中发生错误: " + e.getMessage());
                }
                return NoteDetailDTO.failed(userId, noteUrl, SearchResult.FAILED);
            } finally {
                // 确保资源被释放
                if (page != null && !page.isClosed()) {
                    browserManager.closePage(page);
                }
                if (context != null) {
                    browserManager.closeContext(context);
                }
            }
        });
    }
    
    /**
     * 简化版的fetchNoteDetail方法，没有回调函数
     */
    public CompletableFuture<NoteDetailDTO> fetchNoteDetail(String userId, String noteUrl) {
        return fetchNoteDetail(userId, noteUrl, null, null);
    }
    
    /**
     * 检查登录状态
     * 
     * @param page Playwright页面对象
     * @param userId 用户ID，用于日志记录
     * @return Boolean 登录状态: true=已登录, false=未登录, null=检查失败
     */
    private Boolean checkLoginStatus(Page page, String userId) {
        try {
            log.info("开始检查用户[{}]的登录状态", userId);
            
            // 先尝试使用主选择器检查登录状态
            boolean hasLoggedInElement = page.waitForSelector(LOGGED_IN_SELECTOR, 
                            new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(LOGIN_CHECK_TIMEOUT / 4))
                    .isVisible();
            
            if (hasLoggedInElement) {
                log.info("检测到已登录元素(主选择器)");
                return true;
            }
            
            // 尝试使用备用选择器
            boolean hasLoggedInElementBackup = page.waitForSelector(LOGGED_IN_SELECTOR_BACKUP, 
                            new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(LOGIN_CHECK_TIMEOUT / 4))
                    .isVisible();
            
            if (hasLoggedInElementBackup) {
                log.info("检测到已登录元素(备用选择器)");
                return true;
            }
            
            // 检查登出状态元素
            boolean hasLoggedOutElement = page.waitForSelector(LOGGED_OUT_SELECTOR, 
                            new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(LOGIN_CHECK_TIMEOUT / 4))
                    .isVisible();
            
            if (hasLoggedOutElement) {
                log.info("检测到未登录元素(主选择器)");
                return false;
            }
            
            // 尝试使用备用选择器
            boolean hasLoggedOutElementBackup = page.waitForSelector(LOGGED_OUT_SELECTOR_BACKUP, 
                            new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(LOGIN_CHECK_TIMEOUT / 4))
                    .isVisible();
            
            if (hasLoggedOutElementBackup) {
                log.info("检测到未登录元素(备用选择器)");
                return false;
            }
            
            // 最后尝试检查是否存在"登录"文本
            boolean hasLoginText = page.waitForSelector(LOGIN_TEXT_SELECTOR, 
                            new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(LOGIN_CHECK_TIMEOUT / 4))
                    .isVisible();
            
            if (hasLoginText) {
                log.info("检测到'登录'文本");
                return false;
            }
            
            // 如果上面的检查都未能确定登录状态，则默认假设已登录
            log.info("未明确检测到登录状态，默认假设已登录");
            return true;
            
        } catch (PlaywrightException e) {
            if (e.getMessage().contains("Timeout")) {
                log.warn("登录状态检查超时，可能网络较慢或页面结构变化");
                // 如果超时，我们继续尝试，默认假设已登录
                return true;
            } else {
                log.error("检查登录状态时发生错误: {}", e.getMessage(), e);
                return null;
            }
        } catch (Exception e) {
            log.error("检查登录状态时发生未知错误: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 从笔记详情页提取笔记信息
     * 
     * @param page Playwright页面对象
     * @return NoteInfo 笔记信息对象
     */
    private NoteInfo extractNoteDetail(Page page) {
        NoteInfo noteInfo = new NoteInfo();
        
        try {
            // 1. 提取笔记标题
            try {
                ElementHandle titleElement = page.querySelector(NOTE_TITLE_SELECTOR);
                if (titleElement != null) {
                    String title = titleElement.textContent().trim();
                    noteInfo.setTitle(title);
                    log.info("提取到笔记标题: {}", title);
                }
            } catch (Exception e) {
                log.warn("提取笔记标题时出错: {}", e.getMessage());
            }
            
            // 2. 提取笔记内容
            try {
                ElementHandle contentElement = page.querySelector(NOTE_CONTENT_SELECTOR);
                if (contentElement != null) {
                    // 获取内容元素下的所有span标签
                    List<ElementHandle> spanElements = contentElement.querySelectorAll("span");
                    StringBuilder contentBuilder = new StringBuilder();
                    
                    // 遍历所有span标签，提取文本
                    for (ElementHandle spanElement : spanElements) {
                        String spanText = spanElement.textContent().trim();
                        if (!spanText.isEmpty()) {
                            contentBuilder.append(spanText);
                        }
                    }
                    
                    // 获取最终的内容文本并去除前后空格
                    String content = contentBuilder.toString().trim();
                    noteInfo.setContent(content);
                    
                    if (content.isEmpty()) {
                        log.info("未提取到笔记内容文本，可能只有标签");
                    } else {
                        log.info("提取到笔记内容，长度: {}", content.length());
                    }
                }
            } catch (Exception e) {
                log.warn("提取笔记内容时出错: {}", e.getMessage());
            }
            
            // 3. 提取笔记标签
            try {
                List<ElementHandle> tagElements = page.querySelectorAll(NOTE_TAG_SELECTOR);
                List<String> tags = new ArrayList<>();
                
                for (ElementHandle tagElement : tagElements) {
                    String tag = tagElement.textContent().trim();
                    if (tag.startsWith("#")) {
                        // 移除开头的 # 符号
                        tag = tag.substring(1);
                    }
                    tags.add(tag);
                }
                
                noteInfo.setTags(tags);
                log.info("提取到{}个笔记标签", tags.size());
            } catch (Exception e) {
                log.warn("提取笔记标签时出错: {}", e.getMessage());
            }
            
            // 4. 提取笔记图片
            try {
                List<ElementHandle> slideElements = page.querySelectorAll(NOTE_IMAGE_CONTAINER_SELECTOR);
                List<String> imageUrls = new ArrayList<>();
                Set<String> processedIndices = new HashSet<>();
                
                for (ElementHandle slideElement : slideElements) {
                    // 获取slide索引，用于去重
                    String slideIndex = slideElement.getAttribute("data-swiper-slide-index");
                    if (slideIndex != null && !processedIndices.contains(slideIndex)) {
                        processedIndices.add(slideIndex);
                        
                        // 查找该slide下的图片元素
                        ElementHandle imgElement = slideElement.querySelector(NOTE_IMAGE_SELECTOR);
                        if (imgElement != null) {
                            String imgSrc = imgElement.getAttribute("src");
                            if (imgSrc != null && !imgSrc.isEmpty()) {
                                imageUrls.add(imgSrc);
                            }
                        }
                    }
                }
                
                noteInfo.setImageUrls(imageUrls);
                log.info("提取到{}张笔记图片", imageUrls.size());
            } catch (Exception e) {
                log.warn("提取笔记图片时出错: {}", e.getMessage());
            }
            
            return noteInfo;
            
        } catch (Exception e) {
            log.error("提取笔记详情时发生错误: {}", e.getMessage(), e);
            return noteInfo;
        }
    }
    
    /**
     * 从URL中提取笔记ID
     * 
     * @param url 笔记URL
     * @return 笔记ID
     */
    private String extractNoteIdFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        
        // 尝试从URL中提取笔记ID
        // 小红书笔记URL格式: https://www.xiaohongshu.com/explore/{noteId}
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex != -1 && lastSlashIndex < url.length() - 1) {
            String noteId = url.substring(lastSlashIndex + 1);
            // 如果有查询参数，去掉
            int queryParamIndex = noteId.indexOf('?');
            if (queryParamIndex != -1) {
                noteId = noteId.substring(0, queryParamIndex);
            }
            return noteId;
        }
        
        return "";
    }
} 