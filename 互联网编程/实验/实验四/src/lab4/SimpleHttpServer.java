package lab4;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleHttpServer {
    private static final int DEFAULT_PORT = 8080;
    private static final int THREAD_POOL_SIZE = 32;
    private static final Path WEB_ROOT = Path.of("www").toAbsolutePath().normalize();
    private static final AtomicLong REQUEST_COUNT = new AtomicLong();
    private static final Map<String, Session> SESSIONS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("HTTP server started: http://localhost:" + port);
            System.out.println("Static web root: " + WEB_ROOT);
            System.out.println("Thread pool size: " + THREAD_POOL_SIZE);

            while (true) {
                Socket socket = serverSocket.accept();
                pool.submit(() -> handleClient(socket));
            }
        }
    }

    private static void handleClient(Socket socket) {
        try (socket;
             InputStream input = new BufferedInputStream(socket.getInputStream());
             OutputStream output = new BufferedOutputStream(socket.getOutputStream())) {

            HttpRequest request = HttpRequest.read(input);
            if (request == null) {
                return;
            }

            long requestNo = REQUEST_COUNT.incrementAndGet();
            Session session = getOrCreateSession(request);
            session.lastVisit = LocalDateTime.now();
            session.visitCount++;

            System.out.printf("[%s] #%d %s %s from %s, user=%s, visits=%d%n",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME),
                    requestNo,
                    request.method,
                    request.path,
                    socket.getInetAddress().getHostAddress(),
                    session.username,
                    session.visitCount);

            HttpResponse response = route(request, session);
            response.headers.put("Set-Cookie", "LAB4_SESSION=" + session.id + "; Path=/; Max-Age=3600");
            response.headers.put("X-Request-Count", String.valueOf(requestNo));
            response.writeTo(output, request.method.equals("HEAD"));
        } catch (Exception e) {
            System.out.println("Client handling failed: " + e.getMessage());
        }
    }

    private static HttpResponse route(HttpRequest request, Session session) throws IOException {
        if (!request.method.equals("GET") && !request.method.equals("HEAD") && !request.method.equals("POST")) {
            return HttpResponse.text(405, "Method Not Allowed", "Only GET, HEAD and POST are supported.");
        }

        if (request.method.equals("POST") && request.path.equals("/login")) {
            Map<String, String> form = parseForm(request.body);
            session.username = form.getOrDefault("username", "guest").trim();
            if (session.username.isEmpty()) {
                session.username = "guest";
            }

            String html = """
                    <!doctype html>
                    <html lang="zh-CN">
                    <head><meta charset="UTF-8"><title>POST Result</title></head>
                    <body>
                      <h1>POST 请求处理成功</h1>
                      <p>用户名已经保存到服务端会话：%s</p>
                      <p>服务器通过 Cookie 将会话 ID 返回给浏览器。</p>
                      <p><a href="/">返回首页</a></p>
                    </body>
                    </html>
                    """.formatted(escapeHtml(session.username));
            return HttpResponse.html(200, "OK", html);
        }

        if (request.path.equals("/api/session")) {
            String json = """
                    {
                      "sessionId": "%s",
                      "username": "%s",
                      "visitCount": %d,
                      "lastVisit": "%s"
                    }
                    """.formatted(session.id, escapeJson(session.username), session.visitCount, session.lastVisit);
            return HttpResponse.bytes(200, "OK", "application/json; charset=UTF-8",
                    json.getBytes(StandardCharsets.UTF_8));
        }

        return serveStaticFile(request.path);
    }

    private static HttpResponse serveStaticFile(String requestPath) throws IOException {
        String cleanPath = requestPath.split("\\?", 2)[0];
        if (cleanPath.equals("/")) {
            cleanPath = "/index.html";
        }

        String decodedPath = URLDecoder.decode(cleanPath, StandardCharsets.UTF_8);
        Path target = WEB_ROOT.resolve(decodedPath.substring(1)).normalize();
        if (!target.startsWith(WEB_ROOT) || Files.isDirectory(target) || !Files.exists(target)) {
            return HttpResponse.text(404, "Not Found", "404 Not Found: " + cleanPath);
        }

        return HttpResponse.bytes(200, "OK", contentType(target), Files.readAllBytes(target));
    }

    private static Session getOrCreateSession(HttpRequest request) {
        String sessionId = null;
        String cookieHeader = request.headers.get("cookie");
        if (cookieHeader != null) {
            for (String cookie : cookieHeader.split(";")) {
                String[] pair = cookie.trim().split("=", 2);
                if (pair.length == 2 && pair[0].equals("LAB4_SESSION")) {
                    sessionId = pair[1];
                    break;
                }
            }
        }

        if (sessionId == null || !SESSIONS.containsKey(sessionId)) {
            sessionId = UUID.randomUUID().toString();
            SESSIONS.put(sessionId, new Session(sessionId));
        }
        return SESSIONS.get(sessionId);
    }

    private static Map<String, String> parseForm(byte[] body) {
        Map<String, String> form = new HashMap<>();
        String raw = new String(body, StandardCharsets.UTF_8);
        for (String item : raw.split("&")) {
            if (item.isBlank()) {
                continue;
            }
            String[] pair = item.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            form.put(key, value);
        }
        return form;
    }

    private static String contentType(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html; charset=UTF-8";
        if (name.endsWith(".css")) return "text/css; charset=UTF-8";
        if (name.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class Session {
        final String id;
        String username = "guest";
        int visitCount;
        LocalDateTime lastVisit;

        Session(String id) {
            this.id = id;
        }
    }

    private static final class HttpRequest {
        final String method;
        final String path;
        final Map<String, String> headers;
        final byte[] body;

        private HttpRequest(String method, String path, Map<String, String> headers, byte[] body) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
        }

        static HttpRequest read(InputStream input) throws IOException {
            String headerText = readHeaders(input);
            if (headerText.isBlank()) {
                return null;
            }

            String[] lines = headerText.split("\\r?\\n");
            String[] firstLine = lines[0].split(" ");
            if (firstLine.length < 2) {
                return null;
            }

            Map<String, String> headers = new HashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int index = lines[i].indexOf(':');
                if (index > 0) {
                    headers.put(lines[i].substring(0, index).trim().toLowerCase(Locale.ROOT),
                            lines[i].substring(index + 1).trim());
                }
            }

            int contentLength = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            byte[] body = input.readNBytes(contentLength);
            return new HttpRequest(firstLine[0].toUpperCase(Locale.ROOT), firstLine[1], headers, body);
        }

        private static String readHeaders(InputStream input) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int previous3 = -1;
            int previous2 = -1;
            int previous1 = -1;
            int current;

            while ((current = input.read()) != -1) {
                buffer.write(current);
                if (previous3 == '\r' && previous2 == '\n' && previous1 == '\r' && current == '\n') {
                    break;
                }
                previous3 = previous2;
                previous2 = previous1;
                previous1 = current;
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    private static final class HttpResponse {
        final int statusCode;
        final String reason;
        final byte[] body;
        final Map<String, String> headers = new HashMap<>();

        private HttpResponse(int statusCode, String reason, String contentType, byte[] body) {
            this.statusCode = statusCode;
            this.reason = reason;
            this.body = body;
            headers.put("Content-Type", contentType);
            headers.put("Content-Length", String.valueOf(body.length));
            headers.put("Connection", "close");
            headers.put("Server", "Lab4-Java-Socket-Server");
        }

        static HttpResponse text(int statusCode, String reason, String text) {
            return bytes(statusCode, reason, "text/plain; charset=UTF-8", text.getBytes(StandardCharsets.UTF_8));
        }

        static HttpResponse html(int statusCode, String reason, String html) {
            return bytes(statusCode, reason, "text/html; charset=UTF-8", html.getBytes(StandardCharsets.UTF_8));
        }

        static HttpResponse bytes(int statusCode, String reason, String contentType, byte[] body) {
            return new HttpResponse(statusCode, reason, contentType, body);
        }

        void writeTo(OutputStream output, boolean headOnly) throws IOException {
            StringBuilder header = new StringBuilder();
            header.append("HTTP/1.1 ").append(statusCode).append(' ').append(reason).append("\r\n");
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                header.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            }
            header.append("\r\n");
            output.write(header.toString().getBytes(StandardCharsets.UTF_8));
            if (!headOnly) {
                output.write(body);
            }
            output.flush();
        }
    }
}
