package com.redbook.tool.ui.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redbook.tool.dto.PublishResultDTO;
import com.redbook.tool.entity.NoteInfo;
import com.redbook.tool.entity.UserInfo;
import com.redbook.tool.service.NoteDetailService;
import com.redbook.tool.service.NotePublishService;
import com.redbook.tool.service.UserService;
import com.redbook.tool.ui.util.AlertUtils;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;

/**
 * 笔记发布界面控制器
 */
@Slf4j
@Component
public class NotePublishController {

    @FXML
    private VBox contentRoot;
    
    @FXML
    private TextField titleField;
    
    @FXML
    private TextArea contentArea;
    
    @FXML
    private FlowPane tagsContainer;
    
    @FXML
    private Button addTagButton;
    
    @FXML
    private FlowPane imagesContainer;
    
    @FXML
    private Label imageCountLabel;
    
    @FXML
    private Button publishButton;
    
    @FXML
    private ProgressBar publishProgressBar;
    
    @FXML
    private ComboBox<UserInfo> userComboBox;
    
    @FXML
    private TextArea logTextArea;
    
    private final NoteDetailService noteDetailService;
    private final NotePublishService notePublishService;
    private final UserService userService;
    private MainController mainController;
    
    // 当前的笔记信息
    private NoteInfo currentNote;
    // 标签列表
    private List<String> tags = new ArrayList<>();
    // 图片URL列表
    private List<String> imageUrls = new ArrayList<>();
    
    @Autowired
    public NotePublishController(NoteDetailService noteDetailService, 
                                NotePublishService notePublishService,
                                UserService userService) {
        this.noteDetailService = noteDetailService;
        this.notePublishService = notePublishService;
        this.userService = userService;
    }
    
