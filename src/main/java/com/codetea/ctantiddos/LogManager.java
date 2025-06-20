package com.codetea.ctantiddos;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogManager {
    private final File logFile;
    private final int maxSize;
    private final int maxArchives;

    public LogManager(File logFile, int maxSize, int maxArchives) {
        this.logFile = logFile;
        this.maxSize = maxSize;
        this.maxArchives = maxArchives;
    }

    public void checkRotate() {
        if (logFile.length() > maxSize) {
            // 异步归档，避免主线程阻塞
            new Thread(this::rotate).start();
        }
    }

    private void rotate() {
        try {
            String time = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File archive = new File(logFile.getParent(), logFile.getName() + "." + time + ".log");
            if (!logFile.renameTo(archive)) {
                System.err.println("[CTAntiDdos] 日志归档失败: renameTo失败");
            }
            cleanupArchives();
            if (!logFile.createNewFile()) {
                System.err.println("[CTAntiDdos] 日志新建失败: createNewFile失败");
            }
        } catch (IOException e) {
            System.err.println("[CTAntiDdos] 日志归档异常: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[CTAntiDdos] 日志归档未知异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cleanupArchives() {
        try {
            File dir = logFile.getParentFile();
            File[] archives = dir.listFiles((d, name) -> name.startsWith(logFile.getName() + ".") && name.endsWith(".log"));
            if (archives == null || archives.length <= maxArchives) return;
            java.util.Arrays.sort(archives, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (int i = 0; i < archives.length - maxArchives; i++) {
                if (!archives[i].delete()) {
                    System.err.println("[CTAntiDdos] 日志归档清理失败: " + archives[i].getName());
                }
            }
        } catch (Exception e) {
            System.err.println("[CTAntiDdos] 日志归档清理异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 