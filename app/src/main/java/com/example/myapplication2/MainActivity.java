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
import androidx.core.content.FileProvider;
import com.example.myapplication2.utils.PermissionUtils;
import com.example.myapplication2.utils.FileUtils;
import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 100;

    private ImageView ivPreview;
    private Button btnOpenCamera;
    private ProgressBar progressBar;
    private TextView tvStatus;

    private String currentPhotoPath;

    // 所需权限
    private final String[] REQUIRED_PERMISSIONS = {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
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
    }

    private void initViews() {
        ivPreview = findViewById(R.id.iv_preview);
        btnOpenCamera = findViewById(R.id.btn_open_camera);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);
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
                    // 应用灰度效果
                    Bitmap processedBitmap = ImageProcessor.processImage(
                            photoPath, ImageProcessor.ProcessType.GRAYSCALE);

                    if (processedBitmap != null) {
                        // 保存处理后的图片
                        String processedPath = photoPath.replace(".jpg", "_processed.jpg");
                        ImageProcessor.saveBitmap(processedBitmap, processedPath);

                        // 在主线程更新UI
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                displayProcessedPhoto(processedPath);
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

    private void displayProcessedPhoto(String imagePath) {
        showProcessing(false);

        // 加载并显示图片
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap != null) {
            ivPreview.setImageBitmap(bitmap);
            tvStatus.setText("处理完成");
            tvStatus.setVisibility(View.VISIBLE);

            // 通知图库更新
            notifyGallery(imagePath);

            Toast.makeText(this, "照片已保存并处理", Toast.LENGTH_SHORT).show();
        } else {
            showError("无法加载图片");
        }
    }

    private void showError(String message) {
        showProcessing(false);
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