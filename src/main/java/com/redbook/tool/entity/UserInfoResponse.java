package com.redbook.tool.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息响应数据类，对应小红书API返回的用户信息结构
 * 对应接口: /api/sns/v1/user/me
 */
@Data
@NoArgsConstructor
public class UserInfoResponse {
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 用户昵称
     */
    private String nickname;
    
    /**
     * 用户个人描述
     */
    private String desc;
    
    /**
     * 用户性别
     * 0: 未设置
     * 1: 男
     * 2: 女
     */
    private int gender;
    
    /**
     * 用户头像 (小尺寸)
     */
    private String images;
    
    /**
     * 用户头像 (大尺寸)
     */
    private String imageb;
    
    /**
     * 是否为游客模式
     */
    private boolean guest;
    
    /**
     * 用户小红书ID
     */
    private String redId;
} 