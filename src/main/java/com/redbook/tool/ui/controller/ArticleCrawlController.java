package com.redbook.tool.ui.controller;

import java.util.concurrent.CompletableFuture;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.redbook.tool.dto.SearchResultDTO;
import com.redbook.tool.entity.NoteInfo;
import com.redbook.tool.entity.UserInfo;
import com.redbook.tool.service.ArticleCrawlService;
import com.redbook.tool.service.ArticleCrawlService.SearchResult;
import com.redbook.tool.service.NoteDetailService;
import com.redbook.tool.service.UserService;
import com.redbook.tool.ui.util.AlertUtils;
import com.redbook.tool.ui.viewmodel.ArticleCrawlViewModel;
import com.redbook.tool.util.SpringContextUtil;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Pagination;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import javafx.scene.layout.Region;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 文章爬取界面控制器
 */
@Slf4j
@Component
public class ArticleCrawlController {

    @FXML
    private VBox contentRoot;
    
    @FXML
    private ComboBox<UserInfo> userComboBox;
    
    @FXML
    private TextField keywordTextField;
    
    @FXML
    private Button searchButton;
    
    @FXML
    private HBox searchContainer;
    
    @FXML
    private TableView<NoteInfo> notesTableView;
    
    @FXML
    private ProgressIndicator searchProgressIndicator;
    
    @FXML
    private Pagination pagination;
    
    @FXML
    private TextArea logTextArea;
    
    @FXML
    private ProgressBar searchProgressBar;
    
    private final UserService userService;
    private final ArticleCrawlService articleCrawlService;
    private ArticleCrawlViewModel viewModel;
    private HostServices hostServices;
    
    // 分页相关常量
    private static final int ROWS_PER_PAGE = 10;
    
    // 日期时间格式化器
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // 分页显示的当前数据
    private ObservableList<NoteInfo> currentPageData = FXCollections.observableArrayList();

    /**
     * 构造函数
     *
     * @param userService 用户服务
     * @param articleCrawlService 文章爬取服务
     */
    @Autowired
    public ArticleCrawlController(UserService userService, ArticleCrawlService articleCrawlService) {
        this.userService = userService;
        this.articleCrawlService = articleCrawlService;
    }
    
    /**
     * 设置主机服务，用于打开外部链接
     * 
     * @param hostServices JavaFX主机服务
     */
    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    /**
     * 初始化方法
     */
    @FXML
    public void initialize() {
        log.info("初始化文章爬取控制器");
        
        // 创建ViewModel实例
        viewModel = new ArticleCrawlViewModel(userService, articleCrawlService);
        
        // 设置用户选择框
        setupUserComboBox();
        
        // 设置笔记表格
        setupNotesTable();
        
        // 设置分页控件
        setupPagination();
        
        // 初始化日志文本区域
        initLogTextArea();
        
        // 初始化进度条
        if (searchProgressBar != null) {
            searchProgressBar.setProgress(0);
            searchProgressBar.setVisible(false);
        }
        
        // 绑定关键词输入框
        keywordTextField.textProperty().bindBidirectional(viewModel.getKeyword());
        
        // 绑定UI状态
        bindUIState();
        
        // 加载用户列表
        loadUserData();
        
        // 添加初始日志信息
        appendToLog("系统就绪，请选择账号和关键词开始搜索");
    }
    
    /**
     * 初始化日志文本区域
     */
    private void initLogTextArea() {
        // 设置日志区域的样式和自动滚动
        logTextArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");
    }
    
