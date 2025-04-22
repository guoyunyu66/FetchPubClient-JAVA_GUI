package com.redbook.tool.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.redbook.tool.entity.LoginResult;
import com.redbook.tool.entity.UserInfo;
import com.redbook.tool.entity.UserInfoResponse;
import com.redbook.tool.manager.BrowserManager;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private static final String LOGIN_URL = "https://www.xiaohongshu.com/login";
    private static final String EXPLORE_URL = "https://www.xiaohongshu.com/explore";
    // 备选方案：检查个人主页链接是否存在
    private static final String PROFILE_LINK_SELECTOR = "a[href^='/user/profile/']";
    // 登录容器，如果出现说明需要登录
    private static final String LOGIN_CONTAINER_SELECTOR = ".login-container";
    private static final long LOGIN_TIMEOUT_MS = 5 * 60 * 1000; // 5分钟
    
    private final BrowserManager browserManager;
    // 添加UserService依赖
    private final UserService userService;

    /**
     * 检查登录状态并尝试登录
     * 1. 先检查是否有缓存的cookies
     * 2. 如果有缓存，尝试使用缓存登录
     * 3. 如果登录失败或无缓存，执行扫码登录流程
     *
     * @return true 如果登录成功, false 否则
     */
    public boolean loginAndSaveCookies() {
        return loginAndSaveCookies(null);
    }
    
    /**
     * 检查指定用户的登录状态并尝试登录
     * 1. 如果指定了用户ID，则只检查该用户的cookies
     * 2. 如果未指定用户ID，则尝试所有已保存的用户
     * 3. 如果cookies登录失败或无缓存，执行扫码登录流程
     *
     * @param userId 要检查的用户ID，如果为null则检查所有用户
     * @return true 如果登录成功, false 否则
     */
    public boolean loginAndSaveCookies(String userId) {
        // 尝试使用保存的cookies登录
        UserInfo validUser = tryLoginWithSavedCookies(userId);
        if (validUser != null) {
            log.info("使用已保存的cookies成功登录用户: {}", validUser.getNickname());
            return true;
        }
        
        // 如果使用保存的cookies登录失败，则执行扫码登录
        log.info("使用已保存的cookies登录失败或不存在cookies，开始扫码登录流程");
        return scanLogin();
    }
    
    /**
     * 尝试使用指定用户或所有用户的保存cookies登录
     *
     * @param userId 要尝试的用户ID，如果为null则尝试所有用户
     * @return 成功登录的用户信息，如果登录失败返回null
     */
    private UserInfo tryLoginWithSavedCookies(String userId) {
        try {
            // 如果指定了userId，只检查该用户
            if (userId != null && !userId.isEmpty()) {
                log.info("尝试检查指定用户[{}]的登录状态", userId);
                UserInfo user = userService.loadUserInfo(userId);
                if (user == null) {
                    log.info("未找到用户[{}]的信息", userId);
                    return null;
                }
                
                log.info("尝试使用用户[{}]的cookies登录", user.getNickname());
                if (checkLoginWithCookies(user)) {
                    log.info("使用用户[{}]的cookies登录成功", user.getNickname());
                    return user;
                } else {
                    log.info("用户[{}]的cookies已失效", user.getNickname());
                    return null;
                }
            }

            // 否则，尝试所有已保存的用户
            List<UserInfo> allUsers = userService.loadAllUsers();
            if (allUsers.isEmpty()) {
                log.info("没有找到保存的用户信息");
                return null;
            }
            
            log.info("找到{}个已保存的用户信息，尝试登录", allUsers.size());
            
            // 逐个尝试用户登录
            for (UserInfo user : allUsers) {
                log.info("尝试使用用户[{}]的cookies登录", user.getNickname());
                
                if (checkLoginWithCookies(user)) {
                    log.info("使用用户[{}]的cookies登录成功", user.getNickname());
                    return user;
                }
            }
            
            log.info("所有保存的cookies均已失效，需要重新登录");
            return null;
            
        } catch (IOException e) {
            log.error("读取用户信息时发生错误: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 使用指定用户的cookies检查登录状态
     *
     * @param user 用户信息，包含cookies
     * @return true 如果登录成功, false 否则
     */
    private boolean checkLoginWithCookies(UserInfo user) {
        if (user == null || user.getCookies() == null || user.getCookies().isEmpty()) {
            log.warn("用户信息或cookies为空，无法尝试登录");
            return false;
        }
        
        BrowserContext context = null;
        Page page = null;
        try {
            // 创建浏览器上下文并添加cookies
            context = browserManager.getBrowserContext(BrowserManager.BrowserEnum.CHROMIUM);
            context.addCookies(user.getCookies());
            
            // 使用原子类来标记状态
            AtomicBoolean loginSuccess = new AtomicBoolean(false);
            AtomicBoolean loginRequired = new AtomicBoolean(false);
            
            // 捕获用户信息响应
            UserInfoResponse[] userInfoData = new UserInfoResponse[1];
            
            // 设置多个路由拦截用户信息API请求 - 使用更广泛的匹配模式
            log.info("设置API请求拦截...");
            
            // 修正API版本 - 使用v2而不是v1
            context.route("**/api/sns/web/v2/user/me**", route -> {
                if(loginSuccess.get()) {
                    // 如果已经登录成功，不再处理
                    route.resume();
                    return;
                }
                
                log.info("拦截到用户信息API请求(v2版本): {}", route.request().url());
                
                // 使用fetch获取响应
                try {
                    com.microsoft.playwright.APIResponse apiResponse = route.fetch();
                    String responseText = apiResponse.text();
                    log.info("获取到响应内容: {}", responseText);
                    
                    // 解析响应
                    JSONObject jsonObject = JSONUtil.parseObj(responseText);
                    log.info("解析响应JSON: {}", jsonObject.toString());
                    
                    // 检查响应状态
                    if (jsonObject.containsKey("success") && jsonObject.getBool("success", false)) {
                        if (jsonObject.containsKey("data")) {
                            JSONObject data = jsonObject.getJSONObject("data");
                            
                            // 检查是否为guest用户，guest=true表示游客模式
                            if (data.containsKey("guest") && data.getBool("guest", false)) {
                                log.info("检测到游客模式用户，需要完成登录");
                                route.resume();
                                return;
                            }
                            
                            UserInfoResponse infoResponse = new UserInfoResponse();
                            
                            // 正确匹配API字段 - 使用 user_id而不是userId
                            if (data.containsKey("user_id")) infoResponse.setUserId(data.getStr("user_id"));
                            if (data.containsKey("nickname")) infoResponse.setNickname(data.getStr("nickname"));
                            if (data.containsKey("desc")) infoResponse.setDesc(data.getStr("desc"));
                            if (data.containsKey("gender")) infoResponse.setGender(data.getInt("gender", 0));
                            if (data.containsKey("image")) infoResponse.setImages(data.getStr("image"));
                            if (data.containsKey("images")) infoResponse.setImages(data.getStr("images")); // 尝试两种可能的字段名
                            if (data.containsKey("imageb")) infoResponse.setImageb(data.getStr("imageb"));
                            if (data.containsKey("red_id")) infoResponse.setRedId(data.getStr("red_id"));
                            if (data.containsKey("redId")) infoResponse.setRedId(data.getStr("redId")); // 尝试两种可能的字段名
                            
                            // 确保至少有用户ID和昵称，才认为有效
                            if (infoResponse.getUserId() != null && !infoResponse.getUserId().isEmpty()) {
                                userInfoData[0] = infoResponse;
                                loginSuccess.set(true);
                                log.info("从API响应中获取到用户信息，用户ID: {}, 昵称: {}", 
                                    infoResponse.getUserId(), 
                                    (infoResponse.getNickname() != null ? infoResponse.getNickname() : "未知昵称"));
                            } else {
                                log.warn("用户信息不完整，缺少必要字段: {}", data);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("处理用户信息响应时出错: {}", e.getMessage(), e);
                }
                
                // 继续请求
                route.resume();
            });
            
            // 导航到探索页面
            log.info("导航到登录页面...");
            page = browserManager.newPage(context, LOGIN_URL);
            
            // 等待页面加载完成
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            
            // 设置超时时间（10秒足够检测登录状态）
            final long startTime = System.currentTimeMillis();
            final long timeout = 10000; // 10秒
            page.setDefaultTimeout(timeout);
            
            // 检查是否需要登录 (如果出现登录容器)
            while (!loginSuccess.get() && !loginRequired.get()) {
                try {
                    // 每1s检查一次，快速响应
                    page.waitForTimeout(1000);
                    log.info("检查登录状态中... ");
                    
                    try {
                        if (page.locator(LOGIN_CONTAINER_SELECTOR).isVisible()) {
                            loginRequired.set(true);
                            log.info("检测到登录容器，cookie已失效");
                            break;
                        }
                    } catch (PlaywrightException e) {
                        // 忽略元素未找到异常
                    }
                    
                    // 检查是否超时
                    if (System.currentTimeMillis() - startTime > timeout) {
                        log.info("检查登录状态超时，未能确定状态");
                        break;
                    }
                } catch (PlaywrightException e) {
                    if (!e.getMessage().contains("Timeout")) {
                        log.warn("检查过程中发生非超时异常: {}", e.getMessage());
                    }
                }
            }
            
            // 如果检测成功，更新用户信息和cookies
            if (loginSuccess.get()) {
                // 更新cookies
                List<Cookie> updatedCookies = context.cookies();
                user.setCookies(updatedCookies);
                user.setActive(true);
                user.setLastLoginTime(LocalDateTime.now());
                
                // 如果从API获取到了更新的用户信息，则更新
                if (userInfoData[0] != null) {
                    user.setUserId(userInfoData[0].getUserId());
                    user.setNickname(userInfoData[0].getNickname());
                    user.setAvatar(userInfoData[0].getImages());
                    user.setRedId(userInfoData[0].getRedId());
                    user.setDescription(userInfoData[0].getDesc());
                }
                
                // 保存更新的用户信息
                userService.saveUserInfo(user);
                return true;
            }
            
            // 登录失败，标记为非活跃
            userService.markUserLoginExpired(user);
            return false;
            
        } catch (PlaywrightException | IOException e) {
            log.error("检查登录状态时发生错误: {}", e.getMessage(), e);
            return false;
        } finally {
            // 关闭浏览器
            if (page != null) {
                browserManager.closePage(page);
            } else if (context != null) {
                browserManager.closeContext(context);
            }
        }
    }
    
    /**
     * 扫码登录流程
     *
     * @return true 如果登录成功, false 否则
     */
    private boolean scanLogin() {
        Page page = null;
        BrowserContext context = null;
        try {
            // 1. 使用 BrowserManager 打开登录页面
            context = browserManager.getBrowserContext(BrowserManager.BrowserEnum.CHROMIUM);
            page = browserManager.newPage(context, LOGIN_URL);
            log.info("小红书登录页面已打开，请在5分钟内扫码登录...");

            // 2. 等待登录成功
            LoginResult loginResult = waitForLoginSuccess(page, context);

            if (loginResult.isSuccess()) {
                log.info("登录成功！用户ID: {}", loginResult.getUserId());
                
                // 3. 获取并保存用户信息和Cookies
                List<Cookie> cookies = context.cookies();
                UserInfo user = null;
                
                // 如果有用户信息响应数据
                if (loginResult.getUserInfoResponse() != null) {
                    UserInfoResponse infoResponse = loginResult.getUserInfoResponse();
                    user = new UserInfo();
                    user.setUserId(infoResponse.getUserId());
                    user.setNickname(infoResponse.getNickname());
                    user.setAvatar(infoResponse.getImages());
                    user.setRedId(infoResponse.getRedId());
                    user.setDescription(infoResponse.getDesc());
                    user.setCookies(cookies);
                    user.setActive(true);
                    user.setLastLoginTime(LocalDateTime.now());
                    
                } else {
                    // 只有用户ID，创建基本用户信息
                    user = new UserInfo();
                    user.setUserId(loginResult.getUserId());
                    user.setNickname("未知用户");
                    user.setCookies(cookies);
                    user.setActive(true);
                    user.setLastLoginTime(LocalDateTime.now());
                }
                
                // 保存用户信息
                userService.saveUserInfo(user);
                log.info("用户[{}]的信息和Cookies已保存", user.getNickname());
                return true;
                
            } else {
                log.warn("登录超时或失败。");
                return false;
            }

        } catch (PlaywrightException | IOException e) {
            log.error("登录过程中发生错误: {}", e.getMessage(), e);
            return false;
        } finally {
            // 4. 关闭页面和浏览器上下文
            browserManager.closePage(page); // closePage 会同时关闭 context
            log.info("浏览器已关闭。");
        }
    }

    /**
     * 等待用户登录成功
     *
     * @param page Playwright Page 对象
     * @param context Playwright BrowserContext 对象
     * @return LoginResult 登录结果对象，包含成功状态和用户ID
     */
    private LoginResult waitForLoginSuccess(Page page, BrowserContext context) {
        LoginResult result = new LoginResult();
        
        try {
            // 使用原子类来标记状态
            AtomicBoolean loginSuccess = new AtomicBoolean(false);
            AtomicBoolean loginRequired = new AtomicBoolean(false);
            // 添加浏览器关闭状态标记
            AtomicBoolean browserClosed = new AtomicBoolean(false);
            
            // 记录开始等待的时间
            long startWaitTime = System.currentTimeMillis();
            
            // 捕获用户信息响应
            UserInfoResponse[] userInfoData = new UserInfoResponse[1];
            
            // 设置多个路由拦截用户信息API响应 - 使用更广泛的匹配模式
            log.info("设置API路由拦截...");
            
            // 修正API版本 - 使用v2而不是v1
            context.route("**/api/sns/web/v2/user/me**", route -> {
                if(loginSuccess.get()) {
                    // 如果已经登录成功，不再处理
                    route.resume();
                    return;
                }
                
                log.info("拦截到用户信息API请求(v2版本): {}", route.request().url());
                
                // 使用fetch获取响应
                try {
                    com.microsoft.playwright.APIResponse apiResponse = route.fetch();
                    String responseText = apiResponse.text();
                    log.info("获取到响应内容: {}", responseText);
                    
                    // 解析响应
                    JSONObject jsonObject = JSONUtil.parseObj(responseText);
                    log.info("解析响应JSON: {}", jsonObject.toString());
                    
                    // 检查响应状态
                    if (jsonObject.containsKey("success") && jsonObject.getBool("success", false)) {
                        if (jsonObject.containsKey("data")) {
                            JSONObject data = jsonObject.getJSONObject("data");
                            
                            // 检查是否为guest用户，guest=true表示游客模式
                            if (data.containsKey("guest") && data.getBool("guest", false)) {
                                log.info("检测到游客模式用户，需要完成登录");
                                route.resume();
                                return;
                            }
                            
                            UserInfoResponse userInfo = new UserInfoResponse();
                            
                            // 正确匹配API字段 - 使用user_id而不是userId
                            if (data.containsKey("user_id")) userInfo.setUserId(data.getStr("user_id"));
                            if (data.containsKey("nickname")) userInfo.setNickname(data.getStr("nickname"));
                            if (data.containsKey("desc")) userInfo.setDesc(data.getStr("desc"));
                            if (data.containsKey("gender")) userInfo.setGender(data.getInt("gender", 0));
                            if (data.containsKey("image")) userInfo.setImages(data.getStr("image"));
                            if (data.containsKey("images")) userInfo.setImages(data.getStr("images")); // 尝试两种可能的字段名
                            if (data.containsKey("imageb")) userInfo.setImageb(data.getStr("imageb"));
                            if (data.containsKey("red_id")) userInfo.setRedId(data.getStr("red_id"));
                            if (data.containsKey("redId")) userInfo.setRedId(data.getStr("redId")); // 尝试两种可能的字段名
                            
                            // 确保至少有用户ID，才认为有效
                            if (userInfo.getUserId() != null && !userInfo.getUserId().isEmpty()) {
                                userInfoData[0] = userInfo;
                                loginSuccess.set(true);
                                log.info("从API响应中获取到用户信息，用户ID: {}, 昵称: {}", 
                                    userInfo.getUserId(), 
                                    (userInfo.getNickname() != null ? userInfo.getNickname() : "未知昵称"));
                            } else {
                                log.warn("用户信息不完整，缺少必要字段: {}", data);
                            }
                        }
                    }
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("Target page, context or browser has been closed")) {
                        browserClosed.set(true);
                        log.info("浏览器已被用户关闭，中断API监听");
                    } else {
                        log.error("处理用户信息响应时出错: {}", e.getMessage(), e);
                    }
                }
                
                // 继续请求
                try {
                    route.resume();
                } catch (Exception ignored) {
                    // 忽略已关闭的路由错误
                }
            });
            
            // 并发检查登录状态和登录容器
            while (!loginSuccess.get() && !loginRequired.get() && !browserClosed.get()) {
                try {
                    // 每1秒检查一次，快速响应
                    page.waitForTimeout(1000);
                    if((System.currentTimeMillis() - startWaitTime) % 10000 == 0){
                        log.info("等待登录状态变化...");
                    }
                    
                    // 如果已经获取到用户信息
                    if (userInfoData[0] != null) {
                        result.setUserId(userInfoData[0].getUserId());
                        result.setUserInfoResponse(userInfoData[0]);
                        result.setSuccess(true);
                        log.info("从用户信息响应中获取到用户信息: {}", userInfoData[0].getNickname());
                        return result;
                    }
                    
                    // 总等待时间超过LOGIN_TIMEOUT_MS则退出
                    if (System.currentTimeMillis() - startWaitTime > LOGIN_TIMEOUT_MS) {
                        log.warn("登录等待超时，总等待时间超过五分钟");

                        // 如果URL已经是explore开头并且有个人主页链接
                        if (page.url().startsWith(EXPLORE_URL)) {
                            try {
                                Locator profileLink = page.locator(PROFILE_LINK_SELECTOR);
                                if (profileLink.isVisible()) {
                                    String href = profileLink.first().getAttribute("href");
                                    String userId = extractUserId(href);
                                    result.setUserId(userId);
                                    result.setSuccess(true);
                                    log.info("检测到已跳转至探索页面，并且找到个人主页链接: {}", href);
                                    return result;
                                }
                            } catch (PlaywrightException e) {
                                // 忽略元素未找到异常
                            }
                        }
                    
                        break;
                    }
                } catch (PlaywrightException e) {
                    if (e.getMessage() != null && e.getMessage().contains("Target page, context or browser has been closed")) {
                        browserClosed.set(true);
                        log.info("浏览器已被用户关闭，停止等待登录");
                        break;
                    } else if (!e.getMessage().contains("Timeout")) {
                        log.debug("等待过程中发生异常: {}", e.getMessage());
                    }
                }
            }
            
            // 如果浏览器被关闭
            if (browserClosed.get()) {
                log.info("用户已关闭浏览器，登录流程已中断");
                result.setSuccess(false);
                return result;
            }
            
            // 如果检测到需要登录但未成功登录
            if (!loginRequired.get() && !loginSuccess.get()) {
                log.info("用户需要登录但未完成登录流程");
                result.setSuccess(false);
                return result;
            }

        } catch (PlaywrightException e) {
            if (e.getMessage() != null && e.getMessage().contains("Target page, context or browser has been closed")) {
                log.info("浏览器已被用户关闭，登录流程已中断");
            } else {
                log.error("等待登录过程中发生异常: {}", e.getMessage(), e);
            }
            result.setSuccess(false);
        }
        
        return result;
    }
    
    /**
     * 从用户个人主页链接中提取用户ID
     *
     * @param profileHref 个人主页链接，如 "/user/profile/63f5d7ec000000001f031715"
     * @return 用户ID
     */
    private String extractUserId(String profileHref) {
        // 从 /user/profile/USERID 中提取 USERID
        if (profileHref == null || !profileHref.startsWith("/user/profile/")) {
            return "unknown";
        }
        
        return profileHref.substring("/user/profile/".length());
    }
    
    /**
     * 检查指定用户ID的登录状态，不进行扫码登录
     * 
     * @param userId 要检查的用户ID
     * @return true 如果用户cookies有效，false 如果无效或用户不存在
     */
    public boolean checkUserLoginStatus(String userId) {
        if (userId == null || userId.isEmpty()) {
            log.warn("无法检查空的用户ID");
            return false;
        }
        
        try {
            UserInfo user = userService.loadUserInfo(userId);
            if (user == null) {
                log.info("未找到用户[{}]的信息", userId);
                return false;
            }
            
            boolean loginStatus = checkLoginWithCookies(user);
            if (loginStatus) {
                log.info("用户[{}]的cookies有效", user.getNickname());
            } else {
                log.info("用户[{}]的cookies已失效", user.getNickname());
            }
            
            return loginStatus;
        } catch (IOException e) {
            log.error("检查用户[{}]登录状态时发生错误: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * 执行全新的扫码登录流程，不检查现有cookies
     *
     * @return true 如果扫码登录成功, false 否则
     */
    public boolean performNewScanLogin() {
        log.info("执行全新的扫码登录流程，不检查现有cookies");
        return scanLogin();
    }
} 