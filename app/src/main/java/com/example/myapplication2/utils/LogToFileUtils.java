package com.example.myapplication2.utils;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogToFileUtils {
    private static final String LOG_FILE_NAME = "my_app_log.txt";
    private static Context appContext; // 需要保存 Context 引用

    // 初始化方法，需要在 Application 或 Activity 中调用
    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }


    public static void e(String tag, String message) {
        // 1. 照常输出到 Logcat，便于开发时查看
        Log.e(tag, message);

        // 2. 同时写入文件
        writeToFile("ERROR", tag, message);
    }

    // 可以类似地实现 d(), i(), w() 等方法
    public static void d(String tag, String message) {
        Log.d(tag, message);
        writeToFile("DEBUG", tag, message);
    }

    private static void writeToFile(String level, String tag, String content) {
        File logFile = new File(getLogDir(), LOG_FILE_NAME);
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String logLine = String.format("%s %s/%s: %s\n", time, level, tag, content);

        // 注意：文件操作应在后台线程进行
        new Thread(() -> {
            try (FileOutputStream fos = new FileOutputStream(logFile, true); // true 表示追加模式
                 PrintWriter writer = new PrintWriter(fos)) {
                writer.write(logLine);
            } catch (IOException e) {
                Log.e("LogToFile", "写入日志文件失败", e);
            }
        }).start();
    }


    private static File getLogDir() {
        // 修正：使用保存的 appContext
        File filesDir = appContext.getExternalFilesDir(null);
        File logDir = new File(filesDir, "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        return logDir;
    }
}