package com.redbook.tool.ui.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.redbook.tool.entity.UserInfo;
import com.redbook.tool.service.LoginService;
import com.redbook.tool.service.UserService;
import com.redbook.tool.ui.util.AlertUtils;
import com.redbook.tool.ui.util.ImageUtils;
import com.redbook.tool.ui.viewmodel.UserManagementViewModel;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.TextAlignment;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户管理界面控制器
 */
@Slf4j
@Component
public class UserManagementController {

    @FXML
    private VBox contentRoot;
    
    @FXML
    private TableView<UserInfo> userTableView;
    
    @FXML
    private TableColumn<UserInfo, String> userIdColumn;
    
    @FXML
    private TableColumn<UserInfo, String> nicknameColumn;
    
    @FXML
    private TableColumn<UserInfo, String> redIdColumn;
    
    @FXML
    private TableColumn<UserInfo, Boolean> statusColumn;
    
    @FXML
    private TableColumn<UserInfo, String> lastLoginColumn;
    
    @FXML
    private TableColumn<UserInfo, Void> actionsColumn;
    
    @FXML
    private Pagination pagination;
    
    @FXML
    private VBox emptyStateBox;
    
    @FXML
    private ProgressIndicator loadingIndicator;
    
    @FXML
    private Button loginNewAccountButton;
    
    private final UserService userService;
    private final LoginService loginService;
    private UserManagementViewModel viewModel;
    
    // 每页显示的行数
    private static final int ROWS_PER_PAGE = 10;
    
    // 存储每行的续期按钮，便于后续更新状态
    private final Map<String, Button> renewButtons = new HashMap<>();

    @Autowired
    public UserManagementController(UserService userService, LoginService loginService) {
        this.userService = userService;
        this.loginService = loginService;
    }

    /**
     * FXML初始化方法
     */
    @FXML
    public void initialize() {
        this.viewModel = new UserManagementViewModel(userService, loginService);
        
        // 确保用户ID列不可见
        userIdColumn.setVisible(false);
        
        // 清理旧缓存
        cleanupOldAvatarCache();
        
        // 初始化表格列
        setupTableColumns();
        
        // 绑定UI状态
        bindUIState();
        
        // 设置分页
        setupPagination();
        
        // 加载用户数据
        viewModel.loadUsers();
        
        // 设置登录按钮状态
        loginNewAccountButton.disableProperty().bind(viewModel.getLoginInProgress());
        
        // 为登录按钮添加事件处理器
        loginNewAccountButton.setOnAction(event -> onLoginNewAccount());
    }
    
    /**
     * 清理旧的头像缓存文件
     * 删除格式错误的旧文件以便重新下载
     */
    private void cleanupOldAvatarCache() {
        try {
            Path cacheDir = Paths.get("cache", "avatars");
            if (!Files.exists(cacheDir)) {
                return;
            }
            
            // 遍历缓存目录
            File[] cacheFiles = cacheDir.toFile().listFiles();
            if (cacheFiles != null) {
                int count = 0;
                for (File file : cacheFiles) {
                    // 只检查JPG文件是否实际为WebP格式
                    if (file.isFile() && file.getName().endsWith(".jpg")) {
                        // 检查是否为WebP格式
                        if (checkIfWebP(file)) {
                            file.delete();
                            count++;
                        }
                    }
                }
                // if (count > 0) {
                //     log.info("已清理 {} 个错误格式的缓存文件", count);
                // }
            }
        } catch (Exception e) {
            log.error("清理头像缓存失败: {}", e.getMessage());
        }
    }
    
    /**
     * 检查文件是否为WebP格式
     * WebP文件开头通常有"RIFF"标记和"WEBP"标识
     */
    private boolean checkIfWebP(File file) {
        if (!file.exists() || file.length() < 12) {
            return false;
        }
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[12];
            if (fis.read(header) == 12) {
                // 检查WebP格式头部标记
                String headerStr = new String(header, 0, 4, StandardCharsets.US_ASCII);
                String formatStr = new String(header, 8, 4, StandardCharsets.US_ASCII);
                
                return "RIFF".equals(headerStr) && "WEBP".equals(formatStr);
            }
        } catch (Exception ignored) {}
        
