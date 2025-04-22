package com.redbook.tool.dto;

import java.util.List;

import com.redbook.tool.entity.NoteInfo;
import com.redbook.tool.service.ArticleCrawlService.SearchResult;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索结果数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultDTO {
    
    /**
     * 搜索状态
     */
    private SearchResult status;
    
    /**
     * 爬取到的笔记列表
     */
    private List<NoteInfo> noteList;
    
    /**
     * 搜索关键词
     */
    private String keyword;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 构建成功结果
     */
    public static SearchResultDTO success(String userId, String keyword, List<NoteInfo> noteList) {
        return SearchResultDTO.builder()
                .status(SearchResult.SUCCESS)
                .noteList(noteList)
                .keyword(keyword)
                .userId(userId)
                .build();
    }
    
    /**
     * 构建失败结果
     */
    public static SearchResultDTO failed(String userId, String keyword, SearchResult status) {
        return SearchResultDTO.builder()
                .status(status)
                .keyword(keyword)
                .userId(userId)
                .build();
    }
} 