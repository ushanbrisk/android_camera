package com.example.myapplication2.utils;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpUtils {
    private static final String TAG = "HttpUtils";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient client = new OkHttpClient();

    // 服务器识别接口URL - 请替换为你的实际URL
    private static final String SERVER_URL = "http://192.168.3.13:5000/api/recognize";

    public interface RecognitionCallback {
        void onSuccess(String result);
        void onError(String error);
    }

    public static void uploadImageForRecognition(Bitmap bitmap, String imageName, RecognitionCallback callback) {
        new Thread(() -> {
            try {
                // 将Bitmap转换为base64
                String imageBase64 = bitmapToBase64(bitmap);

//                // 构建JSON请求体
//                String json = "{\"image\": \"" + imageBase64 + "\", \"filename\": \"" + imageName + "\"}";
//                RequestBody body = RequestBody.create(json, JSON);
//

                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("image", imageBase64);
                    jsonObject.put("filename", imageName);
                } catch(JSONException e) {
                    LogToFileUtils.e(TAG, "构建JSON对象失败: " + e.getMessage());
                    callback.onError("数据格式错误");
                    return;
                }
                String json = jsonObject.toString();
                LogToFileUtils.d(TAG, "请求JSON长度: " +json.length());
                LogToFileUtils.d(TAG,"JSON前100字符: " + (json.length() > 100 ? json.substring(0,100) + "..." : json));
                RequestBody body = RequestBody.create(json, JSON);

                Request request = new Request.Builder()
                        .url(SERVER_URL)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        LogToFileUtils.e(TAG, "上传失败: " + e.getMessage());
                        callback.onError("网络连接失败: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String responseBody = response.body().string();
                        LogToFileUtils.d(TAG, "服务器响应状态: " + response.code());
                        LogToFileUtils.d(TAG, "服务器响应内容: " + responseBody);

                        if (response.isSuccessful()) {
                            callback.onSuccess(responseBody);
                        } else {
                            callback.onError("服务器错误: " + response.code() + ", 响应: " + responseBody);
                        }
                    }
                });

            } catch (Exception e) {
                LogToFileUtils.e(TAG, "上传异常: " + e.getMessage());
                callback.onError("处理失败: " + e.getMessage());
            }
        }).start();
    }

    private static String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
//        return Base64.encodeToString(byteArray, Base64.DEFAULT);
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }
}