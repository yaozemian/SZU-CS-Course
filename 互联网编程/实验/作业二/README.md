# 简单HTTP客户端程序
- **学号**：[2024150026]
- **姓名**：[姚泽棉]
- **运行环境**：JDK 21.0.8, Windows

## 程序说明
### 1. HttpClient.java
- 功能：基于 Socket 实现 HTTP GET 请求，下载用户输入 URL 指定的资源并存储到本地磁盘。
- 关键类：`Socket`, `InputStream`, `OutputStream`, `FileOutputStream`, `JFrame`, `JEditorPane`
- 基本功能：解析 URL，建立连接，发送 GET 请求，接收响应，保存文件。
- 提升功能：下载 HTML 后自动解析并下载 `src` / `href` 指向的图片、CSS 等资源；无命令行参数运行时打开图形界面显示下载结果；支持 Basic Auth 身份验证。

### 2. HttpServer1.java
- 功能：本地普通 HTTP 测试服务器，端口为 `8080`。
- 关键类：`ServerSocket`, `Socket`, `BufferedReader`, `OutputStream`, `Files`
- 基本功能：接收浏览器或客户端的 GET 请求，返回 `www` 目录下的静态 HTML、CSS、图片文件。

### 3. HttpServer2.java
- 功能：本地带 Basic Auth 身份验证的 HTTP 测试服务器，端口为 `8081`。
- 关键类：`ServerSocket`, `Socket`, `Base64`, `Files`
- 基本功能：校验 `Authorization` 请求头，验证通过后返回 `auth_www` 目录下的测试页面。
- 测试账号：用户名 `student`，密码 `123456`。

### 4. 提升要求实现情况
- [x] 自动下载图像等资源
- [x] 图形界面显示内容
- [x] 身份验证支持（可配置本地测试服务器）

## 编译与运行
```bash
javac HttpClient.java HttpServer1.java HttpServer2.java
```

启动普通测试服务器：
```bash
java HttpServer1
```

在另一个终端中运行客户端：
```bash
java HttpClient http://localhost:8080/index.html downloads
```

启动身份验证服务器：
```bash
java HttpServer2
```

使用用户名和密码下载身份验证页面：
```bash
java HttpClient http://localhost:8081/index.html auth_downloads student 123456
```

直接运行图形界面：
```bash
java HttpClient
```

## 运行结果
普通页面下载、HTML 内资源自动下载、身份验证下载、图形界面显示等结果见 `screenshots` 文件夹中的截图。
