package org.csu.sqliteanalyzer.db;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 命令行入口：读取 SQL（文件或交互输入），逐条交给 {@link SqliteDatabase} 执行并打印结果。
 *
 * <p>用法：</p>
 * <pre>
 *   java -cp target/classes org.csu.sqliteanalyzer.db.DatabaseCli demo.sql   # 执行文件
 *   java -cp target/classes org.csu.sqliteanalyzer.db.DatabaseCli           # 交互模式
 * </pre>
 */
public class DatabaseCli {

    public static void main(String[] args) throws Exception {
        // 统一用 UTF-8 输出，避免 Windows 控制台下中文乱码
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        SqliteDatabase db = new SqliteDatabase("db.data");
        try {
            if (args.length > 0) {
                runScript(db, Files.readString(Path.of(args[0]), StandardCharsets.UTF_8));
            } else {
                runInteractive(db);
            }
        } finally {
            System.out.println(db.stats());
            db.shutdown();
        }
    }

    /** 执行一段包含多条 SQL（以分号分隔）的脚本。 */
    private static void runScript(SqliteDatabase db, String content) {
        for (String stmt : splitStatements(content)) {
            System.out.println(db.execute(stmt));
            System.out.println("----------------------------------------");
        }
    }

    /** 交互模式：逐行读取，遇到分号结尾就执行。 */
    private static void runInteractive(SqliteDatabase db) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.println("SQLite 迷你数据库（输入 SQL，分号结尾；输入 exit 退出）");
        StringBuilder buffer = new StringBuilder();
        while (true) {
            System.out.print("db> ");
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            String trimmed = line.trim();
            if (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("quit")) {
                break;
            }
            buffer.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                for (String stmt : splitStatements(buffer.toString())) {
                    System.out.println(db.execute(stmt));
                    System.out.println("----------------------------------------");
                }
                buffer.setLength(0);
            }
        }
    }

    /** 按分号把脚本拆成一条条语句，去掉空白。 */
    private static List<String> splitStatements(String content) {
        List<String> result = new ArrayList<>();
        for (String part : content.split(";")) {
            String stmt = part.trim();
            if (!stmt.isEmpty()) {
                result.add(stmt);
            }
        }
        return result;
    }
}
