package com.redbook.tool.entity;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小红书笔记信息实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteInfo {
    
    /**
     * 笔记ID
     */
    private String noteId;
    
    /**
     * 笔记完整链接
     */
    private String noteUrl;
    
    /**
     * 笔记标题
     */
    private String title;
    
    /**
     * 笔记封面图片链接
     */
    private String coverImageUrl;
    
    /**
     * 作者ID
     */
    private String authorId;
    
    /**
     * 作者链接
     */
    private String authorUrl;
    
    /**
     * 作者名称
     */
    private String authorName;
    
    /**
     * 点赞数
     */
    private String likeCount;
    
    /**
     * 笔记正文内容
     */
    private String content;
    
    /**
     * 笔记标签列表
     */
    private List<String> tags;
    
    /**
     * 笔记图片链接列表
     */
    private List<String> imageUrls;
} 