package com.redbook.tool.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录结果类，封装登录状态和用户信息
 */
@Data
@NoArgsConstructor
public class LoginResult {
    /**
     * 登录是否成功
     */
    private boolean success;
    
    /**
     * 用户ID
     */
    private String userId = "unknown";
    
    /**
     * 用户信息响应数据，当登录成功时可用
     */
    private UserInfoResponse userInfoResponse;
} 