        return false;
    }
    
    /**
     * 设置表格列
     */
    private void setupTableColumns() {
        // 确保头像缓存目录存在
        createAvatarCacheDir();
        
        // 设置列值工厂
        userIdColumn.setCellValueFactory(new PropertyValueFactory<>("userId"));
        nicknameColumn.setCellValueFactory(new PropertyValueFactory<>("nickname"));
        redIdColumn.setCellValueFactory(new PropertyValueFactory<>("redId"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("active"));
        lastLoginColumn.setCellValueFactory(cellData -> {
            UserInfo user = cellData.getValue();
            if (user.getLastLoginTime() != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                return Bindings.createStringBinding(() -> user.getLastLoginTime().format(formatter));
            }
            return Bindings.createStringBinding(() -> "未知");
        });
        
        // 设置表头居中
        setHeaderCentered(userIdColumn);
        setHeaderCentered(nicknameColumn);
        setHeaderCentered(redIdColumn);
        setHeaderCentered(statusColumn);
        setHeaderCentered(lastLoginColumn);
        setHeaderCentered(actionsColumn);
        
        // 调整列宽
        nicknameColumn.setPrefWidth(220);
        redIdColumn.setPrefWidth(170);
        statusColumn.setPrefWidth(60);
        lastLoginColumn.setPrefWidth(180);
        actionsColumn.setPrefWidth(170);
        
        // 自定义昵称列渲染 - 添加头像图标
        nicknameColumn.setCellFactory(column -> new TableCell<>() {
            private final HBox container = new HBox();
            private final StackPane avatarPane = new StackPane();
            private final FontIcon userIcon = new FontIcon("fas-user");
            private final ImageView avatarView = new ImageView();
            private final Label nameLabel = new Label();
            
            {
                // 设置头像样式
                avatarPane.getStyleClass().add("user-avatar");
                avatarPane.getChildren().add(userIcon);
                
                // 设置头像图片样式
                avatarView.setFitWidth(32);
                avatarView.setFitHeight(32);
                avatarView.setPreserveRatio(true);
                
                // 设置名称样式
                nameLabel.getStyleClass().add("user-name");
                
                // 设置容器样式和布局
                container.getStyleClass().add("user-info-container");
                container.getChildren().addAll(avatarPane, nameLabel);
                container.setAlignment(Pos.CENTER_LEFT);
            }
            
            @Override
            protected void updateItem(String nickname, boolean empty) {
                super.updateItem(nickname, empty);
                
                if (empty || nickname == null) {
                    setGraphic(null);
                } else {
                    UserInfo user = getTableView().getItems().get(getIndex());
                    
                    // 设置用户名
                    nameLabel.setText(nickname);
                    
                    // 清除之前的头像显示
                    avatarPane.getChildren().clear();
                    
                    // 如果有头像URL，加载真实头像
                    if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                        // 先尝试从本地缓存加载
                        File localAvatarFile = findActualAvatarFile(user.getUserId());
                        
                        if (localAvatarFile != null && localAvatarFile.exists()) {
                            // log.info("检查本地头像缓存: {}, 文件是否存在: {}",
                            //         localAvatarFile.getAbsolutePath(), localAvatarFile.exists());
                            loadAvatarFromFile(avatarPane, avatarView, userIcon, localAvatarFile);
                        } else {
                            // 本地没有，异步下载然后显示
                            userIcon.setIconLiteral("fas-spinner");
                            avatarPane.getChildren().setAll(userIcon);
                            
                            downloadAndCacheAvatar(user.getUserId(), user.getAvatar())
                                .thenAccept(success -> {
                                    if (success) {
                                        // log.info("头像下载成功，准备显示");
                                        // 下载成功，从本地加载头像
                                        Platform.runLater(() -> {
                                            try {
                                                // 获取实际保存的文件路径（考虑到可能是webp格式）
                                                File actualFile = findActualAvatarFile(user.getUserId());
                                                
                                                // 重新检查文件是否存在
                                                if (actualFile != null && actualFile.exists()) {
                                                    // log.info("找到实际下载的头像文件: {}", actualFile.getAbsolutePath());
                                                    loadAvatarFromFile(avatarPane, avatarView, userIcon, actualFile);
                                                } else {
                                                    log.error("下载完成但文件不存在");
                                                    userIcon.setIconLiteral("fas-user-circle");
                                                    avatarPane.getChildren().setAll(userIcon);
                                                }
                                            } catch (Exception e) {
                                                log.error("下载后加载头像时发生异常: {}", e.getMessage(), e);
                                                userIcon.setIconLiteral("fas-user-circle");
                                                avatarPane.getChildren().setAll(userIcon);
                                            }
                                        });
                                    } else {
                                        // 下载失败，使用默认图标
                                        Platform.runLater(() -> {
                                            userIcon.setIconLiteral("fas-user-circle");
                                            avatarPane.getChildren().setAll(userIcon);
                                        });
                                    }
                                });
                        }
                    } else {
                        // 没有头像URL，使用默认图标
                        userIcon.setIconLiteral("fas-user");
                        avatarPane.getChildren().add(userIcon);
                    }
                    
                    setGraphic(container);
                }
            }
            
            /**
             * 从本地文件添加头像
             */
            private void loadAvatarFromFile(StackPane avatarPane, ImageView avatarView, FontIcon userIcon, File localAvatarFile) {
                try {
                    // 使用ImageUtils加载图像(支持WebP格式)
                    Image image = ImageUtils.loadImage(localAvatarFile);
                    
                    if (image != null && !image.isError() && image.getWidth() > 0) {
                        avatarView.setImage(image);
                        applyCircleClip(avatarView);
                        avatarPane.getChildren().setAll(avatarView);
                    } else {
                        // 图像加载失败，使用默认图标
                        userIcon.setIconLiteral("fas-user-circle");
                        avatarPane.getChildren().setAll(userIcon);
                        
                        // 检查是否WebP文件但加载失败
                        boolean isWebP = checkIfWebP(localAvatarFile);
                        if (isWebP && localAvatarFile.exists()) {
                            handleFailedWebpImage(avatarPane, avatarView, userIcon, localAvatarFile);
                        }
                    }
                } catch (Exception e) {
                    log.error("加载头像失败: {}", e.getMessage());
                    userIcon.setIconLiteral("fas-user-circle");
                    avatarPane.getChildren().setAll(userIcon);
                }
            }
            
            /**
             * 处理加载失败的WebP图像
             */
            private void handleFailedWebpImage(StackPane avatarPane, ImageView avatarView, FontIcon userIcon, File localAvatarFile) {
                // 删除缓存文件，准备重新下载
                localAvatarFile.delete();
                
                // 从文件名中提取用户ID
                String userId = extractUserIdFromFilename(localAvatarFile);
                if (userId == null) return;
                
                // 重新获取用户信息
                UserInfo user = userService.getUserById(userId);
                if (user == null || user.getAvatar() == null || user.getAvatar().isEmpty()) return;
                
                // 显示加载图标并重新下载
                userIcon.setIconLiteral("fas-spinner");
                avatarPane.getChildren().setAll(userIcon);
                
                downloadAndCacheAvatar(user.getUserId(), user.getAvatar())
                    .thenAccept(success -> {
                        if (success) {
                            Platform.runLater(() -> {
                                // 获取实际保存的文件路径
                                File actualFile = findActualAvatarFile(user.getUserId());
                                if (actualFile != null && actualFile.exists()) {
                                    loadAvatarFromFile(avatarPane, avatarView, userIcon, actualFile);
                                }
                            });
                        }
                    });
            }
            
            /**
             * 从文件名中提取用户ID
             */
            private String extractUserIdFromFilename(File file) {
                try {
                    String fileName = file.getName();
                    if (fileName.startsWith("avatar_") && fileName.contains(".")) {
                        return fileName.substring(7, fileName.lastIndexOf('.'));
                    }
                } catch (Exception e) {
                    // log.debug("从文件名提取用户ID失败: {}", e.getMessage());
                }
                return null;
            }
            
            /**
             * 应用圆形裁剪效果到ImageView
             */
            private void applyCircleClip(ImageView imageView) {
                try {
                    // 创建圆形裁剪效果
                    double radius = 16; // 半径为头像宽度的一半
                    Circle clip = new Circle(radius, radius, radius);
                    imageView.setClip(clip);
                    
                    // 添加阴影效果
                    DropShadow dropShadow = new DropShadow();
                    dropShadow.setRadius(3.0);
                    dropShadow.setOffsetX(0);
                    dropShadow.setOffsetY(1.0);
                    dropShadow.setColor(Color.color(0, 0, 0, 0.2));
                    
                    if (imageView.getParent() instanceof StackPane) {
                        ((StackPane) imageView.getParent()).setEffect(dropShadow);
                    }
                } catch (Exception e) {
                    // log.error("应用圆形裁剪效果失败: {}", e.getMessage()); // 错误日志通常需要保留
                }
            }
            
            /**
             * 查找用户实际的头像文件
             */
            private File findActualAvatarFile(String userId) {
                // 按优先级检查不同格式的文件
                String[] extensions = {"webp", "jpg", "png", "gif"};
                
                for (String ext : extensions) {
                    File file = new File(getAvatarCachePath(userId, ext));
                    if (file.exists()) return file;
                }
                
                return null;
            }
        });
        
        // 设置状态列
        setupStatusColumn();
        
        // 设置操作列
        setupActionsColumn();
    }
    
    /**
     * 设置表头居中
     */
    private void setHeaderCentered(TableColumn<?, ?> column) {
        // 添加样式类
        column.getStyleClass().add("column-header-centered");
        
        // 直接设置样式
        column.setStyle("-fx-alignment: CENTER;");
        
        // 通过监听器确保表格完全加载后设置标签居中
        Platform.runLater(() -> {
            try {
                // 先获取当前列的标签
                Label label = column.getTableView().lookup(".column-header-centered .label") != null ? 
                              (Label) column.getTableView().lookup(".column-header-centered .label") : 
                              createCenteredLabel(column.getText());
                
                // 确保标签居中
                if (label != null) {
                    label.setAlignment(Pos.CENTER);
                    label.setTextAlignment(TextAlignment.CENTER);
                    label.setMaxWidth(Double.MAX_VALUE);
                    label.setStyle("-fx-alignment: CENTER; -fx-text-alignment: center;");
                }
                
                // 遍历所有表头以保证全部居中
                for (Node headerNode : userTableView.lookupAll(".column-header")) {
                    if (headerNode instanceof Pane) {
                        Pane headerPane = (Pane) headerNode;
                        for (Node node : headerPane.getChildren()) {
                            if (node instanceof Label) {
                                Label headerLabel = (Label) node;
                                headerLabel.setAlignment(Pos.CENTER);
                                headerLabel.setTextAlignment(TextAlignment.CENTER);
                                headerLabel.setMaxWidth(Double.MAX_VALUE);
                                headerLabel.setStyle("-fx-alignment: CENTER; -fx-text-alignment: center;");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("设置表头居中时发生异常: {}", e.getMessage());
            }
        });
    }
    
    /**
     * 创建居中标签
     */
    private Label createCenteredLabel(String text) {
        Label label = new Label(text);
        label.setAlignment(Pos.CENTER);
        label.setTextAlignment(TextAlignment.CENTER);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }
    
    /**
     * 设置操作列
     */
    private void setupActionsColumn() {
        actionsColumn.setCellFactory(column -> new TableCell<>() {
            private final Button renewButton = new Button("续期");
            private final Button deleteButton = new Button("删除");
            private final HBox buttonBox = new HBox(10, renewButton, deleteButton);
            
            {
                renewButton.getStyleClass().addAll("table-action-button", "btn-secondary");
                deleteButton.getStyleClass().addAll("table-action-button", "btn-danger");
                
                // 添加图标到按钮
                renewButton.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("fas-sync"));
                deleteButton.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("fas-trash-alt"));
                
                // 设置按钮容器居中
                buttonBox.setAlignment(Pos.CENTER);
                
                renewButton.setOnAction(event -> {
                    UserInfo user = getTableView().getItems().get(getIndex());
                    onRenewUser(user);
                });
                
                deleteButton.setOnAction(event -> {
                    UserInfo user = getTableView().getItems().get(getIndex());
                    onDeleteUser(user);
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                
                if (empty) {
                    setGraphic(null);
                } else {
                    UserInfo user = getTableView().getItems().get(getIndex());
                    renewButton.setDisable(viewModel.getLoginInProgress().get());
                    
                    // 存储续期按钮用于后续更新状态
                    renewButtons.put(user.getUserId(), renewButton);
                    
                    setGraphic(buttonBox);
                    
                    // 确保单元格内容居中
                    setAlignment(Pos.CENTER);
                }
            }
        });
    }
    
    /**
     * 绑定UI状态
     */
    private void bindUIState() {
        // 绑定加载状态
        loadingIndicator.visibleProperty().bind(viewModel.getLoading());
        
        // 绑定空状态
        emptyStateBox.visibleProperty().bind(
            Bindings.and(
                viewModel.getNoUsersFound(),
                viewModel.getLoading().not()
            )
        );
        
        // 绑定表格可见性
        userTableView.visibleProperty().bind(
            Bindings.and(
                viewModel.getNoUsersFound().not(),
                viewModel.getLoading().not()
            )
        );
        
        // 绑定分页可见性
        pagination.visibleProperty().bind(userTableView.visibleProperty());
    }
    
    /**
     * 设置分页
     */
    private void setupPagination() {
        pagination.setPageCount(calculatePageCount());
        pagination.setCurrentPageIndex(0);
        
        // 页面切换事件
        pagination.currentPageIndexProperty().addListener((obs, oldIndex, newIndex) -> {
            updateTableViewItems(newIndex.intValue());
        });
        
        // 监听用户列表变化，更新分页
        viewModel.getUsers().addListener((javafx.collections.ListChangeListener.Change<? extends UserInfo> c) -> {
            pagination.setPageCount(calculatePageCount());
            updateTableViewItems(pagination.getCurrentPageIndex());
        });
    }
    
    /**
     * 计算页数
     */
    private int calculatePageCount() {
        int itemCount = viewModel.getUsers().size();
        return (itemCount + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE;
    }
    
    /**
     * 更新表格数据
     */
    private void updateTableViewItems(int pageIndex) {
        int fromIndex = pageIndex * ROWS_PER_PAGE;
        int toIndex = Math.min(fromIndex + ROWS_PER_PAGE, viewModel.getUsers().size());
        
        // 防止索引越界
        if (fromIndex >= viewModel.getUsers().size()) {
            userTableView.getItems().clear();
            return;
        }
        
        // 更新表格数据
        userTableView.getItems().setAll(
            viewModel.getUsers().subList(fromIndex, toIndex)
        );
    }
    
    /**
     * 续期用户账号
     */
    private void onRenewUser(UserInfo user) {
        if (user == null || viewModel.getLoginInProgress().get()) {
            return;
        }
        
        log.info("开始续期用户: {}", user.getNickname());
        
        // 禁用续期按钮，防止重复点击
        Button renewButton = renewButtons.get(user.getUserId());
        if (renewButton != null) {
            renewButton.setDisable(true);
            renewButton.setText("续期中...");
            
            // 刷新表格显示状态点
            userTableView.refresh();
        }
        
        // 执行续期操作
        viewModel.refreshUserLoginStatus(user.getUserId())
            .thenAccept(success -> {
                Platform.runLater(() -> {
                    if (success) {
                        AlertUtils.showInfo("续期成功", 
                            String.format("用户 %s 登录状态已成功刷新", user.getNickname()));
                    } else {
                        AlertUtils.showError("续期失败", 
                            "登录失败，请检查网络连接或稍后再试");
                        
                        // 恢复按钮状态
                        if (renewButton != null) {
                            renewButton.setDisable(false);
                            renewButton.setText("续期");
                        }
                    }
                    
                    // 刷新表格以更新状态点
                    userTableView.refresh();
                });
            });
    }
    
    /**
     * 删除用户
     */
    private void onDeleteUser(UserInfo user) {
        if (user == null) {
            return;
        }
        
        boolean confirmed = AlertUtils.showConfirmation(
            "删除确认", 
            String.format("确定要删除用户 %s 吗？此操作不可恢复。", user.getNickname())
        );
        
        if (confirmed) {
            boolean success = viewModel.deleteUser(user.getUserId());
            if (success) {
                AlertUtils.showInfo("删除成功", 
                    String.format("用户 %s 已被删除", user.getNickname()));
            } else {
                AlertUtils.showError("删除失败", 
                    "删除用户时发生错误，请稍后再试");
            }
        }
    }
    
    /**
     * 登录新账号
     */
    @FXML
    private void onLoginNewAccount() {
        if (viewModel.getLoginInProgress().get()) {
            return;
        }
        
        log.info("开始登录新账号");
        loginNewAccountButton.setText("登录中...");
        
        // 调用ViewModel中新的登录方法
        viewModel.performNewLogin()
            .thenAccept(success -> {
                Platform.runLater(() -> {
                    loginNewAccountButton.setText("登录新账号");
                    
                    if (success) {
                        AlertUtils.showInfo("登录成功", "新账号已成功登录并保存");
                    } else {
                        AlertUtils.showError("登录失败", 
                            "登录失败，请检查网络连接或稍后再试");
                    }
                });
            });
    }
    
    /**
     * 刷新用户列表
     */
    @FXML
    private void onRefreshUserList() {
        log.info("开始刷新用户列表");
        
        // 清空续期按钮Map
        renewButtons.clear();
        
        // 清空当前表格数据，避免状态混乱
        userTableView.getItems().clear();
        
        // 重新设置状态列渲染器
        setupStatusColumn();
        
        // 加载用户数据
        viewModel.loadUsers();
    }
    
    /**
     * 单独设置状态列，方便刷新时重置
     */
    private void setupStatusColumn() {
        // 自定义状态列渲染 - 改为圆点指示器
        statusColumn.setCellFactory(column -> new TableCell<>() {
            private final StackPane dotContainer = new StackPane();
            
            {
                // 设置圆点容器
                dotContainer.getStyleClass().add("status-dot");
                
                // 设置容器居中
                HBox container = new HBox(dotContainer);
                container.setAlignment(Pos.CENTER);
                setGraphic(container);
            }
            
            @Override
            protected void updateItem(Boolean active, boolean empty) {
                super.updateItem(active, empty);
                
                // 清除之前的样式类和提示
                dotContainer.getStyleClass().removeAll(
                    "status-active-dot", "status-inactive-dot", "status-pending-dot" // 仍清除pending以防万一
                );
                Tooltip.uninstall(dotContainer, null); // 移除旧提示

                if (empty || active == null) {
                    setGraphic(null);
                } else {
                    setGraphic(dotContainer.getParent()); // 确保父容器可见
                    if (active) {
                        // 已登录状态 - 绿色
                        dotContainer.getStyleClass().add("status-active-dot");
                        Tooltip.install(dotContainer, new Tooltip("已登录"));
                    } else {
                        // 未登录状态 - 红色
                        dotContainer.getStyleClass().add("status-inactive-dot");
                        Tooltip.install(dotContainer, new Tooltip("已失效"));
                    }
                }
            }
        });
        // 确保值工厂设置正确
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("active"));
    }

    /**
     * 创建头像缓存目录
     */
    private void createAvatarCacheDir() {
        try {
            Path cacheDir = Paths.get("cache", "avatars");
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
                // log.info("创建头像缓存目录: {}", cacheDir.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("创建头像缓存目录失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 获取带指定扩展名的头像缓存路径
     */
    private String getAvatarCachePath(String userId, String extension) {
        return Paths.get("cache", "avatars", "avatar_" + userId + "." + extension).toString();
    }

    /**
     * 下载并缓存头像
     */
    private CompletableFuture<Boolean> downloadAndCacheAvatar(String userId, String avatarUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 根据URL判断图片格式
                String extension = determineImageExtension(avatarUrl);
                String cachePath = getAvatarCachePath(userId, extension);
                File outputFile = new File(cachePath);
                
                // 确保父目录存在
                outputFile.getParentFile().mkdirs();
                
                // 设置连接并下载
                URL url = new URL(avatarUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(30000);
                
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    log.error("下载头像失败，HTTP错误码: {}", responseCode);
                    return false;
                }
                
                // 根据内容类型调整文件扩展名
                String contentType = connection.getContentType();
                if (contentType != null) {
                    String newExtension = getExtensionFromContentType(contentType);
                    if (newExtension != null && !extension.equals(newExtension)) {
                        extension = newExtension;
                        cachePath = getAvatarCachePath(userId, extension);
                        outputFile = new File(cachePath);
                        // log.debug("根据内容类型调整为{}格式", extension.toUpperCase());
                    }
                }
                
                // 下载并保存文件
                try (InputStream inputStream = connection.getInputStream();
                     FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                    
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalBytesRead = 0;
                    
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                    }
                    
                    // log.debug("头像下载完成: {}, 大小: {} 字节", outputFile.getName(), totalBytesRead);
                    
                    return outputFile.exists() && outputFile.length() > 0;
                }
            } catch (Exception e) {
                log.error("下载头像失败: {}", e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * 从URL和文件名确定图片扩展名
     */
    private String determineImageExtension(String imageUrl) {
        // 默认扩展名
        String extension = "jpg";
        
        // 检查URL参数
        if (imageUrl.contains("format=webp")) {
            return "webp";
        }
        
        // 从URL路径提取文件名和扩展名
        try {
            URL url = new URL(imageUrl);
            String path = url.getPath();
            if (path.contains(".")) {
                String fileExt = path.substring(path.lastIndexOf('.') + 1);
                if (!fileExt.isEmpty() && fileExt.length() <= 4) {
                    // 只接受合理的扩展名长度
                    return fileExt.toLowerCase();
                }
            }
        } catch (Exception ignored) {}
        
        return extension;
    }
    
    /**
     * 根据内容类型获取文件扩展名
     */
    private String getExtensionFromContentType(String contentType) {
        if (contentType == null) return null;
        
        contentType = contentType.toLowerCase();
        if (contentType.contains("webp")) {
            return "webp";
        } else if (contentType.contains("jpeg") || contentType.contains("jpg")) {
            return "jpg";
        } else if (contentType.contains("png")) {
            return "png";
        } else if (contentType.contains("gif")) {
            return "gif";
        }
        
        return null;
    }
} 