# 📱 小红书笔记获取工具 (RedBookNoteFetchTool)

<div align="center">
  
![版本](https://img.shields.io/badge/版本-1.0.0-brightgreen.svg)
![JDK](https://img.shields.io/badge/JDK-17+-blue.svg)
![SpringBoot](https://img.shields.io/badge/SpringBoot-3.1.0-green.svg)
![JavaFX](https://img.shields.io/badge/JavaFX-17.0.2-orange.svg)
![License](https://img.shields.io/badge/许可证-MIT-lightgrey.svg)

</div>

## 📋 目录

- [📝 项目介绍](#-项目介绍)
- [✨ 主要功能](#-主要功能)
- [🛠️ 技术栈](#️-技术栈)
- [🗂️ 项目结构](#️-项目结构)
- [🚀 快速开始](#-快速开始)
- [📋 使用指南](#-使用指南)
- [💻 开发指南](#-开发指南)
- [🔧 常见问题](#-常见问题)
- [🤝 贡献](#-贡献)
- [📞 联系方式](#-联系方式)

## 📝 项目介绍

小红书笔记获取工具是一款基于Java+Playwright+JavaFX开发的现代化桌面应用，提供精美的用户界面和强大的自动化能力。本工具专注于高效、稳定地抓取和处理小红书平台上的笔记数据，支持数据管理、分析和多格式导出。

## ✨ 主要功能

| 功能 | 描述 |
|------|------|
| 🔍 **自动化抓取** | 基于Playwright实现的稳定可靠的数据抓取，支持批量操作 |
| 📊 **数据处理** | 智能处理和分析抓取到的笔记数据，提取关键信息 |
| 💾 **本地存储** | 使用嵌入式H2数据库安全存储数据，支持检索和备份 |
| 📁 **导出功能** | 支持Excel、CSV等多种格式数据导出，便于后续分析 |
| 🎨 **美观界面** | 基于JavaFX的现代化UI设计，支持亮/暗主题动态切换 |
| 🔧 **灵活配置** | 提供丰富的自定义选项，满足不同场景需求 |

## 🛠️ 技术栈

<details open>
<summary><b>核心技术</b></summary>

- **Java 17+** - 利用最新Java特性（记录类、密封类、模式匹配等）
- **Playwright Java 1.36.0** - 强大的浏览器自动化库，比Selenium更现代化
- **JavaFX 17.0.2** - Java原生UI框架，构建跨平台桌面应用
- **Spring Boot 3.1.0** - 依赖注入和配置管理，简化应用开发
</details>

<details open>
<summary><b>UI增强</b></summary>

- **AtlantaFX 2.0.1** - 现代Material Design风格JavaFX组件库
- **Ikonli 12.3.1** - 图标库集成，包含FontAwesome5图标包
- **自定义CSS** - 实现毛玻璃效果和主题切换功能
</details>

<details open>
<summary><b>数据处理</b></summary>

- **Jackson 2.15.2** - JSON数据处理库
- **Apache POI 5.2.3** - Excel数据导出工具
- **H2 Database 2.1.214** - 轻量级嵌入式数据库
</details>

<details open>
<summary><b>工具库</b></summary>

- **Lombok 1.18.28** - 减少样板代码，提高开发效率
- **Logback 1.4.8** - 高性能日志系统
- **Apache Commons Lang3 3.12.0** - 通用工具类库
- **Vavr 0.10.4** - 函数式编程增强库
</details>

## 🗂️ 项目结构

```
src/
├── main/
│   ├── java/
│   │   └── com/redbook/tool/
│   │       ├── RedBookNoteFetchToolApplication.java  # 应用入口类
│   │       ├── config/                               # 配置类目录
│   │       │   ├── AppConfig.java                    # 应用全局配置
│   │       │   ├── PlaywrightConfig.java             # 浏览器自动化配置
│   │       │   └── DatabaseConfig.java               # 数据库配置
│   │       ├── repository/                           # 数据访问层
│   │       │   ├── NoteRepository.java               # 笔记数据仓库
│   │       │   └── UserRepository.java               # 用户数据仓库
│   │       ├── service/                              # 业务服务层
│   │       │   ├── crawler/                          # 爬虫服务模块
│   │       │   ├── export/                           # 数据导出服务
│   │       │   └── storage/                          # 存储服务模块
│   │       └── ui/                                   # UI层
│   │           ├── controller/                       # FXML控制器
│   │           ├── dialog/                           # 自定义对话框
│   │           ├── util/                             # UI工具类
│   │           └── viewmodel/                        # 视图模型
│   ├── resources/
│   │   ├── css/                                      # 样式文件
│   │   ├── fxml/                                     # FXML布局文件
│   │   ├── images/                                   # 图片资源
│   │   ├── fonts/                                    # 字体资源
│   │   └── application.properties                    # 应用配置文件
│   └── native/                                       # 原生库(如需)
└── test/                                             # 测试代码目录
    └── java/
        └── com/redbook/tool/                         # 单元测试和集成测试
```

## 🚀 快速开始

### 环境要求

- **JDK 17** 或更高版本
- **Maven 3.6+** 或 **Gradle 7.0+**
- 现代浏览器 (**Chrome/Edge/Firefox**)
- 1GB+ 可用内存

### 构建与运行

#### 1. 克隆仓库

```bash
git clone https://github.com/guoyunyu66/FetchPubClient-JAVA_GUI.git
cd FetchPubClient-JAVA_GUI
```

#### 2. 安装Playwright浏览器

```bash
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install"
```

#### 3. 构建项目

```bash
mvn clean package
```

#### 4. 运行应用

```bash
# 方式1: 通过Maven运行
mvn javafx:run

# 方式2: 直接运行JAR包
java -jar target/RedBookNoteFetchTool-1.0-SNAPSHOT.jar
```

## 📋 使用指南

### 基本使用流程

1. **启动应用** - 双击打包好的应用或通过命令行启动
2. **登录账号** - 首次使用需登录小红书账号（仅用于数据访问）
3. **配置参数** - 在设置页面配置抓取参数和规则
4. **选择目标** - 输入目标笔记URL或作者主页
5. **开始抓取** - 点击"开始"按钮启动自动化抓取过程
6. **管理数据** - 在数据管理页面查看和编辑抓取结果
7. **导出结果** - 将数据导出为Excel或其他格式

### 高级功能

- **批量导入** - 支持从文件导入多个目标URL
- **自定义过滤** - 设置关键词过滤规则
- **定时任务** - 配置定期自动抓取任务
- **数据分析** - 简单的数据统计和可视化

## 💻 开发指南

本项目采用MVVM架构模式，结合Spring Boot的依赖注入机制：

- **Model**: 业务模型和数据结构
- **View**: FXML定义的UI组件和布局
- **ViewModel**: 连接View和Model，处理UI逻辑和数据绑定
- **Service**: 核心业务逻辑，由Spring管理生命周期

### 开发环境设置

```bash
# 1. 确保安装JDK 17和Maven
java -version
mvn -version

# 2. 安装IDE插件(以IntelliJ IDEA为例)
# - JavaFX插件
# - Lombok插件
# - SceneBuilder(可选，用于FXML设计)

# 3. 导入项目
# 使用IDE导入Maven项目
```

### 添加新功能

1. 在相应的包中创建所需的类
2. 使用Spring的`@Service`、`@Component`等注解管理依赖
3. 对于UI组件，创建FXML文件和对应的Controller
4. 使用ViewModel连接UI和业务逻辑
5. 编写单元测试验证功能

### 打包部署

```bash
# 创建可执行JAR包
mvn clean package

# 创建原生安装包(jpackage)
mvn javafx:jlink jpackage:jpackage
```

## 🔧 常见问题

<details>
<summary><b>Q: 应用启动时报Playwright相关错误</b></summary>
A: 首次运行需要安装浏览器，请执行:

```bash
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install"
```
</details>

<details>
<summary><b>Q: 如何修改数据存储位置?</b></summary>
A: 在`application.properties`中修改以下配置:

```properties
app.data.storage.path=D:/your-custom-path/data
```
</details>

<details>
<summary><b>Q: 程序运行缓慢或内存占用高</b></summary>
A: 尝试调整JVM参数:

```bash
java -Xmx1g -XX:+UseG1GC -jar RedBookNoteFetchTool-1.0-SNAPSHOT.jar
```
</details>

<details>
<summary><b>Q: 如何在代码中添加新的抓取规则?</b></summary>
A: 在`service/crawler`包中创建新的抓取服务类，实现`CrawlerService`接口
</details>

## 🤝 贡献

欢迎贡献代码、报告问题或提出新功能建议！请遵循以下步骤：

1. Fork 本仓库
2. 创建您的特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交您的更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 开启一个 Pull Request

## 📞 联系方式

- **项目维护者**: [郭云宇](mailto:guoyunyu@example.com)
- **GitHub仓库**: [https://github.com/guoyunyu66/FetchPubClient-JAVA_GUI](https://github.com/guoyunyu66/FetchPubClient-JAVA_GUI)

---

<div align="center">
  
⭐ 如果您觉得这个项目有帮助，请给它一个星标！ 

</div>
