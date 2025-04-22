package com.redbook.tool.ui.viewmodel;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.redbook.tool.dto.SearchResultDTO;
import com.redbook.tool.entity.NoteInfo;
import com.redbook.tool.entity.UserInfo;
import com.redbook.tool.service.ArticleCrawlService;
import com.redbook.tool.service.ArticleCrawlService.SearchResult;
import com.redbook.tool.service.UserService;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 文章爬取界面的视图模型，处理搜索和爬取相关的UI状态
 */
@Slf4j
public class ArticleCrawlViewModel {

    private final UserService userService;
    private final ArticleCrawlService articleCrawlService;

    // UI绑定属性
    @Getter
    private final ObservableList<UserInfo> users = FXCollections.observableArrayList();
    
    @Getter
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    
    @Getter
    private final ObjectProperty<UserInfo> selectedUser = new SimpleObjectProperty<>();
    
    @Getter
    private final StringProperty keyword = new SimpleStringProperty("");
    
    @Getter
    private final BooleanProperty searchInProgress = new SimpleBooleanProperty(false);
    
    @Getter
    private final ObservableList<NoteInfo> notes = FXCollections.observableArrayList();
    
    // 不再需要的属性已被移除

    /**
     * 构造函数
     *
     * @param userService 用户服务
     * @param articleCrawlService 文章爬取服务
     */
    public ArticleCrawlViewModel(UserService userService, ArticleCrawlService articleCrawlService) {
        this.userService = userService;
        this.articleCrawlService = articleCrawlService;
    }

    /**
     * 加载所有用户数据，但只加载登录状态有效的用户
     */
    public void loadUsers() {
        loading.set(true);
        
        CompletableFuture.supplyAsync(() -> {
            try {
                return userService.loadAllUsers();
            } catch (IOException e) {
                log.error("加载用户列表失败: {}", e.getMessage(), e);
                return Collections.<UserInfo>emptyList();
            }
        }).thenAccept(loadedUsers -> {
            // 在JavaFX应用线程上更新UI
            Platform.runLater(() -> {
                users.clear();
                
                // 过滤出活跃状态的用户(active=true)
                long activeCount = loadedUsers.stream()
                    .filter(UserInfo::isActive)
                    .peek(users::add)
                    .count();
                
                log.info("找到{}个有效登录的用户账号", activeCount);
                loading.set(false);
            });
        });
    }

    /**
     * 使用选中的用户搜索关键词，支持实时回调
     *
     * @param noteConsumer 笔记消费者回调，用于实时接收爬取到的笔记信息
     * @return CompletableFuture<SearchResultDTO> 表示搜索操作的结果
     */
    public CompletableFuture<SearchResultDTO> searchWithSelectedUser(Consumer<NoteInfo> noteConsumer) {
        UserInfo user = selectedUser.get();
        String searchKeyword = keyword.get();
        
        if (user == null || !user.isActive()) {
            log.warn("未选择有效用户，无法执行搜索");
            SearchResultDTO result = SearchResultDTO.failed(null, searchKeyword, SearchResult.FAILED);
            return CompletableFuture.completedFuture(result);
        }
        
        if (searchKeyword == null || searchKeyword.trim().isEmpty()) {
            log.warn("搜索关键词为空，无法执行搜索");
            SearchResultDTO result = SearchResultDTO.failed(user.getUserId(), "", SearchResult.FAILED);
            return CompletableFuture.completedFuture(result);
        }
        
        // 设置搜索中状态
        searchInProgress.set(true);
        
        // 执行搜索
        return articleCrawlService.searchWithUserCookies(user.getUserId(), searchKeyword.trim(), noteConsumer)
            .thenApply(result -> {
                // 在JavaFX应用线程上更新UI状态
                Platform.runLater(() -> {
                    searchInProgress.set(false);
                    
                    // 如果是登录失效，需要刷新用户列表
                    if (result.getStatus() == SearchResult.LOGIN_EXPIRED) {
                        log.info("检测到用户[{}]登录已失效，刷新用户列表", user.getNickname());
                        loadUsers();
                    }
                });
                return result;
            });
    }
    
    /**
     * 添加一条笔记到列表中
     * 
     * @param note 要添加的笔记
     */
    public void addNote(NoteInfo note) {
        if (note != null) {
            notes.add(note);
        }
    }
    
    /**
     * 清空笔记列表
     */
    public void clearNotes() {
        notes.clear();
    }
} 