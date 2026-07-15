import java.io.*;
import java.util.Scanner;

public class TextWriter {
    public static void main(String[] args) {
        // 请将此处替换为你的实际学号，并确保后缀为 .txt
        String fileName = "2024150026.txt";
        int totalChars = 0;

        System.out.println("请输入多行英文（在 Windows 命令行中按 Ctrl+Z 然后回车结束输入）：");

        try (Scanner scanner = new Scanner(System.in);
             BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {

            // 当输入 Ctrl+Z 并回车时，hasNextLine() 会返回 false，循环自动结束
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                writer.write(line);
                writer.newLine();
                // 统计每行的字符数
                totalChars += line.length();
            }

            // 写入并打印统计信息
            String stats = "This document contains " + totalChars + " bytes in total.";
            writer.write(stats);

            System.out.println("文件已保存，统计信息：" + stats);

        } catch (IOException e) {
            System.err.println("文件写入错误: " + e.getMessage());
        }
    }
}