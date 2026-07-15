# 实验四：传输协议与套接字应用编程

本项目使用 Java 原生 Socket 实现一个简易 HTTP 服务器、HTTP 客户端和压力测试程序。

## 运行方式

在项目根目录执行：

```powershell
javac -encoding UTF-8 -d out src/lab4/*.java
java -cp out lab4.SimpleHttpServer
```

浏览器访问：

```text
http://localhost:8080
```

另开一个终端运行客户端测试：

```powershell
java -cp out lab4.HttpClientDemo
```

运行压力测试：

```powershell
java -cp out lab4.StressTester localhost 8080 100 20
```

参数含义依次是：服务器地址、端口、并发客户端数量、每个客户端请求次数。

## 已实现功能

- 多线程服务器：服务器使用固定大小线程池处理客户端连接，支持多客户端同时访问。
- GET 请求：可访问首页、CSS、JS、图片和 JSON 接口。
- HEAD 请求：返回响应头，不返回响应体。
- POST 请求：`/login` 接收表单数据并保存用户名。
- 静态资源：`www` 目录包含 HTML、CSS、JS、SVG 图片。
- Cookie 会话：服务器通过 `LAB4_SESSION` 保存并传递会话状态。
- HTTP 客户端：`HttpClientDemo` 会依次发送 GET、HEAD、POST、GET 请求并展示结果。
- 压力测试：`StressTester` 可测试并发请求成功数、失败数和吞吐量。
