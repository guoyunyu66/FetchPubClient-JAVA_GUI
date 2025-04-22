package com.redbook.tool.ui.controller;

import org.springframework.stereotype.Component;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;

/**
 * 主界面控制器，负责管理菜单切换和视图加载
 */
@Slf4j
@Component
public class MainController {

    @FXML
    private BorderPane mainContainer;
    
    @FXML
    private VBox sidebarMenu;
    
    @FXML
    private StackPane contentArea;
    
    @FXML
    private Button userManagementBtn;
    
    @FXML
    private Button articleCrawlBtn;
    
    @FXML
    private Button publishBtn;
    
    @FXML
    private ScrollPane userManagementView;
    
    @FXML
    private ScrollPane articleCrawlView;
    
    @FXML
    private ScrollPane publishView;
    
    /**
     * FXML初始化方法
     */
    @FXML
    public void initialize() {
        log.info("主界面控制器初始化");
        
        // 默认显示用户管理页面
        setActiveButton(userManagementBtn);
        userManagementView.setVisible(true);
        articleCrawlView.setVisible(false);
        publishView.setVisible(false);
    }
    
    /**
     * 切换到用户管理页面
     */
    @FXML
    public void onSwitchToUserManagement() {
        log.info("切换到用户管理页面");
        setActiveButton(userManagementBtn);
        
        userManagementView.setVisible(true);
        articleCrawlView.setVisible(false);
        publishView.setVisible(false);
    }
    
    /**
     * 切换到文章爬取页面
     */
    @FXML
    public void onSwitchToArticleCrawl() {
        log.info("切换到文章爬取页面");
        setActiveButton(articleCrawlBtn);
        
        userManagementView.setVisible(false);
        articleCrawlView.setVisible(true);
        publishView.setVisible(false);
    }
    
    /**
     * 切换到一键发布页面
     */
    @FXML
    public void onSwitchToPublish() {
        log.info("切换到一键发布页面");
        setActiveButton(publishBtn);
        
        userManagementView.setVisible(false);
        articleCrawlView.setVisible(false);
        publishView.setVisible(true);
    }
    
    /**
     * 设置当前活动按钮样式
     *
     * @param activeButton 当前活动的按钮
     */
    private void setActiveButton(Button activeButton) {
        // 移除所有按钮的active样式
        userManagementBtn.getStyleClass().remove("active-menu-item");
        articleCrawlBtn.getStyleClass().remove("active-menu-item");
        publishBtn.getStyleClass().remove("active-menu-item");
        
        // 给当前按钮添加active样式
        if (!activeButton.getStyleClass().contains("active-menu-item")) {
            activeButton.getStyleClass().add("active-menu-item");
        }
    }
} 