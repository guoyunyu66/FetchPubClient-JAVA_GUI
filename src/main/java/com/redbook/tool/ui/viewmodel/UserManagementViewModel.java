package com.redbook.tool.ui.viewmodel;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.redbook.tool.entity.UserInfo;
import com.redbook.tool.service.LoginService;
import com.redbook.tool.service.UserService;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户管理界面的视图模型，处理用户数据和UI状态
 */
@Slf4j
public class UserManagementViewModel {

    private final UserService userService;
    private final LoginService loginService;

    // UI绑定属性
    @Getter
    private final ObservableList<UserInfo> users = FXCollections.observableArrayList();
    @Getter
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    @Getter
    private final BooleanProperty noUsersFound = new SimpleBooleanProperty(false);
    @Getter
    private final ObjectProperty<UserInfo> selectedUser = new SimpleObjectProperty<>();
    @Getter
    private final BooleanProperty loginInProgress = new SimpleBooleanProperty(false);

    /**
     * 构造函数
     *
     * @param userService 用户服务
     * @param loginService 登录服务
     */
    public UserManagementViewModel(UserService userService, LoginService loginService) {
        this.userService = userService;
        this.loginService = loginService;
    }

    /**
     * 加载所有用户数据
     */
    public void loadUsers() {
        loading.set(true);
        noUsersFound.set(false);
        
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
                if (loadedUsers.isEmpty()) {
                    noUsersFound.set(true);
                } else {
                    users.addAll(loadedUsers);
                    noUsersFound.set(false);
                }
                loading.set(false);
            });
        });
    }

    /**
     * 刷新指定用户的登录状态
     *
     * @param userId 要刷新的用户ID
     * @return CompletableFuture<Boolean> 表示操作是否成功
     */
    public CompletableFuture<Boolean> refreshUserLoginStatus(String userId) {
        loginInProgress.set(true);
        
        return CompletableFuture.supplyAsync(() -> {
            boolean success = loginService.loginAndSaveCookies(userId);
            
            // 更新用户列表
            if (success) {
                try {
                    // 重新加载这个用户的最新信息
                    UserInfo updatedUser = userService.loadUserInfo(userId);
                    
                    // 在JavaFX应用线程上更新UI
                    Platform.runLater(() -> {
                        // 更新列表中的用户信息
                        for (int i = 0; i < users.size(); i++) {
                            if (users.get(i).getUserId().equals(userId)) {
                                users.set(i, updatedUser);
                                break;
                            }
                        }
                    });
                } catch (IOException e) {
                    log.error("刷新用户后更新列表失败: {}", e.getMessage(), e);
                }
            }
            
            // 在JavaFX应用线程上更新UI状态
            Platform.runLater(() -> loginInProgress.set(false));
            return success;
        });
    }

    /**
     * 登录新账号 (旧逻辑，保留以备不时之需)
     *
     * @return CompletableFuture<Boolean> 表示登录是否成功
     */
    public CompletableFuture<Boolean> loginNewAccount() {
        loginInProgress.set(true);
        
        return CompletableFuture.supplyAsync(() -> {
            boolean success = loginService.loginAndSaveCookies(); // 旧方法，会检查现有cookie
            
            // 如果登录成功，刷新用户列表
            if (success) {
                try {
                    List<UserInfo> refreshedUsers = userService.loadAllUsers();
                    
                    // 在JavaFX应用线程上更新UI
                    Platform.runLater(() -> {
                        users.clear();
                        users.addAll(refreshedUsers);
                        noUsersFound.set(refreshedUsers.isEmpty());
                    });
                } catch (IOException e) {
                    log.error("登录后刷新用户列表失败: {}", e.getMessage(), e);
                }
            }
            
            // 在JavaFX应用线程上更新UI状态
            Platform.runLater(() -> loginInProgress.set(false));
            return success;
        });
    }

    /**
     * 执行全新的扫码登录流程
     *
     * @return CompletableFuture<Boolean> 表示扫码登录是否成功
     */
    public CompletableFuture<Boolean> performNewLogin() {
        loginInProgress.set(true);
        
        return CompletableFuture.supplyAsync(() -> {
            // 直接调用新的只扫码登录方法
            boolean success = loginService.performNewScanLogin();
            
            // 如果登录成功，刷新用户列表
            if (success) {
                try {
                    List<UserInfo> refreshedUsers = userService.loadAllUsers();
                    
                    // 在JavaFX应用线程上更新UI
                    Platform.runLater(() -> {
                        users.clear();
                        users.addAll(refreshedUsers);
                        noUsersFound.set(refreshedUsers.isEmpty());
                    });
                } catch (IOException e) {
                    log.error("新账号登录后刷新用户列表失败: {}", e.getMessage(), e);
                }
            }
            
            // 在JavaFX应用线程上更新UI状态
            Platform.runLater(() -> loginInProgress.set(false));
            return success;
        });
    }
    
    /**
     * 删除指定用户
     *
     * @param userId 要删除的用户ID
     * @return 是否删除成功
     */
    public boolean deleteUser(String userId) {
        boolean success = userService.deleteUserInfo(userId);
        if (success) {
            // 确保在JavaFX应用线程上更新UI
            Platform.runLater(() -> {
                // 从列表中移除
                users.removeIf(user -> user.getUserId().equals(userId));
                // 如果删除后列表为空，显示空状态
                noUsersFound.set(users.isEmpty());
            });
        }
        return success;
    }
} 