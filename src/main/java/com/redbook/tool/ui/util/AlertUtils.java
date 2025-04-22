package com.redbook.tool.ui.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;

/**
 * 提示对话框工具类
 */
public class AlertUtils {

    /**
     * 显示信息提示框
     *
     * @param title 标题
     * @param message 消息内容
     */
    public static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        
        // 设置样式
        setAlertStyle(alert);
        
        alert.showAndWait();
    }

    /**
     * 显示错误提示框
     *
     * @param title 标题
     * @param message 错误消息
     */
    public static void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        
        // 设置样式
        setAlertStyle(alert);
        
        alert.showAndWait();
    }

    /**
     * 显示警告提示框
     *
     * @param title 标题
     * @param message 警告消息
     */
    public static void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        
        // 设置样式
        setAlertStyle(alert);
        
        alert.showAndWait();
    }

    /**
     * 显示确认对话框
     *
     * @param title 标题
     * @param message 确认消息
     * @return 用户是否点击了确认按钮
     */
    public static boolean showConfirmation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        
        // 设置样式
        setAlertStyle(alert);
        
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
    
    /**
     * 设置Alert对话框样式
     * 
     * @param alert 要设置样式的Alert对话框
     */
    private static void setAlertStyle(Alert alert) {
        
        // 添加应用的CSS样式
        alert.getDialogPane().getStylesheets().add(
            AlertUtils.class.getResource("/css/style.css").toExternalForm()
        );
        
        // 添加样式类
        alert.getDialogPane().getStyleClass().add("custom-alert");
    }
} 