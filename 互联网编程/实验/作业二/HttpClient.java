import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpClient {
    private static final Pattern RESOURCE_PATTERN = Pattern.compile(
            "(?i)(?:src|href)\\s*=\\s*[\"']([^\"'#]+)[\"']");

    public static void main(String[] args) {
        if (args.length == 0) {
            SwingUtilities.invokeLater(HttpClient::showGui);
            return;
        }

        String url = args[0];
        Path outputDir = args.length >= 2 ? Paths.get(args[1]) : Paths.get("downloads");
        String username = args.length >= 3 ? args[2] : "";
        String password = args.length >= 4 ? args[3] : "";

        try {
            DownloadResult result = new HttpClient().downloadPage(url, outputDir, username, password);
            System.out.println("下载完成: " + result.mainFile.toAbsolutePath());
            if (!result.resources.isEmpty()) {
                System.out.println("自动下载资源:");
                for (Path path : result.resources) {
                    System.out.println("  " + path.toAbsolutePath());
                }
            }
        } catch (Exception ex) {
            System.err.println("下载失败: " + ex.getMessage());
        }
    }

    public DownloadResult downloadPage(String urlText, Path outputDir, String username, String password) throws IOException {
        Files.createDirectories(outputDir);
        URI uri = URI.create(urlText);
        HttpResponse response = request(uri, username, password);
        Path mainFile = saveResponse(uri, response, outputDir);

        List<Path> resources = new ArrayList<>();
        if (isHtml(response)) {
            String html = new String(response.body, StandardCharsets.UTF_8);
            for (URI resourceUri : findResources(uri, html)) {
                try {
                    HttpResponse resourceResponse = request(resourceUri, username, password);
                    resources.add(saveResponse(resourceUri, resourceResponse, outputDir));
                } catch (IOException ex) {
                    System.out.println("资源下载失败: " + resourceUri + " (" + ex.getMessage() + ")");
                }
            }
        }
        return new DownloadResult(mainFile, resources);
    }

    private HttpResponse request(URI uri, String username, String password) throws IOException {
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme)) {
            throw new IOException("仅支持 HTTP 协议: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IOException("URL 缺少主机名");
        }
        int port = uri.getPort() == -1 ? 80 : uri.getPort();
        String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            path += "?" + uri.getRawQuery();
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 8000);
            socket.setSoTimeout(8000);

            OutputStream out = socket.getOutputStream();
            StringBuilder request = new StringBuilder();
            request.append("GET ").append(path).append(" HTTP/1.1\r\n");
            request.append("Host: ").append(host).append("\r\n");
            request.append("User-Agent: SimpleJavaHttpClient/1.0\r\n");
            request.append("Accept: */*\r\n");
            request.append("Connection: close\r\n");
            if (username != null && !username.isBlank()) {
                String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
                request.append("Authorization: Basic ").append(token).append("\r\n");
            }
            request.append("\r\n");
            out.write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
            out.flush();

            return readResponse(socket.getInputStream());
        }
    }

    private HttpResponse readResponse(InputStream input) throws IOException {
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        int matched = 0;
        int[] end = {'\r', '\n', '\r', '\n'};
        int value;
        while ((value = input.read()) != -1) {
            headerBytes.write(value);
            matched = value == end[matched] ? matched + 1 : (value == '\r' ? 1 : 0);
            if (matched == end.length) {
                break;
            }
        }

        String headerText = headerBytes.toString(StandardCharsets.ISO_8859_1);
        String[] lines = headerText.split("\r\n");
        if (lines.length == 0 || !lines[0].startsWith("HTTP/")) {
            throw new IOException("服务器响应格式不正确");
        }

        int status = parseStatus(lines[0]);
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
            }
        }

        byte[] body;
        if ("chunked".equalsIgnoreCase(headers.getOrDefault("transfer-encoding", ""))) {
            body = readChunkedBody(input);
        } else {
            body = input.readAllBytes();
        }

        if (status >= 400) {
            throw new IOException("HTTP 状态码 " + status);
        }
        return new HttpResponse(status, headers, body);
    }

    private static int parseStatus(String statusLine) throws IOException {
        String[] parts = statusLine.split(" ");
        if (parts.length < 2) {
            throw new IOException("无法读取 HTTP 状态码");
        }
        return Integer.parseInt(parts[1]);
    }

    private static byte[] readChunkedBody(InputStream input) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readAsciiLine(input);
            int semicolon = sizeLine.indexOf(';');
            if (semicolon >= 0) {
                sizeLine = sizeLine.substring(0, semicolon);
            }
            int size = Integer.parseInt(sizeLine.trim(), 16);
            if (size == 0) {
                readAsciiLine(input);
                break;
            }
            body.write(input.readNBytes(size));
            input.readNBytes(2);
        }
        return body.toByteArray();
    }

    private static String readAsciiLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int previous = -1;
        int current;
        while ((current = input.read()) != -1) {
            if (previous == '\r' && current == '\n') {
                byte[] bytes = line.toByteArray();
                return new String(bytes, 0, Math.max(0, bytes.length - 1), StandardCharsets.ISO_8859_1);
            }
            line.write(current);
            previous = current;
        }
        return line.toString(StandardCharsets.ISO_8859_1);
    }

    private Path saveResponse(URI uri, HttpResponse response, Path outputDir) throws IOException {
        Path file = outputDir.resolve(fileNameFor(uri, response.headers));
        Files.createDirectories(file.getParent());
        Files.write(file, response.body);
        return file;
    }

    private static String fileNameFor(URI uri, Map<String, String> headers) {
        String path = uri.getPath();
        String name = path == null || path.isBlank() || path.endsWith("/") ? "index.html" : path.substring(path.lastIndexOf('/') + 1);
        if (!name.contains(".")) {
            String contentType = headers.getOrDefault("content-type", "");
            if (contentType.contains("html")) {
                name += ".html";
            } else if (contentType.contains("jpeg")) {
                name += ".jpg";
            } else if (contentType.contains("png")) {
                name += ".png";
            } else if (contentType.contains("css")) {
                name += ".css";
            } else if (contentType.contains("javascript")) {
                name += ".js";
            }
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static boolean isHtml(HttpResponse response) {
        return response.headers.getOrDefault("content-type", "").toLowerCase(Locale.ROOT).contains("html");
    }

    private static List<URI> findResources(URI baseUri, String html) {
        List<URI> resources = new ArrayList<>();
        Matcher matcher = RESOURCE_PATTERN.matcher(html);
        while (matcher.find()) {
            String value = matcher.group(1).trim();
            if (value.startsWith("http://") || value.startsWith("/") || !value.contains(":")) {
                resources.add(baseUri.resolve(value));
            }
        }
        return resources;
    }

    private static void showGui() {
        JFrame frame = new JFrame("简单HTTP客户端程序");
        JTextField urlField = new JTextField("http://localhost:8080/index.html");
        JTextField dirField = new JTextField("downloads");
        JTextField userField = new JTextField();
        JPasswordField passField = new JPasswordField();
        JTextArea logArea = new JTextArea(8, 60);
        JEditorPane pageView = new JEditorPane();
        JButton downloadButton = new JButton("下载并显示");

        logArea.setEditable(false);
        pageView.setEditable(false);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("URL"));
        form.add(urlField);
        form.add(new JLabel("保存目录"));
        form.add(dirField);
        form.add(new JLabel("用户名"));
        form.add(userField);
        form.add(new JLabel("密码"));
        form.add(passField);

        downloadButton.addActionListener(event -> {
            downloadButton.setEnabled(false);
            logArea.setText("开始下载...\n");
            new Thread(() -> {
                try {
                    DownloadResult result = new HttpClient().downloadPage(
                            urlField.getText().trim(),
                            Paths.get(dirField.getText().trim()),
                            userField.getText().trim(),
                            new String(passField.getPassword()));
                    StringBuilder log = new StringBuilder();
                    log.append("主文件: ").append(result.mainFile.toAbsolutePath()).append('\n');
                    for (Path path : result.resources) {
                        log.append("资源: ").append(path.toAbsolutePath()).append('\n');
                    }
                    SwingUtilities.invokeLater(() -> {
                        logArea.setText(log.toString());
                        try {
                            pageView.setPage(result.mainFile.toUri().toURL());
                        } catch (IOException ex) {
                            pageView.setText("文件已下载，无法在界面中直接显示该内容。");
                        }
                        downloadButton.setEnabled(true);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        logArea.setText("下载失败: " + ex.getMessage());
                        downloadButton.setEnabled(true);
                    });
                }
            }).start();
        });

        frame.setLayout(new BorderLayout(10, 10));
        frame.add(form, BorderLayout.NORTH);
        frame.add(new JScrollPane(pageView), BorderLayout.CENTER);
        frame.add(new JScrollPane(logArea), BorderLayout.SOUTH);
        frame.add(downloadButton, BorderLayout.EAST);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(900, 650);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private record HttpResponse(int status, Map<String, String> headers, byte[] body) {
    }

    public record DownloadResult(Path mainFile, List<Path> resources) {
    }
}
