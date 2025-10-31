package com.example.myapplication2;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImageProcessor {
    private static final String TAG = "ImageProcessor";

    public enum ProcessType {
        GRAYSCALE,      // 灰度化
        SEPIA,          // 怀旧效果
        INVERT,         // 反色
        BLUR,           // 模糊
        SHARPEN,        // 锐化
        BRIGHTNESS,     // 亮度调整
        CONTRAST        // 对比度调整
    }

    // 处理图片
    public static Bitmap processImage(String imagePath, ProcessType processType) {
        try {
            Bitmap originalBitmap = loadCorrectlyOrientedBitmap(imagePath);
            if (originalBitmap == null) {
                Log.e(TAG, "无法加载图片: " + imagePath);
                return null;
            }

            Bitmap processedBitmap = null;

            switch (processType) {
                case GRAYSCALE:
                    processedBitmap = toGrayscale(originalBitmap);
                    break;
                case SEPIA:
                    processedBitmap = toSepia(originalBitmap);
                    break;
                case INVERT:
                    processedBitmap = invertColors(originalBitmap);
                    break;
                case BLUR:
                    processedBitmap = applyBlur(originalBitmap);
                    break;
                case SHARPEN:
                    processedBitmap = sharpenImage(originalBitmap);
                    break;
                case BRIGHTNESS:
                    processedBitmap = adjustBrightness(originalBitmap, 1.2f);
                    break;
                case CONTRAST:
                    processedBitmap = adjustContrast(originalBitmap, 1.5f);
                    break;
                default:
                    processedBitmap = originalBitmap;
            }

            return processedBitmap;

        } catch (Exception e) {
            Log.e(TAG, "图片处理错误: " + e.getMessage());
            return null;
        }
    }

    // 正确加载旋转的图片
    private static Bitmap loadCorrectlyOrientedBitmap(String imagePath) {
        try {
            // 首先获取图片旋转信息
            ExifInterface exif = new ExifInterface(imagePath);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );

            // 加载原始图片
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2; // 缩小尺寸以提高性能

            Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
            if (bitmap == null) {
                return null;
            }

            // 根据旋转信息旋转图片
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                default:
                    return bitmap;
            }

            return Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        } catch (IOException e) {
            Log.e(TAG, "读取图片EXIF信息错误: " + e.getMessage());
            return BitmapFactory.decodeFile(imagePath);
        }
    }

    // 转换为灰度图
    private static Bitmap toGrayscale(Bitmap original) {
        Bitmap result = Bitmap.createBitmap(original.getWidth(),
                original.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();

        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0);

        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
        paint.setColorFilter(filter);

        canvas.drawBitmap(original, 0, 0, paint);
        return result;
    }

    // 怀旧效果
    private static Bitmap toSepia(Bitmap original) {
        Bitmap result = Bitmap.createBitmap(original.getWidth(),
                original.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();

        ColorMatrix matrix = new ColorMatrix();
        matrix.setScale(1f, 0.95f, 0.82f, 1.0f); // 棕褐色调
        matrix.postConcat(getSaturationMatrix(0.5f)); // 降低饱和度

        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
        paint.setColorFilter(filter);

        canvas.drawBitmap(original, 0, 0, paint);
        return result;
    }

    // 反色效果
    private static Bitmap invertColors(Bitmap original) {
        Bitmap result = Bitmap.createBitmap(original.getWidth(),
                original.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();

        ColorMatrix matrix = new ColorMatrix(new float[] {
                -1, 0, 0, 0, 255,
                0, -1, 0, 0, 255,
                0, 0, -1, 0, 255,
                0, 0, 0, 1, 0
        });

        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
        paint.setColorFilter(filter);

        canvas.drawBitmap(original, 0, 0, paint);
        return result;
    }

    // 简单模糊效果
    private static Bitmap applyBlur(Bitmap original) {
        // 简化版模糊 - 实际应用中可以使用 RenderScript 或更复杂的算法
        Bitmap result = Bitmap.createScaledBitmap(original,
                original.getWidth() / 4, original.getHeight() / 4, true);
        return Bitmap.createScaledBitmap(result,
                original.getWidth(), original.getHeight(), true);
    }

    // 锐化效果
    private static Bitmap sharpenImage(Bitmap original) {
        // 简化版锐化
        return original; // 实际应用中可以实现卷积核锐化
    }

    // 调整亮度
    private static Bitmap adjustBrightness(Bitmap original, float brightness) {
        Bitmap result = Bitmap.createBitmap(original.getWidth(),
                original.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();

        ColorMatrix matrix = new ColorMatrix();
        matrix.setScale(brightness, brightness, brightness, 1);

        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
        paint.setColorFilter(filter);

        canvas.drawBitmap(original, 0, 0, paint);
        return result;
    }

    // 调整对比度
    private static Bitmap adjustContrast(Bitmap original, float contrast) {
        Bitmap result = Bitmap.createBitmap(original.getWidth(),
                original.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();

        float scale = contrast;
        float translate = (1 - contrast) / 2 * 255;

        ColorMatrix matrix = new ColorMatrix(new float[] {
                scale, 0, 0, 0, translate,
                0, scale, 0, 0, translate,
                0, 0, scale, 0, translate,
                0, 0, 0, 1, 0
        });

        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
        paint.setColorFilter(filter);

        canvas.drawBitmap(original, 0, 0, paint);
        return result;
    }

    private static ColorMatrix getSaturationMatrix(float saturation) {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(saturation);
        return matrix;
    }

    // 保存处理后的图片
    public static boolean saveBitmap(Bitmap bitmap, String filePath) {
        try {
            FileOutputStream out = new FileOutputStream(filePath);
            boolean success = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.close();
            return success;
        } catch (Exception e) {
            Log.e(TAG, "保存图片错误: " + e.getMessage());
            return false;
        }
    }
}