package com.example.myapplication2;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 100;

    // UI组件
    private ImageView ivPreview;
    private TextView tvImageResult; // 图片上的结果预览
    private TextView tvCurrentResult; // 当前结果区域
    private LinearLayout llCurrentResult; // 当前结果容器
    private LinearLayout llHistoryContainer; // 历史记录容器
    private ScrollView scrollHistory; // 历史记录滚动视图
    private Button btnOpenCamera;
    private Button btnUpload;
    private Button btnCopyResult;
    private Button btnClearCurrent;
    private Button btnClearHistory;
    private ProgressBar progressBar;
    private TextView tvStatus;

    // 数据
    private String currentPhotoPath;
    private Bitmap currentProcessedBitmap;
    private List<RecognitionResult> recognitionHistory; // 历史记录列表


    // 历史记录数据类
    private static class RecognitionResult {
        String timestamp;
        String result;
        String imagePath; // 可选：关联的图片路径

        RecognitionResult(String timestamp, String result, String imagePath) {
            this.timestamp = timestamp;
            this.result = result;
            this.imagePath = imagePath;
        }
    }

    // 所需权限
    private final String[] REQUIRED_PERMISSIONS = {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.ACCESS_NETWORK_STATE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupClickListeners();
        initData();

        // 检查权限
        if (!PermissionUtils.hasPermissions(this, REQUIRED_PERMISSIONS)) {
            requestPermissions();
        }
        LogToFileUtils.init(this);
    }

    private void initViews() {
        ivPreview = findViewById(R.id.iv_preview);
        tvImageResult = findViewById(R.id.tv_image_result);
        tvCurrentResult = findViewById(R.id.tv_current_result);
        llCurrentResult = findViewById(R.id.ll_current_result);
        llHistoryContainer = findViewById(R.id.ll_history_container);
        scrollHistory = findViewById(R.id.scroll_history);
        btnOpenCamera = findViewById(R.id.btn_open_camera);
        btnUpload = findViewById(R.id.btn_upload);
        btnCopyResult = findViewById(R.id.btn_copy_result);
        btnClearCurrent = findViewById(R.id.btn_clear_current);
        btnClearHistory = findViewById(R.id.btn_clear_history);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);
    }

    private void initData() {
        recognitionHistory = new ArrayList<>();
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

        // 上传按钮
        btnUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentProcessedBitmap != null) {
                    uploadImageToServer();
                } else {
                    showToast("请先拍照处理图片");
                }
            }
        });

        // 复制结果按钮
        btnCopyResult.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyResultToClipboard();
            }
        });

        // 清除当前结果按钮
        btnClearCurrent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearCurrentResult();
            }
        });


        // 清空历史按钮
        btnClearHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearHistory();
            }
        });

        // 图片点击显示完整结果
        ivPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tvImageResult.getVisibility() == View.VISIBLE) {
                    // 如果图片上有预览文字，点击切换显示/隐藏
                    tvImageResult.setVisibility(View.GONE);
                } else if (tvImageResult.getVisibility() == View.GONE &&
                        !TextUtils.isEmpty(tvImageResult.getText())) {
                    // 如果有内容但隐藏了，点击显示
                    tvImageResult.setVisibility(View.VISIBLE);
                }
            }
        });
    }

