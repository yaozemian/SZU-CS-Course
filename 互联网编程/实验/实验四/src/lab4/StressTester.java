package lab4;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class StressTester {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        int clients = args.length > 2 ? Integer.parseInt(args[2]) : 100;
        int requestsPerClient = args.length > 3 ? Integer.parseInt(args[3]) : 20;

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<String> paths = List.of("/", "/style.css", "/app.js", "/images/network.svg", "/api/session");
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(clients, 200));
        Instant start = Instant.now();

        List<Callable<Void>> tasks = java.util.stream.IntStream.range(0, clients)
                .mapToObj(client -> (Callable<Void>) () -> {
                    for (int i = 0; i < requestsPerClient; i++) {
                        String path = paths.get((client + i) % paths.size());
                        if (request(host, port, path)) {
                            success.incrementAndGet();
                        } else {
                            failed.incrementAndGet();
                        }
                    }
                    return null;
                })
                .toList();

        for (Future<Void> future : pool.invokeAll(tasks)) {
            future.get();
        }
        pool.shutdown();

        Duration cost = Duration.between(start, Instant.now());
        int total = clients * requestsPerClient;
        double seconds = Math.max(cost.toMillis() / 1000.0, 0.001);
        System.out.println("Clients: " + clients);
        System.out.println("Requests per client: " + requestsPerClient);
        System.out.println("Total requests: " + total);
        System.out.println("Success: " + success.get());
        System.out.println("Failed: " + failed.get());
        System.out.printf("Elapsed: %.3f s%n", seconds);
        System.out.printf("Throughput: %.2f requests/s%n", total / seconds);
    }

    private static boolean request(String host, int port, String path) {
        try (Socket socket = new Socket(host, port);
             OutputStream output = new BufferedOutputStream(socket.getOutputStream());
             InputStream input = new BufferedInputStream(socket.getInputStream())) {
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + host + ":" + port + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            output.write(request.getBytes(StandardCharsets.UTF_8));
            output.flush();

            byte[] prefix = input.readNBytes(12);
            return new String(prefix, StandardCharsets.US_ASCII).startsWith("HTTP/1.1 20");
        } catch (IOException e) {
            return false;
        }
    }
}
