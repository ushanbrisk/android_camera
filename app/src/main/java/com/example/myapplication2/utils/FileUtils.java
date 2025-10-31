package com.example.myapplication2.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileUtils {

    private static final String TAG = "FileUtils";

    // 创建图片文件
    public static File createImageFile(Context context) throws IOException {
        // 创建时间戳名称
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";

        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir == null) {
            storageDir = context.getFilesDir();
        }

        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        Log.d(TAG, "创建文件: " + image.getAbsolutePath());
        return image;
    }

    // 检查外部存储是否可用
    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    // 获取应用图片目录
    public static File getAppPictureDirectory(Context context) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
}
