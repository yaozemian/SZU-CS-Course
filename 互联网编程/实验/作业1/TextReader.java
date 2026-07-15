import java.io.*;

public class TextReader {
    public static void main(String[] args) {
        String fileName = "2024150026.txt";

        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            System.out.println("文件内容（已去空格）：");

            while ((line = reader.readLine()) != null) {
                // 使用 replaceAll 去掉所有空格 [cite: 11]
                System.out.print(line.replaceAll(" ", ""));
            }
            System.out.println(); // 换行

        } catch (IOException e) {
            System.err.println("文件读取错误: " + e.getMessage());
        }
    }
}