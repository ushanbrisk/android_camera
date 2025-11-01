//package com.example.myapplication2.utils;
//
//import android.content.Context;
//import android.os.Environment;
//import android.util.Log;
//import java.io.File;
//import java.io.IOException;
//import java.text.SimpleDateFormat;
//import java.util.Date;
//import java.util.Locale;
//
//public class FileUtils {
//
//    private static final String TAG = "FileUtils";
//
//    // 创建图片文件
//    public static File createImageFile(Context context) throws IOException {
//        // 创建时间戳名称
//        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
//        String imageFileName = "JPEG_" + timeStamp + "_";
//
//        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
//        if (storageDir == null) {
//            storageDir = context.getFilesDir();
//        }
//
//        File image = File.createTempFile(
//                imageFileName,  /* prefix */
//                ".jpg",         /* suffix */
//                storageDir      /* directory */
//        );
//
//        Log.d(TAG, "创建文件: " + image.getAbsolutePath());
//        return image;
//    }
//
//    // 检查外部存储是否可用
//    public static boolean isExternalStorageWritable() {
//        String state = Environment.getExternalStorageState();
//        return Environment.MEDIA_MOUNTED.equals(state);
//    }
//
//    // 获取应用图片目录
//    public static File getAppPictureDirectory(Context context) {
//        File dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
//        if (dir != null && !dir.exists()) {
//            dir.mkdirs();
//        }
//        return dir;
//    }
//}

package com.example.myapplication2.utils;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileUtils {

    // 方法1：保存到公共目录（推荐）
    public static File createPublicImageFile(Context context) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "CameraApp_" + timeStamp + ".jpg";

        // 创建应用专属的公共文件夹
        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File appDir = new File(picturesDir, "CameraApp");

        if (!appDir.exists()) {
            appDir.mkdirs();
        }

        return new File(appDir, imageFileName);
    }

    // 方法2：使用 MediaStore（Android 10+）
    public static Uri saveToMediaStore(Context context, Bitmap bitmap, String fileName) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CameraApp");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);

            Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri uri = context.getContentResolver().insert(collection, values);

            try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);

                // 标记为已完成
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                context.getContentResolver().update(uri, values, null, null);

                return uri;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        } else {
            // Android 9 及以下使用传统方法
            return saveToPublicDirectory(context, bitmap, fileName);
        }
    }

    private static Uri saveToPublicDirectory(Context context, Bitmap bitmap, String fileName) throws IOException {
        File file = createPublicImageFile(context);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);

            // 通知媒体库更新
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(Uri.fromFile(file));
            context.sendBroadcast(mediaScanIntent);

            return Uri.fromFile(file);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}