    /**
     * 添加日志到文本区域，并自动滚动到底部
     * 
     * @param message 日志消息
     */
    private void appendToLog(String message) {
        Platform.runLater(() -> {
            // 添加时间戳
            String timestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER);
            String formattedMessage = String.format("[%s] %s\n", timestamp, message);
            
            // 添加消息到文本区域
            logTextArea.appendText(formattedMessage);
            
            // 自动滚动到底部
            logTextArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    /**
     * 设置表格响应式高度调整
     */
    private void setupTableResponsiveHeight() {
        // 已不再需要，使用正确的布局约束即可
    }

    /**
     * 设置笔记表格
     */
    private void setupNotesTable() {
        // 创建表格列
        TableColumn<NoteInfo, String> titleColumn = new TableColumn<>("标题");
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleColumn.setPrefWidth(200);
        
        TableColumn<NoteInfo, String> authorColumn = new TableColumn<>("作者");
        authorColumn.setCellValueFactory(new PropertyValueFactory<>("authorName"));
        authorColumn.setPrefWidth(120);
        
        TableColumn<NoteInfo, String> likeCountColumn = new TableColumn<>("点赞数");
        likeCountColumn.setCellValueFactory(new PropertyValueFactory<>("likeCount"));
        likeCountColumn.setPrefWidth(80);
        
        // 创建操作列
        TableColumn<NoteInfo, String> actionColumn = new TableColumn<>("操作");
        actionColumn.setPrefWidth(160);
        
        actionColumn.setCellFactory(param -> new TableCell<NoteInfo, String>() {
            private final Button viewNoteBtn = new Button("一键发布");
            private final Button viewAuthorBtn = new Button("爬取作者");
            private final HBox pane = new HBox(5, viewNoteBtn, viewAuthorBtn);
            
            {
                // 设置按钮样式和大小
                viewNoteBtn.getStyleClass().add("btn-primary");
                viewNoteBtn.setPrefHeight(30);
                viewAuthorBtn.getStyleClass().add("btn-secondary");
                viewAuthorBtn.setPrefHeight(30);
                pane.setAlignment(javafx.geometry.Pos.CENTER);
                
                viewNoteBtn.setOnAction(event -> {
                    NoteInfo note = getTableRow().getItem();
                    if (note != null) {
                        appendToLog("开始爬取笔记详情: " + note.getTitle());
                        
                        // 获取当前选中的用户
                        UserInfo selectedUser = viewModel.getSelectedUser().get();
                        if (selectedUser == null || !selectedUser.isActive()) {
                            appendToLog("未选择有效用户，无法爬取笔记详情");
                            AlertUtils.showWarning("操作失败", "未选择有效用户，请先选择一个有效登录的账号");
                            return;
                        }
                        
                        // 显示进度条
                        if (searchProgressBar != null) {
                            searchProgressBar.setProgress(0);
                            searchProgressBar.setVisible(true);
                        }
                        
                        // 获取主控制器
                        MainController mainController = SpringContextUtil.getBean(MainController.class);
                        
                        // 获取笔记发布控制器，用于显示爬取到的笔记详情
                        NotePublishController publishController = SpringContextUtil.getBean(NotePublishController.class);
                        
                        // 设置主控制器，以便从发布界面返回
                        publishController.setMainController(mainController);
                        
                        // 获取笔记详情服务
                        NoteDetailService noteDetailService = SpringContextUtil.getBean(NoteDetailService.class);
                        
                        // 调用爬取服务
                        noteDetailService.fetchNoteDetail(
                            selectedUser.getUserId(),
                            note.getNoteUrl(),
                            // 日志回调
                            message -> appendToLog(message),
                            // 进度回调
                            (current, total, message) -> Platform.runLater(() -> {
                                if (searchProgressBar != null) {
                                    double progress = current / (double) total;
                                    searchProgressBar.setProgress(progress);
                                    
                                    // 更新状态文本
                                    if (progress < 1.0) {
                                        appendToLog(message);
                                    }
                                }
                            })
                        ).thenAccept(result -> {
                            Platform.runLater(() -> {
                                // 隐藏进度条
                                if (searchProgressBar != null) {
                                    searchProgressBar.setProgress(1.0);
                                    // 延迟2秒后隐藏进度条
                                    new Thread(() -> {
                                        try {
                                            Thread.sleep(2000);
                                            Platform.runLater(() -> searchProgressBar.setVisible(false));
                                        } catch (InterruptedException e) {
                                            // 忽略中断异常
                                        }
                                    }).start();
                                }
                                
                                if (result.getStatus() == SearchResult.SUCCESS && result.getNoteDetail() != null) {
                                    // 成功获取到笔记详情
                                    appendToLog("成功获取笔记详情: " + result.getNoteDetail().getTitle());
                                    
                                    // 加载笔记详情到发布界面
                                    publishController.loadNoteInfo(result.getNoteDetail());
                                    
                                    // 切换到发布界面
                                    mainController.onSwitchToPublish();
                                } else if (result.getStatus() == SearchResult.LOGIN_EXPIRED) {
                                    // 登录状态失效
                                    appendToLog("用户登录已失效");
                                    AlertUtils.showWarning("登录已失效", 
                                        "用户 " + selectedUser.getNickname() + " 的登录状态已失效，请重新登录");
                                    
                                    // 刷新用户列表
                                    loadUserData();
                                } else {
                                    // 爬取失败
                                    appendToLog("爬取笔记详情失败: " + result.getStatus());
                                    AlertUtils.showError("爬取失败", "无法获取笔记详情，请检查网络连接或重试");
                                }
                            });
                        });
                    }
                });
                
                viewAuthorBtn.setOnAction(event -> {
                    NoteInfo note = getTableRow().getItem();
                    if (note != null && hostServices != null) {
                        hostServices.showDocument(note.getAuthorUrl());
                        appendToLog("查看作者: " + note.getAuthorName());
                    }
                });
            }
            
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(pane);
                }
            }
        });
        
        // 添加列到表格
        notesTableView.getColumns().addAll(titleColumn, authorColumn, likeCountColumn, actionColumn);
        
        // 设置表格数据和样式
        notesTableView.setItems(currentPageData);
        notesTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        // 设置行高
        notesTableView.setFixedCellSize(40);
        
        // 监听ViewModel的数据变化
        viewModel.getNotes().addListener((ListChangeListener<NoteInfo>) c -> {
            updatePagination();
        });
    }

