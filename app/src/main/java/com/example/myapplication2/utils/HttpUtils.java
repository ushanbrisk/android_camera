package com.example.myapplication2.utils;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLHandshakeException;

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
    private static final String SERVER_URL = "http://192.168.3.8:8000/v1/chat/completions";

    public interface RecognitionCallback {
        void onSuccess(String result);
        void onError(String error);
    }

    public static void uploadImageForRecognition(Bitmap bitmap, String imageName, RecognitionCallback callback) {
        new Thread(() -> {
            try {
                LogToFileUtils.d(TAG, "开始图片上传流程...");
                LogToFileUtils.d(TAG, "图片尺寸: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                LogToFileUtils.d(TAG, "图片格式: " + bitmap.getConfig());

                // 检查网络连接
                if (!isNetworkAvailable()) {
                    LogToFileUtils.e(TAG, "网络不可用");
                    callback.onError("网络连接不可用");
                    return;
                }

                // 将Bitmap转换为base64
                String imageBase64 = bitmapToBase64(bitmap);
                LogToFileUtils.d(TAG, "图片Base64长度: " + imageBase64.length());
                LogToFileUtils.d(TAG, "Base64前100字符: " + (imageBase64.length() > 100 ? imageBase64.substring(0, 100) : imageBase64));

                // 构建JSON请求体
                JSONObject requestBody = buildRequestJson(imageBase64);
                String json = requestBody.toString();

                LogToFileUtils.d(TAG, "请求JSON长度: " + json.length());
                LogToFileUtils.d(TAG, "JSON前200字符: " + (json.length() > 200 ? json.substring(0, 200) + "..." : json));

                // 检查请求大小
                if (json.length() > 10 * 1024 * 1024) { // 10MB限制
                    LogToFileUtils.e(TAG, "请求数据过大: " + (json.length() / 1024 / 1024) + "MB");
                    callback.onError("图片过大，请压缩后重试");
                    return;
                }

                RequestBody body = RequestBody.create(json, JSON);
                Request request = buildRequest(body);

                LogToFileUtils.d(TAG, "发送请求到: " + SERVER_URL);
                LogToFileUtils.d(TAG, "请求头: " + request.headers().toString());

                // 添加超时控制
                OkHttpClient clientWithTimeout = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS)    // 上传超时
                        .readTimeout(60, TimeUnit.SECONDS)      // 读取超时
                        .build();

                clientWithTimeout.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        LogToFileUtils.e(TAG, "网络请求失败: " + e.getMessage());
                        LogToFileUtils.e(TAG, "失败类型: " + e.getClass().getSimpleName());

                        // 详细错误分析
                        if (e instanceof SocketTimeoutException) {
                            callback.onError("请求超时，请检查网络连接");
                        } else if (e instanceof ConnectException) {
                            callback.onError("无法连接到服务器，请检查服务器地址");
                        } else if (e instanceof SSLHandshakeException) {
                            callback.onError("SSL握手失败");
                        } else {
                            callback.onError("网络错误: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String responseBody = response.body().string();
                        LogToFileUtils.d(TAG, "服务器响应状态: " + response.code());
                        LogToFileUtils.d(TAG, "响应头: " + response.headers().toString());
                        LogToFileUtils.d(TAG, "响应体长度: " + responseBody.length());
                        LogToFileUtils.d(TAG, "响应体内容: " + responseBody);

                        if (response.isSuccessful()) {
                            handleSuccessResponse(responseBody, callback);
                        } else {
                            handleErrorResponse(response, responseBody, callback);
                        }
                    }
                });

            } catch (Exception e) {
                LogToFileUtils.e(TAG, "上传异常: " + e.getMessage());
                callback.onError("处理失败: " + e.getMessage());
            }
        }).start();
    }

    // 分离出来的辅助方法
    private static JSONObject buildRequestJson(String imageBase64) throws JSONException {
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "qwen2.5-vl-7b");

        JSONArray messagesArray = new JSONArray();
        JSONObject messageObject = new JSONObject();
        messageObject.put("role", "user");

        JSONArray contentArray = new JSONArray();

        JSONObject textContent = new JSONObject();
        textContent.put("type", "text");
        textContent.put("text", "描述这张图片的内容");
        contentArray.put(textContent);

        JSONObject imageContent = new JSONObject();
        imageContent.put("type", "image_url");

        JSONObject imageUrlObject = new JSONObject();
        imageUrlObject.put("url", "data:image/jpeg;base64," + imageBase64);
        imageContent.put("image_url", imageUrlObject);
        contentArray.put(imageContent);

        messageObject.put("content", contentArray);
        messagesArray.put(messageObject);
        requestBody.put("messages", messagesArray);
        requestBody.put("max_tokens", 300);
        requestBody.put("temperature", 0.7);

        return requestBody;
    }

    private static Request buildRequest(RequestBody body) {
        return new Request.Builder()
                .url(SERVER_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "Android-QwenClient/1.0")
                .build();
    }

    private static void handleSuccessResponse(String responseBody, RecognitionCallback callback) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            if (jsonResponse.has("choices")) {
                JSONArray choices = jsonResponse.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject message = choice.getJSONObject("message");
                    String content = message.getString("content");
                    LogToFileUtils.d(TAG, "解析成功，回复内容长度: " + content.length());
                    callback.onSuccess(content);
                } else {
                    LogToFileUtils.e(TAG, "响应中没有choices数据");
                    callback.onError("服务器返回数据格式错误");
                }
            } else if (jsonResponse.has("error")) {
                String error = jsonResponse.getString("error");
                LogToFileUtils.e(TAG, "服务器返回错误: " + error);
                callback.onError("服务器错误: " + error);
            } else {
                LogToFileUtils.e(TAG, "响应格式异常: " + responseBody);
                callback.onError("响应格式异常");
            }
        } catch (JSONException e) {
            LogToFileUtils.e(TAG, "响应解析错误: " + e.getMessage());
            callback.onError("响应解析失败: " + e.getMessage());
        }
    }

    private static void handleErrorResponse(Response response, String responseBody, RecognitionCallback callback) {
        LogToFileUtils.e(TAG, "HTTP错误状态: " + response.code());

        switch (response.code()) {
            case 400:
                callback.onError("请求参数错误（400）");
                break;
            case 413:
                callback.onError("图片太大，服务器拒绝处理（413）");
                break;
            case 500:
                callback.onError("服务器内部错误（500）");
                break;
            case 502:
            case 503:
            case 504:
                callback.onError("服务器暂时不可用（" + response.code() + "）");
                break;
            default:
                callback.onError("服务器错误: " + response.code() + ", 响应: " + responseBody);
        }
    }

    // 网络检查方法
    private static boolean isNetworkAvailable() {
        try {
            // 简单的网络检查
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec("ping -c 1 8.8.8.8");
            int exitValue = process.waitFor();
            return exitValue == 0;
        } catch (Exception e) {
            return false;
        }
    }


            // 纯文本请求方法（可选）
    public static void sendTextRequest(String prompt, RecognitionCallback callback) {
        new Thread(() -> {
            try {
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", "qwen2.5-vl-7b");

                JSONArray messagesArray = new JSONArray();
                JSONObject messageObject = new JSONObject();
                messageObject.put("role", "user");
                messageObject.put("content", prompt);
                messagesArray.put(messageObject);

                requestBody.put("messages", messagesArray);
                requestBody.put("max_tokens", 300);
                requestBody.put("temperature", 0.7);

                String json = requestBody.toString();
                RequestBody body = RequestBody.create(json, JSON);

                Request request = new Request.Builder()
                        .url(SERVER_URL)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        callback.onError("网络连接失败: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String responseBody = response.body().string();
                        if (response.isSuccessful()) {
                            try {
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                JSONArray choices = jsonResponse.getJSONArray("choices");
                                JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                                String content = message.getString("content");
                                callback.onSuccess(content);
                            } catch (JSONException e) {
                                callback.onError("响应解析失败");
                            }
                        } else {
                            callback.onError("服务器错误: " + response.code());
                        }
                    }
                });

            } catch (Exception e) {
                callback.onError("处理失败: " + e.getMessage());
            }
        }).start();
    }






    private static String bitmapToBase64(Bitmap bitmap) {
        // 先压缩图片
        Bitmap compressedBitmap = compressBitmap(bitmap);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();

        LogToFileUtils.d(TAG, "压缩后图片大小: " + (byteArray.length / 1024) + "KB");

        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }

    private static Bitmap compressBitmap(Bitmap originalBitmap) {
        int maxWidth = 1024;  // 最大宽度
        int maxHeight = 1024; // 最大高度

        int width = originalBitmap.getWidth();
        int height = originalBitmap.getHeight();

        LogToFileUtils.d(TAG, "原始图片尺寸: " + width + "x" + height);

        // 如果图片尺寸合适，直接返回
        if (width <= maxWidth && height <= maxHeight) {
            return originalBitmap;
        }

        // 计算缩放比例
        float scale = Math.min((float) maxWidth / width, (float) maxHeight / height);
        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);

        LogToFileUtils.d(TAG, "压缩后尺寸: " + newWidth + "x" + newHeight + ", 缩放比例: " + scale);

        return Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true);
    }



}