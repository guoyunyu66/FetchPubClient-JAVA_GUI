package com.redbook.tool.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.microsoft.playwright.options.Cookie;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.redbook.tool.dto.PublishResultDTO;
import com.redbook.tool.entity.NoteInfo;
import com.redbook.tool.entity.UserInfo;
import com.redbook.tool.manager.BrowserManager;
import com.redbook.tool.manager.BrowserManager.BrowserEnum;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

/**
 * 笔记发布服务
 */
@Slf4j
@Service
public class NotePublishService {
    
    private static final String PUBLISH_URL = "https://creator.xiaohongshu.com/publish/publish?source=official";
    private static final String XIAO_HONG_SHU_URL = "https://www.xiaohongshu.com";

    // 图片上传相关选择器
    private static final String UPLOAD_IMAGE_INPUT_SELECTOR = "input.upload-input[type='file'][accept='.jpg,.jpeg,.png,.webp']";
    private static final String UPLOAD_SUCCESS_IMAGE_SELECTOR = "div.format-img img.img.preview";
    
    // 文本内容相关选择器
    private static final String TITLE_INPUT_SELECTOR = "input.d-text[type='text'][placeholder='填写标题会有更多赞哦～']";
    private static final String CONTENT_EDITOR_SELECTOR = "div.ql-editor[contenteditable='true']";
    
    // 标签相关选择器
    private static final String TAG_SUGGESTION_SELECTOR = "div.ql-mention-list-container";
    
    // 发布按钮相关选择器
    private static final String PUBLISH_BUTTON_SELECTOR = "button.publishBtn";
    private static final String SUCCESS_CONTAINER_SELECTOR = "div.success-container";
    
    // 最大重试次数
    private static final int MAX_RETRY_COUNT = 2;
    
    // 临时文件目录
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    
    private final BrowserManager browserManager;
    private final UserService userService;
    
    @Autowired
    public NotePublishService(BrowserManager browserManager, UserService userService) {
        this.browserManager = browserManager;
        this.userService = userService;
    }
    