//    // 添加上传按钮点击监听
//    private void setupUploadButton() {
//        Button btnUpload = findViewById(R.id.btn_upload); // 需要在布局中添加这个按钮
//        if (btnUpload != null) {
//            btnUpload.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    if (currentProcessedBitmap != null) {
//                        uploadImageToServer();
//                    } else {
//                        Toast.makeText(MainActivity.this, "请先拍照处理图片", Toast.LENGTH_SHORT).show();
//                    }
//                }
//            });
//            // 初始隐藏上传按钮
//            showUploadButton(false);
//        }
//    }

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
            btnUpload.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            btnUpload.setEnabled(true);
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
//                Toast.makeText(this, "需要相机和存储权限才能使用此功能", Toast.LENGTH_LONG).show();
                showToast("需要相机和存储权限才能使用此功能");
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
        showUploadButton(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap processedBitmap = ImageProcessor.processImage(
                            photoPath, ImageProcessor.ProcessType.GRAYSCALE);

                    if (processedBitmap != null) {
                        // 保存处理后的图片用于显示和上传
                        currentProcessedBitmap = processedBitmap;
                        currentPhotoPath = photoPath;

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

//                tvStatus.setVisibility(View.VISIBLE);

                // 显示上传按钮
                showUploadButton(true);

//                Toast.makeText(this, "照片已保存到相册", Toast.LENGTH_SHORT).show();
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
//            Toast.makeText(this, "没有可上传的图片", Toast.LENGTH_SHORT).show();
            showToast("没有可上传的图片");
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

        // 显示在当前结果区域
        showCurrentResult(displayText);

        // 添加到历史记录
        addToHistory(displayText, currentPhotoPath);

        // 在图片上显示简要预览
        showImagePreview(displayText);

        tvStatus.setText("识别完成");

//
//        tvStatus.setText("识别结果: " + displayText);
//        Toast.makeText(this, "识别完成: " + displayText, Toast.LENGTH_LONG).show();
//
//        // 可以在这里添加更多结果展示逻辑
        // 比如显示识别框、标签等
    }

    private void showCurrentResult(String result) {
        // 显示当前结果区域
        llCurrentResult.setVisibility(View.VISIBLE);
        tvCurrentResult.setText(result);

        // 自动滚动到结果区域
        llCurrentResult.post(new Runnable() {
            @Override
            public void run() {
                llCurrentResult.requestFocus();
            }
        });
    }


    private void addToHistory(String result, String imagePath) {
        // 创建时间戳
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

        // 创建历史记录项
        RecognitionResult historyItem = new RecognitionResult(timestamp, result, imagePath);
        recognitionHistory.add(0, historyItem); // 添加到开头（最新在前）

        // 更新历史记录显示
        updateHistoryDisplay();
    }


    private void updateHistoryDisplay() {
        // 清空现有视图
        llHistoryContainer.removeAllViews();

        // 添加历史记录项
        for (int i = 0; i < Math.min(recognitionHistory.size(), 10); i++) { // 最多显示10条
            RecognitionResult item = recognitionHistory.get(i);
            addHistoryItemView(item, i);
        }

        // 自动滚动到顶部
        scrollHistory.post(new Runnable() {
            @Override
            public void run() {
                scrollHistory.fullScroll(ScrollView.FOCUS_UP);
            }
        });
    }


    private void addHistoryItemView(RecognitionResult item, int position) {
        // 创建历史记录项视图
        View historyView = LayoutInflater.from(this).inflate(R.layout.item_history, llHistoryContainer, false);

        TextView tvTime = historyView.findViewById(R.id.tv_history_time);
        TextView tvContent = historyView.findViewById(R.id.tv_history_content);
        Button btnCopy = historyView.findViewById(R.id.btn_history_copy);

        tvTime.setText(item.timestamp);

        // 限制显示长度
        String displayContent = item.result;
        if (displayContent.length() > 100) {
            displayContent = displayContent.substring(0, 100) + "...";
        }
        tvContent.setText(displayContent);

        // 复制按钮
        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyTextToClipboard(item.result);
                showToast("已复制到剪贴板");
            }
        });

        // 点击项显示完整内容
        historyView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showResultDetailDialog(item);
            }
        });

        llHistoryContainer.addView(historyView);
    }


    private void showImagePreview(String result) {
        // 在图片上显示简要预览
        String previewText = result;
        if (previewText.length() > 50) {
            previewText = previewText.substring(0, 50) + "...";
        }

        tvImageResult.setText(previewText);
        tvImageResult.setVisibility(View.VISIBLE);
    }

    private void copyResultToClipboard() {
        String currentText = tvCurrentResult.getText().toString();
        if (!TextUtils.isEmpty(currentText)) {
            copyTextToClipboard(currentText);
            showToast("结果已复制到剪贴板");
        }
    }

    private void copyTextToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("识别结果", text);
        clipboard.setPrimaryClip(clip);
    }

    private void clearCurrentResult() {
        llCurrentResult.setVisibility(View.GONE);
        tvCurrentResult.setText("");
        tvImageResult.setVisibility(View.GONE);
        showToast("当前结果已清除");
    }

    private void clearHistory() {
        recognitionHistory.clear();
        llHistoryContainer.removeAllViews();
        showToast("历史记录已清空");
    }


    private void showResultDetailDialog(RecognitionResult item) {
        // 创建详细结果对话框
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("识别结果 - " + item.timestamp);

        // 创建滚动文本框
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        textView.setText(item.result);
        textView.setPadding(32, 32, 32, 32);
        textView.setTextSize(14);
        textView.setLineSpacing(0, 1.2f);
        scrollView.addView(textView);

        builder.setView(scrollView);
        builder.setPositiveButton("复制", (dialog, which) -> {
            copyTextToClipboard(item.result);
            showToast("已复制到剪贴板");
        });
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    private void showUploadButton(boolean show) {
        if (btnUpload != null) {
            btnUpload.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }






    private String parseRecognitionResult(String jsonResult) {
        try {
            // 这里根据你的服务器返回格式进行解析
            // 假设返回的是纯文本或简单JSON
            if (jsonResult.startsWith("{") && jsonResult.contains("\"result\"")) {
                // JSON格式
                org.json.JSONObject json = new org.json.JSONObject(jsonResult);
                return json.optString("result", jsonResult);
            } else {
                // 纯文本格式
                return jsonResult;
            }
        } catch (Exception e) {
            return jsonResult; // 返回原始文本
        }
    }



    private void showError(String message) {
        showProcessing(false);
        showUploading(false);
        tvStatus.setText(message);
        tvStatus.setVisibility(View.VISIBLE);
        showToast(message);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void notifyGallery(String imagePath) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(imagePath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        sendBroadcast(mediaScanIntent);
    }
}
