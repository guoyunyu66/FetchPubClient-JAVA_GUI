package com.redbook.tool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.redbook.tool.ui.controller.ArticleCrawlController;
import com.redbook.tool.ui.controller.MainController;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * 小红书笔记获取工具应用主入口
 */
@SpringBootApplication
public class RedBookNoteFetchToolApplication extends Application {
    
    private ConfigurableApplicationContext springContext;
    
    public static void main(String[] args) {
        launch(args);
    }
    
    @Override
    public void init() {
        // 初始化Spring上下文
        springContext = SpringApplication.run(RedBookNoteFetchToolApplication.class);
    }
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        try {
            // 尝试加载主界面
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-layout.fxml"));
            loader.setControllerFactory(springContext::getBean);
            
            Parent root = loader.load();
            Scene scene = new Scene(root, 1000, 700);
            
            // 加载CSS样式
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            
            primaryStage.setTitle("小红书笔记获取工具");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);
            primaryStage.show();
            
            // 获取主控制器
            MainController mainController = loader.getController();
            
            // 设置HostServices到ArticleCrawlController
            ArticleCrawlController articleCrawlController = springContext.getBean(ArticleCrawlController.class);
            articleCrawlController.setHostServices(getHostServices());
            
            System.out.println("主界面加载成功！");
        } catch (Exception e) {
            // 如果加载失败，显示错误并回退到简单界面
            e.printStackTrace();
            System.out.println("主界面加载失败，回退到简单界面: " + e.getMessage());
            
            // 加载简单测试页面
            FXMLLoader simpleLoader = new FXMLLoader(getClass().getResource("/fxml/simple-test.fxml"));
            Parent simpleRoot = simpleLoader.load();
            Scene simpleScene = new Scene(simpleRoot, 800, 600);
            
            // 加载CSS样式
            simpleScene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            
            primaryStage.setTitle("小红书笔记获取工具 - 测试界面");
            primaryStage.setScene(simpleScene);
            primaryStage.setMinWidth(650);
            primaryStage.setMinHeight(500);
            primaryStage.show();
        }
    }
    
    @Override
    public void stop() {
        // 关闭Spring上下文
        springContext.close();
    }
}
