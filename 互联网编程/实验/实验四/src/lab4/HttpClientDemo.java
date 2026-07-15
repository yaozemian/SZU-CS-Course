package lab4;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class HttpClientDemo {
    private static String cookie = "";

    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;

        send(host, port, "GET", "/", "");
        send(host, port, "HEAD", "/images/network.svg", "");
        send(host, port, "POST", "/login", "username=YaoZemian&message=hello");
        send(host, port, "GET", "/api/session", "");
    }

    private static void send(String host, int port, String method, String path, String body) throws IOException {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        try (Socket socket = new Socket(host, port);
             OutputStream output = new BufferedOutputStream(socket.getOutputStream());
             InputStream input = new BufferedInputStream(socket.getInputStream())) {

            StringBuilder request = new StringBuilder();
            request.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
            request.append("Host: ").append(host).append(':').append(port).append("\r\n");
            request.append("User-Agent: Lab4-HttpClientDemo\r\n");
            request.append("Connection: close\r\n");
            if (!cookie.isEmpty()) {
                request.append("Cookie: ").append(cookie).append("\r\n");
            }
            if (method.equals("POST")) {
                request.append("Content-Type: application/x-www-form-urlencoded; charset=UTF-8\r\n");
                request.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
            }
            request.append("\r\n");

            output.write(request.toString().getBytes(StandardCharsets.UTF_8));
            if (method.equals("POST")) {
                output.write(bodyBytes);
            }
            output.flush();

            HttpResult result = readResponse(input);
            updateCookie(result.headers);
            printResult(method, path, result);
        }
    }

    private static HttpResult readResponse(InputStream input) throws IOException {
        String headersText = readHeaders(input);
        String[] lines = headersText.split("\\r?\\n");
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int index = lines[i].indexOf(':');
            if (index > 0) {
                headers.put(lines[i].substring(0, index).trim().toLowerCase(Locale.ROOT),
                        lines[i].substring(index + 1).trim());
            }
        }

        int length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
        byte[] body = input.readNBytes(length);
        return new HttpResult(lines.length == 0 ? "" : lines[0], headers, body);
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

    private static void updateCookie(Map<String, String> headers) {
        String setCookie = headers.get("set-cookie");
        if (setCookie == null) {
            return;
        }
        cookie = setCookie.split(";", 2)[0];
    }

    private static void printResult(String method, String path, HttpResult result) {
        System.out.println("\n========== " + method + " " + path + " ==========");
        System.out.println(result.statusLine);
        System.out.println("Content-Type: " + result.headers.getOrDefault("content-type", ""));
        System.out.println("Cookie: " + cookie);
        System.out.println("Body bytes: " + result.body.length);

        String type = result.headers.getOrDefault("content-type", "");
        if (type.startsWith("text/") || type.contains("json") || type.contains("javascript")) {
            String text = new String(result.body, StandardCharsets.UTF_8);
            System.out.println(text.length() > 500 ? text.substring(0, 500) + "..." : text);
        } else {
            System.out.println("Binary/image response received successfully.");
        }
    }

    private record HttpResult(String statusLine, Map<String, String> headers, byte[] body) {
    }
}
