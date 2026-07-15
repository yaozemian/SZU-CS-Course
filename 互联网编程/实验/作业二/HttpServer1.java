import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.Locale;

public class HttpServer1 {
    private static final int PORT = 8080;
    private static final Path WEB_ROOT = Paths.get("www");

    public static void main(String[] args) throws IOException {
        prepareDemoFiles();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("HttpServer1 已启动: http://localhost:" + PORT + "/index.html");
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handle(socket)).start();
            }
        }
    }

    private static void handle(Socket socket) {
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
             OutputStream out = socket.getOutputStream()) {
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                return;
            }
            String line;
            while ((line = reader.readLine()) != null && !line.isBlank()) {
                // Headers are not required by this simple static server.
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2 || !"GET".equals(parts[0])) {
                sendText(out, 405, "Method Not Allowed", "仅支持 GET 请求");
                return;
            }

            Path file = resolveFile(parts[1]);
            if (file == null || !Files.exists(file) || Files.isDirectory(file)) {
                sendText(out, 404, "Not Found", "文件不存在");
                return;
            }

            byte[] body = Files.readAllBytes(file);
            sendHeader(out, 200, "OK", contentType(file), body.length);
            out.write(body);
        } catch (IOException ex) {
            System.out.println("处理请求失败: " + ex.getMessage());
        }
    }

    private static Path resolveFile(String rawPath) throws IOException {
        String path = rawPath.split("\\?", 2)[0];
        if (path.equals("/")) {
            path = "/index.html";
        }
        Path file = WEB_ROOT.resolve(path.substring(1)).normalize();
        Path root = WEB_ROOT.toAbsolutePath().normalize();
        Path absolute = file.toAbsolutePath().normalize();
        return absolute.startsWith(root) ? file : null;
    }

    private static void sendText(OutputStream out, int status, String reason, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        sendHeader(out, status, reason, "text/plain; charset=utf-8", body.length);
        out.write(body);
    }

    private static void sendHeader(OutputStream out, int status, String reason, String contentType, int length) throws IOException {
        String header = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private static void prepareDemoFiles() throws IOException {
        Files.createDirectories(WEB_ROOT);
        Path html = WEB_ROOT.resolve("index.html");
        if (!Files.exists(html)) {
            Files.writeString(html, """
                    <!doctype html>
                    <html>
                    <head>
                        <meta charset="utf-8">
                        <title>本地HTTP测试页面</title>
                        <link rel="stylesheet" href="style.css">
                    </head>
                    <body>
                        <h1>简单HTTP服务器测试页面</h1>
                        <p>该页面用于测试 HttpClient.java 的 HTML 和资源下载功能。</p>
                        <img src="logo.png" alt="测试图片">
                    </body>
                    </html>
                    """, StandardCharsets.UTF_8);
        }
        Path css = WEB_ROOT.resolve("style.css");
        if (!Files.exists(css)) {
            Files.writeString(css, "body{font-family:Arial,'Microsoft YaHei',sans-serif;margin:40px;}img{width:180px;}", StandardCharsets.UTF_8);
        }
        Path image = WEB_ROOT.resolve("logo.png");
        if (!Files.exists(image)) {
            Files.write(image, Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAAdElEQVR4Xu3QsQ0AIAwDweT+O3cOBkQKpL2nD93YzOwAwA+H8S8Aq4FqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwFqwD8BT+AAGruBi0AAAAASUVORK5CYII="));
        }
    }
}
