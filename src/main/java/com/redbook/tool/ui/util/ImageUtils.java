package com.redbook.tool.ui.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import javafx.scene.image.Image;
import lombok.extern.slf4j.Slf4j;

/**
 * 图像处理工具类
 */
@Slf4j
public class ImageUtils {

    /**
     * 从任意格式的图像文件加载JavaFX Image
     * 支持包括WebP在内的多种图像格式
     *
     * @param imageFile 图像文件
     * @return JavaFX Image对象
     */
    public static Image loadImage(File imageFile) {
        if (imageFile == null || !imageFile.exists() || imageFile.length() == 0) {
            return null;
        }
        
        try {
            // 检测文件格式
            String fileFormat = detectImageFormat(imageFile);
            
            // 对于WebP格式，直接使用ImageIO处理
            if ("webp".equalsIgnoreCase(fileFormat)) {
                try {
                    BufferedImage bufferedImage = ImageIO.read(imageFile);
                    if (bufferedImage != null) {
                        return convertToFXImage(bufferedImage);
                    }
                } catch (Exception e) {
                    log.debug("WebP加载失败，尝试其他方式");
                }
            } else {
                // 对于标准格式，先尝试JavaFX直接加载
                try {
                    Image image = new Image(new FileInputStream(imageFile), 32, 32, true, true);
                    if (!image.isError()) {
                        return image;
                    }
                } catch (Exception ignored) { }
            }
            
            // 通用加载方式：使用ImageIO
            try {
                BufferedImage bufferedImage = ImageIO.read(imageFile);
                if (bufferedImage != null) {
                    return convertToFXImage(bufferedImage);
                }
            } catch (Exception ignored) { }
            
            // 最后尝试直接读取文件字节
            try {
                byte[] imageData = Files.readAllBytes(imageFile.toPath());
                Image image = new Image(new ByteArrayInputStream(imageData), 32, 32, true, true);
                if (!image.isError()) {
                    return image;
                }
            } catch (Exception ignored) { }
            
        } catch (Exception e) {
            log.error("加载图像过程中发生异常: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 检测图像格式
     */
    private static String detectImageFormat(File imageFile) {
        // 先通过ImageIO检测
        try (ImageInputStream iis = ImageIO.createImageInputStream(imageFile)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                String formatName = reader.getFormatName();
                return formatName;
            }
        } catch (Exception ignored) { }
        
        // 再尝试通过文件扩展名判断
        String fileName = imageFile.getName().toLowerCase();
        if (fileName.endsWith(".webp")) {
            return "webp";
        } else if (fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf('.') + 1);
        }
        
        return "unknown";
    }
    
    /**
     * 将AWT BufferedImage转换为JavaFX Image
     */
    private static Image convertToFXImage(BufferedImage bufferedImage) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // 将BufferedImage写入PNG格式
            ImageIO.write(bufferedImage, "png", outputStream);
            
            // 从字节数组创建JavaFX Image
            byte[] imageData = outputStream.toByteArray();
            return new Image(new ByteArrayInputStream(imageData), 32, 32, true, true);
        }
    }
} 