    /**
     * 设置主控制器，用于页面切换
     * 
     * @param mainController 主控制器
     */
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }
    
    /**
     * 初始化方法
     */
    @FXML
    public void initialize() {
        log.info("初始化笔记发布控制器");
        
        // 设置用户选择下拉框
        setupUserComboBox();
        
        // 初始化进度条
        if (publishProgressBar != null) {
            publishProgressBar.setProgress(0);
            publishProgressBar.setVisible(false);
        }
        
        // 设置发布按钮点击事件
        if (publishButton != null) {
            publishButton.setOnAction(e -> onPublish());
        }
        
        // 初始化日志文本区域
        if (logTextArea != null) {
            logTextArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");
        }
    }
    
    /**
     * 初始化用户选择下拉框
     */
    private void setupUserComboBox() {
        if (userComboBox == null) return;
        
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
        
        // 加载用户列表
        loadUserList();
    }
    
    /**
     * 加载用户列表
     */
    private void loadUserList() {
        if (userComboBox == null) return;
        
        appendToLog("正在加载用户列表...");
        
        List<UserInfo> users = userService.getAllUsers();
        
        Platform.runLater(() -> {
            userComboBox.getItems().clear();
            for (UserInfo user : users) {
                if (user.isActive()) { // 只添加已登录的用户
                    userComboBox.getItems().add(user);
                }
            }
            
            if (!userComboBox.getItems().isEmpty()) {
                userComboBox.getSelectionModel().selectFirst();
                appendToLog("已加载 " + userComboBox.getItems().size() + " 个用户");
            } else {
                appendToLog("没有已登录的用户，请先登录");
            }
        });
    }
    
    /**
     * 使用NoteInfo加载笔记详情
     * 
     * @param noteInfo 笔记信息对象
     */
    public void loadNoteInfo(NoteInfo noteInfo) {
        if (noteInfo == null) {
            log.warn("尝试加载的笔记信息为空");
            return;
        }
        
        log.info("加载笔记详情: {}", noteInfo.getTitle());
        this.currentNote = noteInfo;
        
        // 更新UI
        Platform.runLater(() -> {
            // 设置标题和内容
            titleField.setText(noteInfo.getTitle());
            contentArea.setText(noteInfo.getContent());
            
            // 加载标签
            loadTags(noteInfo.getTags());
            
            // 加载图片
            loadImages(noteInfo.getImageUrls());
        });
    }
    
    /**
     * 加载标签列表
     * 
     * @param tagList 标签列表
     */
    private void loadTags(List<String> tagList) {
        if (tagList == null) {
            log.warn("标签列表为空");
            return;
        }
        
        // 清空容器和列表
        tagsContainer.getChildren().clear();
        tags.clear();
        
        // 添加标签
        for (String tag : tagList) {
            tags.add(tag);
            addTagToContainer(tag);
        }
        
        log.info("已加载{}个标签", tags.size());
    }
    
    /**
     * 添加标签到界面容器
     * 
     * @param tag 标签文本
     */
    private void addTagToContainer(String tag) {
        // 创建标签显示组件
        HBox tagBox = new HBox();
        tagBox.setAlignment(Pos.CENTER_LEFT);
        tagBox.setSpacing(5);
        tagBox.setPadding(new Insets(5, 10, 5, 10));
        tagBox.getStyleClass().add("tag-box");
        
        // 标签文本
        Label tagLabel = new Label("#" + tag);
        tagLabel.getStyleClass().add("tag-text");
        
        // 编辑按钮
        Button editBtn = new Button();
        editBtn.getStyleClass().addAll("btn-secondary", "tag-btn");
        editBtn.setGraphic(new javafx.scene.text.Text("编辑"));
        editBtn.setOnAction(e -> editTag(tag, tagBox));
        
        // 删除按钮
        Button deleteBtn = new Button();
        deleteBtn.getStyleClass().addAll("btn-danger", "tag-btn");
        deleteBtn.setGraphic(new javafx.scene.text.Text("删除"));
        deleteBtn.setOnAction(e -> removeTag(tag, tagBox));
        
        tagBox.getChildren().addAll(tagLabel, editBtn, deleteBtn);
        tagsContainer.getChildren().add(tagBox);
    }
    
    /**
     * 编辑标签
     * 
     * @param oldTag 原标签文本
     * @param tagBox 标签容器
     */
    private void editTag(String oldTag, HBox tagBox) {
        TextInputDialog dialog = new TextInputDialog(oldTag);
        dialog.setTitle("编辑标签");
        dialog.setHeaderText("请输入新的标签名称");
        dialog.setContentText("标签名称:");
        
        dialog.showAndWait().ifPresent(newTag -> {
            if (!newTag.isEmpty()) {
                // 更新标签列表
                int index = tags.indexOf(oldTag);
                if (index >= 0) {
                    tags.set(index, newTag);
                }
                
                // 更新UI
                Label tagLabel = (Label) tagBox.getChildren().get(0);
                tagLabel.setText("#" + newTag);
                
                log.info("标签 '{}' 已更新为 '{}'", oldTag, newTag);
            }
        });
    }
    
    /**
     * 删除标签
     * 
     * @param tag 标签文本
     * @param tagBox 标签容器
     */
    private void removeTag(String tag, HBox tagBox) {
        // 从列表中删除
        tags.remove(tag);
        
        // 从UI中删除
        tagsContainer.getChildren().remove(tagBox);
        
        log.info("标签 '{}' 已删除", tag);
    }
    
    /**
     * 加载图片列表
     * 
     * @param urls 图片URL列表
     */
    private void loadImages(List<String> urls) {
        if (urls == null) {
            log.warn("图片URL列表为空");
            return;
        }
        
        // 清空容器和列表
        imagesContainer.getChildren().clear();
        imageUrls.clear();
        
        // 添加图片
        for (String url : urls) {
            imageUrls.add(url);
            addImageToContainer(url, imageUrls.size());
        }
        
        // 更新图片计数
        updateImageCount();
        log.info("已加载{}张图片", imageUrls.size());
    }
    
    /**
     * 添加图片到界面容器
     * 
     * @param imageUrl 图片URL
     * @param index 图片索引
     */
    private void addImageToContainer(String imageUrl, int index) {
        // 创建图片容器
        VBox imageBox = new VBox();
        imageBox.setAlignment(Pos.CENTER);
        imageBox.setSpacing(5);
        imageBox.setPadding(new Insets(5));
        imageBox.getStyleClass().add("image-box");
        
        try {
            // 创建图片显示
            ImageView imageView = new ImageView();
            imageView.setFitWidth(150);
            imageView.setFitHeight(150);
            imageView.setPreserveRatio(true);
            
            // 使用占位图
            imageView.setImage(new Image(getClass().getResourceAsStream("/images/image-placeholder.png")));
            
            // 尝试异步加载实际图片
            if (imageUrl != null && !imageUrl.isEmpty()) {
                new Thread(() -> {
                    try {
                        Image image = new Image(imageUrl, true);
                        // 图片加载完成后更新UI
                        if (!image.isError()) {
                            Platform.runLater(() -> imageView.setImage(image));
                        }
                    } catch (Exception e) {
                        log.error("加载图片失败: {}", e.getMessage());
                    }
                }).start();
            }
            
            // 图片标签
            Label imageLabel = new Label("图片 " + index);
            imageLabel.getStyleClass().add("image-label");
            
            // 删除按钮
            Button deleteBtn = new Button("删除");
            deleteBtn.getStyleClass().add("btn-danger");
            deleteBtn.setOnAction(e -> removeImage(imageUrl, imageBox));
            
            // 添加到容器
            imageBox.getChildren().addAll(imageView, imageLabel, deleteBtn);
            imagesContainer.getChildren().add(imageBox);
            
        } catch (Exception e) {
            log.error("添加图片到容器失败: {}", e.getMessage());
        }
    }
    
    /**
     * 删除图片
     * 
     * @param imageUrl 图片URL
     * @param imageBox 图片容器
     */
    private void removeImage(String imageUrl, VBox imageBox) {
        // 从列表中删除
        imageUrls.remove(imageUrl);
        
        // 从UI中删除
        imagesContainer.getChildren().remove(imageBox);
        
        // 更新图片计数
        updateImageCount();
        log.info("图片已删除");
    }
    
    /**
     * 更新图片计数
     */
    private void updateImageCount() {
        imageCountLabel.setText("共" + imageUrls.size() + "张图片");
    }
    
    /**
     * 添加标签按钮点击事件
     */
    @FXML
    private void onAddTag() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("添加标签");
        dialog.setHeaderText("请输入标签名称");
        dialog.setContentText("标签名称:");
        
        dialog.showAndWait().ifPresent(tag -> {
            if (!tag.isEmpty()) {
                // 添加到列表
                tags.add(tag);
                
                // 添加到UI
                addTagToContainer(tag);
                
                log.info("已添加标签: {}", tag);
            }
        });
    }
    
    /**
     * 发布按钮点击事件
     */
    @FXML
    private void onPublish() {
        // 获取当前编辑的数据
        String title = titleField.getText().trim();
        String content = contentArea.getText().trim();
        
        if (title.isEmpty()) {
            AlertUtils.showWarning("标题不能为空", "请输入笔记标题");
            return;
        }
        
        if (content.isEmpty()) {
            AlertUtils.showWarning("内容不能为空", "请输入笔记内容");
            return;
        }
        
        // 检查是否选择了用户
        UserInfo selectedUser = userComboBox != null ? userComboBox.getSelectionModel().getSelectedItem() : null;
        if (selectedUser == null) {
            AlertUtils.showWarning("请选择用户", "请选择要发布笔记的用户账号");
            return;
        }
        
        // 更新当前笔记信息
        updateCurrentNoteInfo(title, content);
        
        // 显示进度条
        if (publishProgressBar != null) {
            publishProgressBar.setProgress(0);
            publishProgressBar.setVisible(true);
        }
        
        // 禁用发布按钮
        publishButton.setDisable(true);
        
        // 清空日志区域
        if (logTextArea != null) {
            logTextArea.clear();
        }
        
        appendToLog("开始发布笔记: " + title);
        
        // 调用发布服务
        notePublishService.publishNote(
            selectedUser.getUserId(),
            currentNote,
            // 日志回调
            message -> appendToLog(message),
            // 进度回调
            (current, total) -> Platform.runLater(() -> {
                if (publishProgressBar != null) {
                    publishProgressBar.setProgress(current / (double) total);
                }
            })
        ).thenAccept(result -> {
            Platform.runLater(() -> {
                // 启用发布按钮
                publishButton.setDisable(false);
                
                // 设置进度条完成
                if (publishProgressBar != null) {
                    publishProgressBar.setProgress(1.0);
                    // 延迟2秒后隐藏进度条
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000);
                            Platform.runLater(() -> publishProgressBar.setVisible(false));
                        } catch (InterruptedException e) {
                            // 忽略中断异常
                        }
                    }).start();
                }
                
                switch (result.getStatus()) {
                    case SUCCESS:
                        AlertUtils.showInfo("发布成功", 
                            "笔记《" + title + "》已成功发布，包含" + tags.size() + "个标签和" + imageUrls.size() + "张图片");
                        log.info("发布笔记成功: {}, 标签: {}, 图片: {}", title, tags.size(), imageUrls.size());
                        
                        // 返回文章爬取页面
                        if (mainController != null) {
                            mainController.onSwitchToArticleCrawl();
                        }
                        break;
                        
                    case LOGIN_EXPIRED:
                        AlertUtils.showWarning("登录已失效", "用户 " + selectedUser.getNickname() + " 的登录状态已失效，请重新登录");
                        appendToLog("用户登录已失效");
                        // 刷新用户列表
                        loadUserList();
                        break;
                        
                    case FAILED:
                    default:
                        AlertUtils.showError("发布失败", "发布笔记失败: " + result.getErrorMessage());
                        appendToLog("发布失败: " + result.getErrorMessage());
                        break;
                }
            });
        });
    }
    
    /**
     * 更新当前笔记信息
     */
    private void updateCurrentNoteInfo(String title, String content) {
        if (currentNote == null) {
            currentNote = new NoteInfo();
        }
        
        // 更新标题和内容
        currentNote.setTitle(title);
        currentNote.setContent(content);
        
        // 更新标签和图片
        currentNote.setTags(new ArrayList<>(tags));
        currentNote.setImageUrls(new ArrayList<>(imageUrls));
    }
    
    /**
     * 添加日志到文本区域
     */
    private void appendToLog(String message) {
        if (logTextArea == null) return;
        
        Platform.runLater(() -> {
            // 添加时间戳
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            String formattedMessage = String.format("[%s] %s\n", timestamp, message);
            
            // 添加消息到文本区域
            logTextArea.appendText(formattedMessage);
            
            // 自动滚动到底部
            logTextArea.setScrollTop(Double.MAX_VALUE);
        });
    }
    
    /**
     * 返回文章爬取按钮点击事件
     */
    @FXML
    private void onBackToArticleCrawl() {
        if (mainController != null) {
            mainController.onSwitchToArticleCrawl();
        }
    }
} 