    /**
     * 发布笔记
     * 
     * @param userId 用户ID
     * @param noteInfo 笔记信息
     * @param logCallback 日志回调
     * @param progressCallback 进度回调
     * @return 发布结果的CompletableFuture
     */
    public CompletableFuture<PublishResultDTO> publishNote(
            String userId, 
            NoteInfo noteInfo,
            Consumer<String> logCallback,
            BiConsumer<Integer, Integer> progressCallback) {
        
        return CompletableFuture.supplyAsync(() -> {
            logCallback.accept("开始发布笔记: " + noteInfo.getTitle());
            progressCallback.accept(0, 100);
            
            // 获取用户信息
            UserInfo userInfo = userService.getUserById(userId);
            if (userInfo == null || !userInfo.isActive()) {
                logCallback.accept("用户不存在或未登录");
                return PublishResultDTO.fail("用户不存在或未登录");
            }
            
            // 下载图片到本地
            List<String> localImagePaths = new ArrayList<>();
            try {
                localImagePaths = downloadImages(noteInfo.getImageUrls(), logCallback, progressCallback);
            } catch (Exception e) {
                log.error("下载图片失败", e);
                logCallback.accept("下载图片失败: " + e.getMessage());
                return PublishResultDTO.fail("下载图片失败: " + e.getMessage());
            }
            
            progressCallback.accept(20, 100);
            
            // 使用Playwright发布笔记
            BrowserContext context = null;
            Page page = null;
            
            try {
                // 创建浏览器上下文
                context = browserManager.getBrowserContext(BrowserEnum.CHROMIUM);
                
                // 恢复用户的cookies
                log.info("cookies是{}", userInfo.getCookies());
                context.addCookies(userInfo.getCookies());
                page = browserManager.newPage(context, XIAO_HONG_SHU_URL);

                // 打开发布页面
                page.navigate(PUBLISH_URL);
                logCallback.accept("已打开发布页面");
                
                // 等待页面加载完成
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                progressCallback.accept(30, 100);
                
                // 检查登录状态
                if (!isLoggedIn(page)) {
                    logCallback.accept("用户登录已失效，请重新登录");
                    return PublishResultDTO.loginExpired();
                }
                
                // 检查是否可以发布
                if (!canPublish(page)) {
                    logCallback.accept("无法发布笔记，请检查账号状态");
                    return PublishResultDTO.fail("无法发布笔记，请检查账号状态");
                }
                
                // 选择上传图文
                clickUploadImageText(page);
                logCallback.accept("已选择上传图文");
                progressCallback.accept(40, 100);
                
                // 上传图片
                boolean uploadSuccess = uploadImages(page, localImagePaths, logCallback);
                if (!uploadSuccess) {
                    return PublishResultDTO.fail("上传图片失败");
                }
                progressCallback.accept(60, 100);
                
                // 输入标题
                inputTitle(page, noteInfo.getTitle());
                logCallback.accept("已输入标题");
                
                // 输入正文
                inputContent(page, noteInfo.getContent());
                logCallback.accept("已输入正文");
                progressCallback.accept(70, 100);
                
                // 输入标签
                if (noteInfo.getTags() != null && !noteInfo.getTags().isEmpty()) {
                    inputTags(page, noteInfo.getTags(), logCallback);
                    logCallback.accept("已输入标签");
                }
                progressCallback.accept(80, 100);
                
                // 点击发布
                boolean publishSuccess = clickPublishButton(page, logCallback);
                if (!publishSuccess) {
                    return PublishResultDTO.fail("发布失败，请重试");
                }
                
                progressCallback.accept(100, 100);
                logCallback.accept("笔记发布成功！");
                
                // 获取发布后的笔记ID和URL (这部分需要根据实际情况调整)
                String noteId = extractNoteId(page);
                String noteUrl = extractNoteUrl(page);
                
                return PublishResultDTO.success(noteId, noteUrl);
                
            } catch (Exception e) {
                log.error("发布笔记失败", e);
                logCallback.accept("发布笔记失败: " + e.getMessage());
                return PublishResultDTO.fail(e.getMessage());
            } finally {
                // 清理临时文件
                cleanupTempFiles(localImagePaths);
                
                // 关闭浏览器
                if (page != null) {
                    browserManager.closePage(page);
                }
                if (context != null) {
                    browserManager.closeContext(context);
                }
            }
        });
    }
    
    /**
     * 检查是否已登录
     */
    private boolean isLoggedIn(Page page) {
        try {
            // 检查是否存在登录按钮或需要登录的元素
            return !page.isVisible("text=登录");
        } catch (Exception e) {
            log.error("检查登录状态失败", e);
            return false;
        }
    }
    
    /**
     * 检查是否可以发布内容
     */
    private boolean canPublish(Page page) {
        try {
            // 等待上传按钮出现
            return page.waitForSelector("input.upload-input[type='file']", 
                    new Page.WaitForSelectorOptions().setTimeout(5000))
                    != null;
        } catch (TimeoutError e) {
            log.error("等待发布页面元素超时", e);
            return false;
        }
    }
    
    /**
     * 点击"上传图文"
     */
    private void clickUploadImageText(Page page) {
        try {
            // 定位并点击"上传图文"按钮
            page.waitForSelector("span.title:has-text('上传图文')");
            page.click("span.title:has-text('上传图文')");
            page.waitForTimeout(1000); // 等待点击生效
        } catch (Exception e) {
            log.error("点击上传图文按钮失败", e);
            throw new RuntimeException("点击上传图文按钮失败", e);
        }
    }
    
