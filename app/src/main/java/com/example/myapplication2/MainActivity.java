package com.example.myapplication2;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication2.utils.LogToFileUtils;
import com.example.myapplication2.utils.PermissionUtils;
import com.example.myapplication2.utils.FileUtils;
import com.example.myapplication2.utils.HttpUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 100;

    private ImageView ivPreview;
    private Button btnOpenCamera;
    private ProgressBar progressBar;
    private TextView tvStatus;

    private String currentPhotoPath;
    private Bitmap currentProcessedBitmap; // 保存处理后的图片用于上传

    // 所需权限
    private final String[] REQUIRED_PERMISSIONS = {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.INTERNET,        // 添加网络权限
            android.Manifest.permission.ACCESS_NETWORK_STATE // 网络状态权限
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupClickListeners();

        // 检查权限
        if (!PermissionUtils.hasPermissions(this, REQUIRED_PERMISSIONS)) {
            requestPermissions();
        }
        LogToFileUtils.init(this);
    }

    private void initViews() {
        ivPreview = findViewById(R.id.iv_preview);
        btnOpenCamera = findViewById(R.id.btn_open_camera);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);
    }

    // 添加上传按钮的显示/隐藏控制
    private void showUploadButton(boolean show) {
        Button btnUpload = findViewById(R.id.btn_upload); // 需要在布局中添加这个按钮
        if (btnUpload != null) {
            btnUpload.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                btnUpload.setEnabled(true);
                btnUpload.setAlpha(1.0f);
            } else {
                btnUpload.setEnabled(false);
                btnUpload.setAlpha(0.5f);
            }
        }
    }

    private void setupClickListeners() {
        btnOpenCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (PermissionUtils.hasPermissions(MainActivity.this, REQUIRED_PERMISSIONS)) {
                    openCamera();
                } else {
                    requestPermissions();
                }
            }
        });
    }

    // 添加上传按钮点击监听
    private void setupUploadButton() {
        Button btnUpload = findViewById(R.id.btn_upload); // 需要在布局中添加这个按钮
        if (btnUpload != null) {
            btnUpload.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (currentProcessedBitmap != null) {
                        uploadImageToServer();
                    } else {
                        Toast.makeText(MainActivity.this, "请先拍照处理图片", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            // 初始隐藏上传按钮
            showUploadButton(false);
        }
    }

    private void requestPermissions() {
        PermissionUtils.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CAMERA_PERMISSION);
    }

    private void openCamera() {
        Intent intent = new Intent(this, CameraActivity.class);
        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
    }

    private void showProcessing(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        tvStatus.setVisibility(show ? View.VISIBLE : View.GONE);
        btnOpenCamera.setEnabled(!show);

        if (show) {
            tvStatus.setText("处理中...");
        }
    }

    private void showUploading(boolean uploading) {
        if (uploading) {
            tvStatus.setText("上传识别中...");
            progressBar.setVisibility(View.VISIBLE);
        } else {
            progressBar.setVisibility(View.GONE);
        }
    }

    // 处理权限请求结果
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                openCamera();
            } else {
                Toast.makeText(this, "需要相机和存储权限才能使用此功能", Toast.LENGTH_LONG).show();
            }
        }
    }

    // 处理拍照结果
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            if (data != null && data.hasExtra("photo_path")) {
                String photoPath = data.getStringExtra("photo_path");
                processAndDisplayPhoto(photoPath);
            }
        }
    }

    private void processAndDisplayPhoto(final String photoPath) {
        showProcessing(true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap processedBitmap = ImageProcessor.processImage(
                            photoPath, ImageProcessor.ProcessType.GRAYSCALE);

                    if (processedBitmap != null) {
                        // 保存处理后的图片用于显示和上传
                        currentProcessedBitmap = processedBitmap;

                        // 使用新方法保存到公共目录
                        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                        String fileName = "processed_" + timeStamp + ".jpg";

                        Uri savedUri = FileUtils.saveToMediaStore(MainActivity.this, processedBitmap, fileName);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (savedUri != null) {
                                    displayProcessedPhoto(savedUri);
                                    // 自动上传识别（可选）
                                    uploadImageToServer();
                                } else {
                                    showError("保存失败");
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showError("图片处理失败");
                        }
                    });
                }
            }
        }).start();
    }

    private void displayProcessedPhoto(Uri imageUri) {
        showProcessing(false);

        try {
            // 从 URI 加载图片
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

            if (bitmap != null) {
                ivPreview.setImageBitmap(bitmap);
                tvStatus.setText("处理完成 - 已保存到相册");
                tvStatus.setVisibility(View.VISIBLE);

                // 显示上传按钮
                showUploadButton(true);

                Toast.makeText(this, "照片已保存到相册", Toast.LENGTH_SHORT).show();
            } else {
                showError("无法加载图片");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showError("加载图片失败");
        }
    }

    // 新增：上传图片到服务器进行识别
    private void uploadImageToServer() {
        if (currentProcessedBitmap == null) {
            Toast.makeText(this, "没有可上传的图片", Toast.LENGTH_SHORT).show();
            return;
        }

        showUploading(true);

        String imageName = "recognition_" + System.currentTimeMillis() + ".jpg";

        HttpUtils.uploadImageForRecognition(currentProcessedBitmap, imageName, new HttpUtils.RecognitionCallback() {
            @Override
            public void onSuccess(String result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showUploading(false);
                        // 解析并显示识别结果
                        displayRecognitionResult(result);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showUploading(false);
                        showError("识别失败: " + error);
                    }
                });
            }
        });
    }

    // 新增：显示识别结果
    private void displayRecognitionResult(String result) {
        // 解析服务器返回的JSON结果
        String displayText = parseRecognitionResult(result);

        tvStatus.setText("识别结果: " + displayText);
        Toast.makeText(this, "识别完成: " + displayText, Toast.LENGTH_LONG).show();

        // 可以在这里添加更多结果展示逻辑
        // 比如显示识别框、标签等
    }

    // 新增：解析服务器返回的识别结果
    private String parseRecognitionResult(String jsonResult) {
        try {
            // 简单的JSON解析示例
            // 实际根据你的服务器返回格式进行解析
            if (jsonResult.contains("\"objects\"")) {
                // 假设返回格式: {"objects": ["猫", "狗", "树"], "confidence": [0.95, 0.87, 0.76]}
                return "检测到多个物体";
            } else if (jsonResult.contains("cat") || jsonResult.contains("猫")) {
                return "猫";
            } else if (jsonResult.contains("dog") || jsonResult.contains("狗")) {
                return "狗";
            } else {
                return "识别结果: " + jsonResult.substring(0, Math.min(50, jsonResult.length()));
            }
        } catch (Exception e) {
            return "解析失败: " + jsonResult;
        }
    }

    private void showError(String message) {
        showProcessing(false);
        showUploading(false);
        tvStatus.setText(message);
        tvStatus.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void notifyGallery(String imagePath) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(imagePath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        sendBroadcast(mediaScanIntent);
    }
}
