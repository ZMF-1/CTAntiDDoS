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
            rotate();
        }
    }

    private void rotate() {
        String time = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File archive = new File(logFile.getParent(), logFile.getName() + "." + time + ".log");
        logFile.renameTo(archive);
        cleanupArchives();
        try {
            logFile.createNewFile();
        } catch (IOException ignored) {}
    }

    private void cleanupArchives() {
        File dir = logFile.getParentFile();
        File[] archives = dir.listFiles((d, name) -> name.startsWith(logFile.getName() + ".") && name.endsWith(".log"));
        if (archives == null || archives.length <= maxArchives) return;
        java.util.Arrays.sort(archives, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < archives.length - maxArchives; i++) {
            archives[i].delete();
        }
    }
} 