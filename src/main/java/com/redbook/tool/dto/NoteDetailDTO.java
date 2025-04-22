package com.redbook.tool.dto;

import com.redbook.tool.entity.NoteInfo;
import com.redbook.tool.service.ArticleCrawlService.SearchResult;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记详情数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteDetailDTO {
    
    /**
     * 爬取状态
     */
    private SearchResult status;
    
    /**
     * 爬取到的笔记详情
     */
    private NoteInfo noteDetail;
    
    /**
     * 笔记URL
     */
    private String noteUrl;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 构建成功结果
     */
    public static NoteDetailDTO success(String userId, String noteUrl, NoteInfo noteDetail) {
        return NoteDetailDTO.builder()
                .status(SearchResult.SUCCESS)
                .noteDetail(noteDetail)
                .noteUrl(noteUrl)
                .userId(userId)
                .build();
    }
    
    /**
     * 构建失败结果
     */
    public static NoteDetailDTO failed(String userId, String noteUrl, SearchResult status) {
        return NoteDetailDTO.builder()
                .status(status)
                .noteUrl(noteUrl)
                .userId(userId)
                .build();
    }
} 