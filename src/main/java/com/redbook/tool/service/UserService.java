package com.redbook.tool.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.redbook.tool.entity.UserInfo;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户服务，处理用户信息的存储、加载和管理
 */
@Slf4j
@Service
public class UserService {

    // 存储目录结构
    private static final String REPOSITORY_DIR = "repository";
    private static final String USERS_DIR = REPOSITORY_DIR + "/users";
    // 存储文件名格式: user_{userId}.json 
    private static final String USER_FILE_PATTERN = "user_%s.json";

    /**
     * 创建用户存储目录
     */
    public void createUserDirectory() {
        try {
            Path repoDir = Paths.get(REPOSITORY_DIR);
            Path usersDir = Paths.get(USERS_DIR);
            
            // 创建主存储目录
            if (!Files.exists(repoDir)) {
                Files.createDirectories(repoDir);
                log.info("创建仓库目录: {}", repoDir.toAbsolutePath());
            }
            
            // 创建用户目录
            if (!Files.exists(usersDir)) {
                Files.createDirectories(usersDir);
                log.info("创建用户数据目录: {}", usersDir.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("创建存储目录时发生错误: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 保存用户信息和cookies
     *
     * @param userInfo 用户信息对象
     * @throws IOException 保存文件时可能发生的IO异常
     */
    public void saveUserInfo(UserInfo userInfo) throws IOException {
        if (userInfo == null || userInfo.getUserId() == null) {
            log.warn("无法保存空的用户信息或没有用户ID");
            return;
        }
        
        // 确保目录存在
        createUserDirectory();
        
        // 构建用户文件路径
        String fileName = String.format(USER_FILE_PATTERN, userInfo.getUserId());
        Path userFilePath = Paths.get(USERS_DIR, fileName);
        
        // 更新最后登录时间
        if (userInfo.getLastLoginTime() == null) {
            userInfo.setLastLoginTime(LocalDateTime.now());
        }
        
        // 使用Hutool序列化并保存（美化输出）
        String jsonStr = JSONUtil.toJsonPrettyStr(userInfo);
        FileUtil.writeString(jsonStr, userFilePath.toFile(), StandardCharsets.UTF_8);
        
        log.info("用户[{}]的信息已保存到: {}", userInfo.getNickname(), userFilePath.toAbsolutePath());
    }
    
    /**
     * 基于用户ID加载指定用户信息
     *
     * @param userId 用户ID
     * @return 用户信息对象，如果不存在返回null
     * @throws IOException 读取文件时可能发生的IO异常
     */
    public UserInfo loadUserInfo(String userId) throws IOException {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        
        String fileName = String.format(USER_FILE_PATTERN, userId);
        Path userFilePath = Paths.get(USERS_DIR, fileName);
        
        if (!Files.exists(userFilePath)) {
            log.info("未找到用户[{}]的信息文件", userId);
            return null;
        }
        
        // 使用Hutool从文件读取JSON并转换为对象
        String jsonContent = FileUtil.readString(userFilePath.toFile(), StandardCharsets.UTF_8);
        UserInfo userInfo = JSONUtil.toBean(jsonContent, UserInfo.class);
        
        log.info("已加载用户[{}]的信息", userInfo.getNickname());
        return userInfo;
    }
    
    /**
     * 加载所有用户信息
     *
     * @return 用户信息列表
     * @throws IOException 读取文件时可能发生的IO异常
     */
    public List<UserInfo> loadAllUsers() throws IOException {
        Path usersDir = Paths.get(USERS_DIR);
        List<UserInfo> users = new ArrayList<>();
        
        // 如果目录不存在，返回空列表
        if (!Files.exists(usersDir)) {
            return users;
        }
        
        // 列出所有用户文件
        try (Stream<Path> paths = Files.list(usersDir)) {
            // 筛选JSON文件并加载
            List<Path> userFiles = paths
                .filter(path -> path.toString().endsWith(".json"))
                .filter(path -> path.getFileName().toString().startsWith("user_"))
                .collect(Collectors.toList());
            
            for (Path file : userFiles) {
                try {
                    // 使用Hutool从文件读取JSON并转换为对象
                    String jsonContent = FileUtil.readString(file.toFile(), StandardCharsets.UTF_8);
                    UserInfo user = JSONUtil.toBean(jsonContent, UserInfo.class);
                    users.add(user);
                } catch (Exception e) {
                    log.error("读取用户文件失败: {}, 错误: {}", file.getFileName(), e.getMessage());
                }
            }
        }
        
        return users;
    }

    /**
     * 获取所有用户信息（简化版，无需抛出异常）
     *
     * @return 用户信息列表，出错时返回空列表
     */
    public List<UserInfo> getAllUsers() {
        try {
            return loadAllUsers();
        } catch (IOException e) {
            log.error("加载所有用户信息时发生错误: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 删除用户信息
     *
     * @param userId 要删除的用户ID
     * @return 是否删除成功
     */
    public boolean deleteUserInfo(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        
        String fileName = String.format(USER_FILE_PATTERN, userId);
        Path userFilePath = Paths.get(USERS_DIR, fileName);
        
        try {
            boolean deleted = Files.deleteIfExists(userFilePath);
            if (deleted) {
                log.info("已删除用户[{}]的信息", userId);
            } else {
                log.info("未找到用户[{}]的信息文件，无需删除", userId);
            }
            return deleted;
        } catch (IOException e) {
            log.error("删除用户[{}]信息时发生错误: {}", userId, e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取用户存储目录
     * 
     * @return 用户目录路径
     */
    public String getUsersDirectory() {
        return USERS_DIR;
    }
    
    /**
     * 根据用户ID获取用户信息
     * 
     * @param userId 用户ID
     * @return 用户信息对象，如果不存在返回null
     */
    public UserInfo getUserById(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        
        try {
            return loadUserInfo(userId);
        } catch (IOException e) {
            log.error("加载用户[{}]信息时发生错误: {}", userId, e.getMessage());
            return null;
        }
    }
    
    /**
     * 将用户标记为登录失效状态
     * 清除cookies并更新用户状态
     * 
     * @param userId 用户ID
     * @return 是否成功更新用户状态
     */
    public boolean markUserLoginExpired(String userId) {
        if (userId == null || userId.isEmpty()) {
            log.warn("无法处理空的用户ID");
            return false;
        }
        
        try {
            UserInfo user = loadUserInfo(userId);
            if (user == null) {
                log.warn("未找到用户[{}]的信息", userId);
                return false;
            }
            
            // 更新用户状态
            user.setActive(false);
            user.setCookies(null); // 清除cookies
            user.setLastLoginTime(LocalDateTime.now());
            saveUserInfo(user);
            
            log.info("已将用户[{}]标记为登录失效状态", user.getNickname());
            return true;
        } catch (IOException e) {
            log.error("标记用户[{}]登录失效状态时发生错误: {}", userId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 将用户信息对象标记为登录失效状态
     * 清除cookies并更新用户状态
     * 
     * @param user 用户信息对象
     * @return 是否成功更新用户状态
     */
    public boolean markUserLoginExpired(UserInfo user) {
        if (user == null || user.getUserId() == null) {
            log.warn("无法处理空的用户信息或没有用户ID");
            return false;
        }
        
        return markUserLoginExpired(user.getUserId());
    }
} 