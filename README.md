# 小红书笔记获取工具 (RedBookNoteFetchTool)

![版本](https://img.shields.io/badge/版本-1.0.0-brightgreen.svg)
![JDK](https://img.shields.io/badge/JDK-17+-blue.svg)
![SpringBoot](https://img.shields.io/badge/SpringBoot-3.1.0-green.svg)
![JavaFX](https://img.shields.io/badge/JavaFX-17.0.2-orange.svg)

## 📝 项目介绍

小红书笔记获取工具是一款基于Java+Playwright+JavaFX的现代化桌面应用，提供精美的用户界面和强大的自动化能力，主要用于抓取和处理小红书平台上的笔记数据。通过本工具，您可以高效地获取、管理和导出小红书笔记内容。

## ✨ 主要功能

- 🔍 **自动化抓取**：基于Playwright实现的稳定可靠的数据抓取
- 📊 **数据处理**：智能处理和分析抓取到的笔记数据
- 💾 **本地存储**：使用嵌入式数据库安全存储数据
- 📁 **导出功能**：支持多种格式数据导出(如Excel)
- 🎨 **美观界面**：基于JavaFX的现代化UI设计，支持亮/暗主题切换

## 🛠️ 技术栈

- **核心技术**
  - Java 17+
  - Playwright Java 1.36.0
  - JavaFX 17.0.2
  - Spring Boot 3.1.0

- **UI增强**
  - AtlantaFX 2.0.1
  - Ikonli (FontAwesome5) 12.3.1

- **数据处理**
  - Jackson 2.15.2
  - Apache POI 5.2.3
  - H2 Database 2.1.214

- **工具库**
  - Lombok 1.18.28
  - Logback 1.4.8
  - Apache Commons Lang3 3.12.0
  - Vavr 0.10.4

## 🗂️ 项目结构

```
src/
├── main/
│   ├── java/
│   │   └── com/redbook/tool/
│   │       ├── RedBookNoteFetchToolApplication.java  # 主入口
│   │       ├── config/                              # 配置类
│   │       ├── repository/                          # 数据访问层
│   │       ├── service/                             # 业务服务层
│   │       │   ├── crawler/                         # 爬虫服务
│   │       │   ├── export/                          # 数据导出服务
│   │       │   └── storage/                         # 存储服务
│   │       └── ui/                                  # UI层
│   │           ├── controller/                      # FXML控制器
│   │           ├── dialog/                          # 自定义对话框
│   │           ├── util/                            # UI工具类
│   │           └── viewmodel/                       # 视图模型
│   ├── resources/
│   │   ├── css/                                     # 样式文件
│   │   ├── fxml/                                    # FXML布局文件
│   │   ├── images/                                  # 图片资源
│   │   ├── fonts/                                   # 字体资源
│   │   └── application.properties                   # 应用配置
│   └── native/                                      # 原生库(如需)
└── test/                                            # 测试代码
```

## 🚀 快速开始

### 环境要求

- JDK 17 或更高版本
- Maven 3.6+ 或 Gradle 7.0+
- 现代浏览器 (Chrome/Edge/Firefox)

### 构建与运行

1. **克隆仓库**

```bash
git clone https://github.com/yourusername/RedBookNoteFetchTool.git
cd RedBookNoteFetchTool
```

2. **构建项目**

```bash
mvn clean package
```

3. **运行应用**

```bash
mvn javafx:run
```

或者

```bash
java -jar target/RedBookNoteFetchTool-1.0-SNAPSHOT.jar
```

## 📋 使用指南

1. **启动应用**：双击打包好的应用或通过命令行启动
2. **配置参数**：在设置页面配置必要的参数
3. **开始抓取**：选择目标内容，开始自动化抓取
4. **查看结果**：在结果页面查看和管理抓取的数据
5. **导出数据**：将结果导出为Excel等格式

## 💻 开发指南

本项目采用MVVM架构模式，结合SpringBoot的依赖注入：

- **Model**: 业务模型和数据结构
- **View**: FXML定义的UI组件和布局
- **ViewModel**: 连接View和Model，处理UI逻辑
- **Service**: 核心业务逻辑，由Spring管理

### 添加新功能

1. 在相应的包中创建所需的类
2. 使用Spring的`@Service`、`@Component`等注解管理依赖
3. 对于UI组件，创建FXML文件和对应的Controller
4. 使用ViewModel连接UI和业务逻辑

## 📄 许可证

[MIT License](LICENSE)

## 🔧 常见问题

**Q: 应用启动时报Playwright相关错误**  
A: 首次运行需要安装浏览器，请执行 `mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install"`

**Q: 如何修改数据存储位置?**  
A: 在`application.properties`中修改`app.data.storage.path`属性

## 🤝 贡献

欢迎贡献代码、报告问题或提出新功能建议！请遵循以下步骤：

1. Fork 本仓库
2. 创建您的特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交您的更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 开启一个 Pull Request

## 📞 联系方式

项目维护者: [维护者姓名](mailto:your-email@example.com)

---

⭐ 如果您觉得这个项目有帮助，请给它一个星标！ 