    /**
     * 设置用户选择下拉框
     */
    private void setupUserComboBox() {
        // 设置ComboBox的项显示转换器
        userComboBox.setConverter(new StringConverter<UserInfo>() {
            @Override
            public String toString(UserInfo user) {
                return user == null ? "" : user.getNickname() + (user.getRedId() != null ? " (" + user.getRedId() + ")" : "");
            }

            @Override
            public UserInfo fromString(String string) {
                return null; // 不需要从字符串转换
            }
        });
        
        // 绑定选中用户
        userComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            viewModel.getSelectedUser().set(newVal);
            if (newVal != null) {
                appendToLog("已选择用户: " + newVal.getNickname());
            }
        });
        
        // 绑定用户列表数据
        userComboBox.setItems(viewModel.getUsers());
        
        // 当用户列表变化时，自动选择第一个用户
        viewModel.getUsers().addListener((ListChangeListener<UserInfo>) c -> {
            if (!viewModel.getUsers().isEmpty() && userComboBox.getSelectionModel().getSelectedItem() == null) {
                Platform.runLater(() -> userComboBox.getSelectionModel().selectFirst());
            }
        });
    }

    /**
     * 绑定UI状态
     */
    private void bindUIState() {
        // 简化UI状态绑定，移除不必要的绑定
        
        // 绑定搜索进度指示器
        searchProgressIndicator.visibleProperty().bind(viewModel.getSearchInProgress());
        
        // 绑定搜索按钮状态
        searchButton.disableProperty().bind(
            viewModel.getKeyword().isEmpty()
            .or(viewModel.getSelectedUser().isNull())
            .or(viewModel.getSearchInProgress())
        );
        
        // 绑定搜索中状态
        viewModel.getSearchInProgress().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                searchButton.setText("搜索中...");
            } else {
                searchButton.setText("搜索");
            }
        });
    }

    /**
     * 加载用户数据
     */
    private void loadUserData() {
        appendToLog("正在加载用户列表...");
        viewModel.loadUsers();
    }

    /**
     * 搜索按钮点击事件
     */
    @FXML
    private void onSearch() {
        UserInfo selectedUser = viewModel.getSelectedUser().get();
        String keyword = viewModel.getKeyword().get();
        
        appendToLog("开始搜索，关键词: " + keyword + ", 用户: " + 
            (selectedUser != null ? selectedUser.getNickname() : "未选择"));
        
        log.info("开始搜索，关键词: {}, 用户: {}", 
            keyword, 
            selectedUser != null ? selectedUser.getNickname() : "未选择");
        
        // 清空之前的搜索结果
        viewModel.clearNotes();
        
        // 清空表格和更新分页控件
        Platform.runLater(() -> {
            currentPageData.clear();
            // 直接设置表格高度，即使没有数据也保持固定高度
            forceTableHeight();
            // 重置分页控件
            updatePagination();
        });
        
        // 显示进度条并设置初始值
        if (searchProgressBar != null) {
            searchProgressBar.setProgress(0);
            searchProgressBar.setVisible(true);
        }
        
        // 使用实时回调进行搜索
        CompletableFuture<SearchResultDTO> searchFuture = articleCrawlService.searchWithUserCookies(
            selectedUser.getUserId(),
            keyword,
            // 笔记实时回调，每获取到一条笔记就更新UI
            noteInfo -> Platform.runLater(() -> {
                viewModel.addNote(noteInfo);
                // 每次添加笔记后都强制保持表格高度
                forceTableHeight();
            }),
            // 日志回调
            message -> appendToLog(message),
            // 进度回调
            (current, total, message) -> Platform.runLater(() -> {
                if (searchProgressBar != null) {
                    double progress = current / (double) total;
                    searchProgressBar.setProgress(progress);
                    
                    // 更新状态文本
                    if (progress < 1.0) {
                        appendToLog(message);
                    }
                }
            })
        );
        
        searchFuture.thenAccept(result -> {
            Platform.runLater(() -> {
                // 隐藏进度条或设置为完成状态
                if (searchProgressBar != null) {
                    searchProgressBar.setProgress(1.0);
                    // 延迟2秒后隐藏进度条
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000);
                            Platform.runLater(() -> searchProgressBar.setVisible(false));
                        } catch (InterruptedException e) {
                            // 忽略中断异常
                        }
                    }).start();
                }
                
                if (result.getNoteList() == null || result.getNoteList().isEmpty()) {
                    appendToLog("未找到相关笔记");
                } else {
                    appendToLog("搜索完成，共获取到 " + result.getNoteList().size() + " 条笔记");
                }
                
                // 再次强制表格高度
                forceTableHeight();
                
                switch (result.getStatus()) {
                    case SUCCESS:
                        log.info("搜索完成，共获取到 {} 条笔记", result.getNoteList() != null ? result.getNoteList().size() : 0);
                        break;
                    case INTERRUPTED:
                        // 中断不需要提示，因为是用户主动关闭浏览器
                        log.info("搜索被用户中断，不显示提示");
                        break;
                    case LOGIN_EXPIRED:
                        // 登录状态失效，提示用户并刷新用户列表
                        if (selectedUser != null) {
                            AlertUtils.showWarning("登录已失效", 
                                String.format("用户 %s 的登录状态已失效，请重新登录此账号", 
                                selectedUser.getNickname()));
                            
                            // 刷新用户列表，过滤掉失效的用户
                            loadUserData();
                        }
                        break;
                    case FAILED:
                    default:
                        AlertUtils.showError("搜索失败", "无法完成搜索，请检查网络连接或用户登录状态");
                        break;
                }
            });
        });
    }

    /**
     * 设置分页控件
     */
    private void setupPagination() {
        pagination.setPageCount(1);
        pagination.setCurrentPageIndex(0);
        
        // 关键修改：不再使用页面工厂返回表格，而是直接使用索引刷新数据
        pagination.setPageFactory(pageIndex -> {
            // 只刷新数据，不返回任何组件
            refreshPageData(pageIndex);
            // 返回一个空区域，这样Pagination控件不会干扰表格布局
            return new Region();
        });
        
        // 添加页面切换监听器，在页面切换后立即调整表格大小
        pagination.currentPageIndexProperty().addListener((obs, oldVal, newVal) -> {
            // 页面切换后立即强制表格大小
            forceTableHeight();
            
            // 额外延迟处理，解决异步渲染问题
            Platform.runLater(() -> {
                // 第一次延迟调整
                forceTableHeight();
                
                // 再次延迟调整，确保在JavaFX完成渲染后应用
                Platform.runLater(() -> {
                    forceTableHeight();
                });
            });
        });
    }
    
    /**
     * 强制设置表格高度的辅助方法
     */
    private void forceTableHeight() {
        // 设置表格固定高度(API方法)
        notesTableView.setPrefHeight(300);
        notesTableView.setMinHeight(300);
        
        // 设置表格样式(CSS方法)
        notesTableView.setStyle(
            "-fx-pref-height: 300px; " + 
            "-fx-min-height: 300px; " + 
            "-fx-max-height: 300px; " + 
            "-fx-fixed-cell-size: 40px;"
        );
        
        // 刷新表格布局
        notesTableView.requestLayout();
        
        // 获取表格的所有父容器并强制布局刷新
        javafx.scene.Parent parent = notesTableView.getParent();
        while (parent != null) {
            // 对Region类型的容器调用requestLayout
            if (parent instanceof Region) {
                ((Region) parent).requestLayout();
            }
            
            // 特别处理BorderPane中心区域
            if (parent instanceof BorderPane) {
                BorderPane borderPane = (BorderPane) parent;
                if (borderPane.getCenter() != null && borderPane.getCenter() instanceof Region) {
                    ((Region) borderPane.getCenter()).requestLayout();
                }
            }
            
            parent = parent.getParent();
        }
    }

    /**
     * 返回用户管理界面按钮点击事件
     */
    @FXML
    private void onBackToUserManagement() {
        // 此方法通过FXML直接调用MainController中的onSwitchToUserManagement
    }

    /**
     * 刷新用户列表按钮点击事件
     */
    @FXML
    private void onRefreshUserList() {
        appendToLog("刷新用户列表");
        loadUserData();
    }
    
    /**
     * 清空搜索结果按钮点击事件
     */
    @FXML
    private void onClearResults() {
        appendToLog("清空搜索结果");
        
        // 先重置UI状态
        Platform.runLater(() -> {
            pagination.setPageFactory(null);
            pagination.setPageCount(1);
            pagination.setCurrentPageIndex(0);
            currentPageData.clear();
            
            // 然后清空数据模型
            viewModel.clearNotes();
            
            // 强制设置表格高度，即使没有数据
            forceTableHeight();
            
            // 最后重新设置页面工厂
            pagination.setPageFactory(pageIndex -> {
                refreshPageData(pageIndex);
                return notesTableView;
            });
        });
    }

    /**
     * 刷新指定页的数据
     */
    private void refreshPageData(int pageIndex) {
        int fromIndex = pageIndex * ROWS_PER_PAGE;
        int toIndex = Math.min(fromIndex + ROWS_PER_PAGE, viewModel.getNotes().size());
        
        log.info("刷新第{}页数据，范围: {} - {}, 总数据量: {}", 
            pageIndex + 1, fromIndex, toIndex, viewModel.getNotes().size());
        
        if (fromIndex >= viewModel.getNotes().size()) {
            currentPageData.clear();
            log.warn("页码超出范围，清空数据显示");
        } else {
            List<NoteInfo> pageItems = viewModel.getNotes().subList(fromIndex, toIndex);
            currentPageData.setAll(pageItems);
            log.info("当前页显示{}条数据", pageItems.size());
            appendToLog("显示第 " + (pageIndex + 1) + " 页数据，共 " + pageItems.size() + " 条");
        }
        
        // 强制设置表格高度，确保不会被压缩
        forceTableHeight();
        
        // 获取当前分页控件所在的父容器
        javafx.scene.Parent parent = pagination.getParent();
        if (parent != null) {
            // 要求父容器重新布局
            parent.requestLayout();
        }
    }

    /**
     * 更新分页控件
     */
    private void updatePagination() {
        int itemCount = viewModel.getNotes().size();
        int pageCount = Math.max(1, (int) Math.ceil((double) itemCount / ROWS_PER_PAGE));
        
        Platform.runLater(() -> {
            // 先暂时移除页面工厂，避免触发不必要的数据加载
            pagination.setPageFactory(null);
            
            // 更新分页总数
            pagination.setPageCount(pageCount);
            
            // 确保当前页索引有效
            int currentIndex = pagination.getCurrentPageIndex();
            if (currentIndex >= pageCount) {
                pagination.setCurrentPageIndex(0);
            }
            
            // 重新设置页面工厂
            pagination.setPageFactory(pageIndex -> {
                refreshPageData(pageIndex);
                return new Region();
            });
            
            // 手动刷新当前页数据
            refreshPageData(pagination.getCurrentPageIndex());
            
            // 强制更新表格高度
            forceTableHeight();
        });
    }
} 