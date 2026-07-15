# I/O Stream编程作业
- **学号**：2024150026
- **姓名**：姚泽棉
- **运行环境**：JDK 21, Windows 11

## 实验目的
[cite_start]深入理解 Java 输入输出流相关类的基本用法，掌握 Java 程序的编写和调试 [cite: 2]。

## 程序说明

### 1. TextWriter.java
- [cite_start]**功能**：从键盘读入多行英文字符串，将其写入到文本文件 `2024150026.txt` 中 [cite: 7]。同时统计字符串中所有的字符个数，并将形如 "This document contains XX bytes in total." [cite_start]的统计信息追加显示在文件末尾 [cite: 8]。
- **核心实现**：
  - 使用 `Scanner(System.in)` 包装标准输入流读取用户输入。
  - 使用 `BufferedWriter` 和 `FileWriter` 实现高效的字符流文件写入。
- **结束输入操作（重要）**：在 Windows 11 命令行环境中，当所有文本输入完成后，在**新的一行**按下快捷键 `Ctrl + Z`（屏幕显示 `^Z`），然后按下 `Enter`（回车键）。这会向程序发送 EOF (End of File) 信号，使 `Scanner.hasNextLine()` 返回 `false`，从而安全退出输入循环。

### 2. TextReader.java
- [cite_start]**功能**：从上一步生成的 `2024150026.txt` 文件中读取所有字符串，并将内容在命令行窗口显示出来，同时去掉所有的空格 [cite: 10, 11]。
- **核心实现**：
  - 使用 `BufferedReader` 和 `FileReader` 逐行读取文本文件。
  - 使用 `String.replaceAll(" ", "")` 方法通过正则表达式替换掉所有空格字符。

## 运行结果
程序的实际运行和测试效果请查看 `screenshots` 文件夹中的相关截图：
1. `输入和生成文件.jpg`：展示了 TextWriter 的多行输入、`Ctrl+Z` 结束操作以及最终生成的文本文件内容（含字符统计信息）。
2. `读取并去空格.jpg`：展示了 TextReader 在控制台中输出的已去除所有空格的文本内容。