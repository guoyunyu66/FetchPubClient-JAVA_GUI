package com.redbook.tool.entity;

import com.microsoft.playwright.options.Cookie;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户信息类，封装用户信息、cookies和登录状态
 */
@Data
@NoArgsConstructor
public class UserInfo {
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 用户昵称
     */
    private String nickname;
    
    /**
     * 用户头像URL
     */
    private String avatar;
    
    /**
     * 用户个人描述
     */
    private String description;
    
    /**
     * 小红书ID
     */
    private String redId;
    
    /**
     * 用户cookies
     */
    private List<Cookie> cookies = new ArrayList<>();
    
    /**
     * 登录状态是否有效
     */
    private boolean active = false;
    
    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;
} 