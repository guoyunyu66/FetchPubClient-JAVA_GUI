package com.redbook.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记发布结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishResultDTO {
    
    /**
     * 发布状态枚举
     */
    public enum Status {
        SUCCESS,        // 发布成功
        FAILED,         // 发布失败
        LOGIN_EXPIRED,  // 登录已过期
        INTERRUPTED     // 发布被中断
    }
    
    /**
     * 发布状态
     */
    private Status status;
    
    /**
     * 发布成功后的笔记ID
     */
    private String noteId;
    
    /**
     * 发布成功后的笔记URL
     */
    private String noteUrl;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 创建成功结果
     */
    public static PublishResultDTO success(String noteId, String noteUrl) {
        return PublishResultDTO.builder()
                .status(Status.SUCCESS)
                .noteId(noteId)
                .noteUrl(noteUrl)
                .build();
    }
    
    /**
     * 创建失败结果
     */
    public static PublishResultDTO fail(String errorMessage) {
        return PublishResultDTO.builder()
                .status(Status.FAILED)
                .errorMessage(errorMessage)
                .build();
    }
    
    /**
     * 创建登录过期结果
     */
    public static PublishResultDTO loginExpired() {
        return PublishResultDTO.builder()
                .status(Status.LOGIN_EXPIRED)
                .errorMessage("登录已过期，请重新登录")
                .build();
    }
    
    /**
     * 创建中断结果
     */
    public static PublishResultDTO interrupted() {
        return PublishResultDTO.builder()
                .status(Status.INTERRUPTED)
                .errorMessage("发布过程被中断")
                .build();
    }
} 