    /**
     * 上传图片
     */
    private boolean uploadImages(Page page, List<String> imagePaths, Consumer<String> logCallback) {
        try {
            if (imagePaths.isEmpty()) {
                logCallback.accept("没有图片可上传");
                return false;
            }
            
            // 等待上传输入框可见
            ElementHandle uploadInput = page.waitForSelector(UPLOAD_IMAGE_INPUT_SELECTOR);
            
            // 上传所有图片
            Path[] paths = imagePaths.stream()
                    .map(Paths::get)
                    .toArray(Path[]::new);
            uploadInput.setInputFiles(paths);
            
            // 等待上传完成，检查是否有预览图显示
            logCallback.accept("正在上传图片...");
            return page.waitForSelector(UPLOAD_SUCCESS_IMAGE_SELECTOR, 
                    new Page.WaitForSelectorOptions().setTimeout(30000)) != null;
            
        } catch (Exception e) {
            log.error("上传图片失败", e);
            logCallback.accept("上传图片失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 输入标题
     */
    private void inputTitle(Page page, String title) {
        try {
            // 定位标题输入框并输入
            ElementHandle titleInput = page.waitForSelector(TITLE_INPUT_SELECTOR);
            titleInput.click();
            titleInput.fill(title);
        } catch (Exception e) {
            log.error("输入标题失败", e);
            throw new RuntimeException("输入标题失败", e);
        }
    }
    
    /**
     * 输入正文内容
     */
    private void inputContent(Page page, String content) {
        try {
            // 定位内容编辑器并输入
            ElementHandle contentEditor = page.waitForSelector(CONTENT_EDITOR_SELECTOR);
            contentEditor.click();
            contentEditor.fill(content);
        } catch (Exception e) {
            log.error("输入正文失败", e);
            throw new RuntimeException("输入正文失败", e);
        }
    }
    
    /**
     * 输入标签
     */
    private void inputTags(Page page, List<String> tags, Consumer<String> logCallback) {
        try {
            // 定位内容编辑器
            ElementHandle contentEditor = page.waitForSelector(CONTENT_EDITOR_SELECTOR);
            contentEditor.press("End"); // 移动到内容末尾
            
            // 逐个输入标签
            for (String tag : tags) {
                logCallback.accept("添加标签: " + tag);
                
                // 先输入空格
                contentEditor.type(" ");
                page.waitForTimeout(300);
                
                // 输入#后跟标签
                contentEditor.type("#" + tag);
                page.waitForTimeout(500);
                
                // 等待标签建议出现
                try {
                    page.waitForSelector(TAG_SUGGESTION_SELECTOR, 
                            new Page.WaitForSelectorOptions().setTimeout(2000));
                    
                    // 按回车选择第一个标签建议
                    contentEditor.press("Enter");
                    page.waitForTimeout(500);
                } catch (TimeoutError e) {
                    // 如果没有建议，就继续输入下一个标签
                    logCallback.accept("标签 '" + tag + "' 没有建议，直接使用");
                }
            }
        } catch (Exception e) {
            log.error("输入标签失败", e);
            logCallback.accept("输入标签失败: " + e.getMessage());
            // 继续执行，标签失败不影响发布
        }
    }
    
    /**
     * 点击发布按钮并等待发布结果
     */
    private boolean clickPublishButton(Page page, Consumer<String> logCallback) {
        AtomicInteger retryCount = new AtomicInteger(0);
        
        while (retryCount.get() < MAX_RETRY_COUNT) {
            try {
                // 定位并点击发布按钮
                ElementHandle publishButton = page.waitForSelector(PUBLISH_BUTTON_SELECTOR);
                publishButton.click();
                logCallback.accept("已点击发布按钮，等待发布结果...");
                
                // 等待成功提示或继续判断
                try {
                    // 等待成功提示
                    page.waitForSelector(SUCCESS_CONTAINER_SELECTOR, 
                            new Page.WaitForSelectorOptions().setTimeout(10000));
                    return true;
                } catch (TimeoutError e) {
                    // 检查是否仍在发布页面
                    if (page.isVisible(PUBLISH_BUTTON_SELECTOR)) {
                        // 仍在发布页面，可能需要重试
                        logCallback.accept("发布未完成，尝试重新点击发布按钮...");
                        retryCount.incrementAndGet();
                        continue;
                    } else if (canPublish(page)) {
                        // 如果回到了发布初始页面，说明发布成功
                        return true;
                    }
                }
            } catch (Exception e) {
                log.error("点击发布按钮失败", e);
                logCallback.accept("点击发布按钮失败: " + e.getMessage());
                retryCount.incrementAndGet();
            }
        }
        
        logCallback.accept("发布失败，已重试" + MAX_RETRY_COUNT + "次");
        return false;
    }
    
    /**
     * 从页面提取笔记ID
     */
    private String extractNoteId(Page page) {
        // 这里需要根据实际情况从页面或URL中提取笔记ID
        try {
            return UUID.randomUUID().toString(); // 临时返回随机ID
        } catch (Exception e) {
            log.error("提取笔记ID失败", e);
            return null;
        }
    }
    
    /**
     * 从页面提取笔记URL
     */
    private String extractNoteUrl(Page page) {
        // 这里需要根据实际情况从页面中提取笔记URL
        try {
            return "https://www.xiaohongshu.com/note/" + extractNoteId(page); // 临时构造URL
        } catch (Exception e) {
            log.error("提取笔记URL失败", e);
            return null;
        }
    }
    
    /**
     * 将cookie字符串解析为Playwright的Cookie对象列表
     */
    private List<com.microsoft.playwright.options.Cookie> parseCookiesString(List<Cookie> cookiesString) {
        List<com.microsoft.playwright.options.Cookie> cookies = new ArrayList<>();
        
        // 这里需要根据你的cookie格式进行解析
        // 简单示例：假设cookiesString是JSON格式，需要适当修改
        try {
            // 假设cookiesString已是正确格式，直接创建样例cookie
            com.microsoft.playwright.options.Cookie cookie = new com.microsoft.playwright.options.Cookie("session", "value");
            cookie.setDomain("xiaohongshu.com");
            cookie.setPath("/");
            cookies.add(cookie);
            
            // TODO: 实现实际的cookie解析逻辑
            
        } catch (Exception e) {
            log.error("解析cookies失败", e);
        }
        
        return cookies;
    }
    
    /**
     * 下载图片到本地临时文件夹
     */
    private List<String> downloadImages(List<String> imageUrls, Consumer<String> logCallback, 
            BiConsumer<Integer, Integer> progressCallback) throws IOException {
        
        List<String> localPaths = new ArrayList<>();
        int total = imageUrls.size();
        AtomicInteger current = new AtomicInteger(0);
        
        for (String imageUrl : imageUrls) {
            if (StringUtils.isBlank(imageUrl)) {
                continue;
            }
            
            String fileName = UUID.randomUUID().toString() + getImageExtension(imageUrl);
            Path tempFilePath = Paths.get(TEMP_DIR, fileName);
            
            try (InputStream in = new URL(imageUrl).openStream()) {
                Files.copy(in, tempFilePath, StandardCopyOption.REPLACE_EXISTING);
                localPaths.add(tempFilePath.toString());
                
                current.incrementAndGet();
                int progressPercent = (int) ((current.get() / (double) total) * 20);
                logCallback.accept(String.format("已下载图片(%d/%d): %s", current.get(), total, getFileNameFromUrl(imageUrl)));
                progressCallback.accept(progressPercent, 100);
            } catch (Exception e) {
                log.error("下载图片失败: {}", imageUrl, e);
                logCallback.accept("下载图片失败: " + imageUrl);
                // 继续下载其他图片
            }
        }
        
        return localPaths;
    }
    
    /**
     * 获取图片文件扩展名
     */
    private String getImageExtension(String imageUrl) {
        // 从URL提取扩展名
        String extension = ".jpg"; // 默认扩展名
        
        int lastDotPos = imageUrl.lastIndexOf('.');
        if (lastDotPos > 0) {
            String ext = imageUrl.substring(lastDotPos).toLowerCase();
            if (ext.matches("\\.(jpg|jpeg|png|webp)")) {
                extension = ext;
            }
        }
        
        return extension;
    }
    
    /**
     * 从URL获取文件名
     */
    private String getFileNameFromUrl(String url) {
        int lastSlashPos = url.lastIndexOf('/');
        if (lastSlashPos >= 0 && lastSlashPos < url.length() - 1) {
            return url.substring(lastSlashPos + 1);
        }
        return url;
    }
    
    /**
     * 清理临时文件
     */
    private void cleanupTempFiles(List<String> filePaths) {
        for (String path : filePaths) {
            try {
                Files.deleteIfExists(Paths.get(path));
            } catch (IOException e) {
                log.error("删除临时文件失败: {}", path, e);
            }
        }
    }
} 