from flask import Flask, request, jsonify
import base64
import cv2
import numpy as np
from PIL import Image
import io
import os
import logging
from datetime import datetime
import time

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('server.log'),  # 输出到文件
        logging.StreamHandler()  # 输出到控制台
    ]
)

app = Flask(__name__)

@app.before_request
def log_request_info():
    """在每个请求之前记录客户端信息"""
    logging.info(f"客户端连接 - IP: {request.remote_addr} | 方法: {request.method} | 路径: {request.path}")

@app.after_request
def log_response_info(response):
    """在每个请求之后记录响应信息"""
    logging.info(f"请求完成 - IP: {request.remote_addr} | 状态码: {response.status_code}")
    return response

@app.route('/api/recognize', methods=['POST'])
def recognize_image():
    start_time = time.time()
    client_ip = request.remote_addr

    
    try:
        # 添加更健壮的JSON解析
        if not request.content_type or 'application/json' not in request.content_type:
            return jsonify({"success": False, "error": "Content-Type必须是application/json"}), 400

        raw_data = request.get_data(as_text=True)
        if raw_data:
            logging.info(f"接收到的原始数据前200字符:{raw_data[:200]}")

        try:
            data = request.get_json()
        except Exception as json_error:
            logging.error(f"JSON解析失败: {str(json_error)}")
            #尝试清理数据
            if raw_data:
                #移除可能的问题字符
                import re
                cleaned_data = re.sub(r'[\x00-\x1f\x7f]', '', raw_data)
                try:
                    import json
                    data = json.loads(cleaned_data)
                    logging.info("清理后JSON解析成功")
                except Exception as e2:
                    logging.error(f"清理后JSON仍然解析失败: {str(e2)}")
                    return jsonify({"success":False, "error": f"无效的JSON格式: {str(json_error)}"}), 400
        if not data:
            return jsonify({"success": False, "error": "请求体为空或不是有效的JSON"}), 400
        
        filename = data.get('filename', 'unknown')
        image_data = data.get('image', '')

        if not image_data:
            return jsonify({"success": False, "error": "image字段不能为空"}), 400

        logging.info(f"开始处理图像识别 - 客户端IP: {client_ip} | 文件名: {filename} | 数据长度: {len(image_data)}")

        # 解码base64图片
        try:
            # 确保base64数据没有换行符和空格
            image_data_clean = image_data.replace(' ', '+').replace('\n', '').replace('\r', '')
            image_bytes = base64.b64decode(image_data_clean)
            image = Image.open(io.BytesIO(image_bytes))
            image_np = np.array(image)

            # ========== 新增：保存图片到本地 ==========
            logging.info(f"saving image from {client_ip} to {filename} ")
            save_image_to_local(image, filename, client_ip)
            # ========================================

        except Exception as img_error:
            logging.error(f"图像解码失败: {str(img_error)}")
            return jsonify({"success": False, "error": f"图像数据格式错误: {str(img_error)}"}), 400

        # 记录图像信息
        logging.info(f"图像信息 - 尺寸: {image_np.shape} | 客户端IP: {client_ip}")

        # 模拟识别结果
        processing_time = time.time() - start_time
        result = {
            "success": True,
            "objects": ["猫", "沙发", "电视"],
            "confidence": [0.95, 0.87, 0.76],
            "message": "识别完成",
            "processing_time": f"{processing_time:.2f}秒"
        }

        logging.info(f"识别完成 - 客户端IP: {client_ip} | 处理时间: {processing_time:.2f}秒 | 识别结果: {result['objects']}")

        return jsonify(result)

    except Exception as e:
        processing_time = time.time() - start_time
        logging.error(f"识别失败 - 客户端IP: {client_ip} | 错误: {str(e)} | 处理时间: {processing_time:.2f}秒")
        return jsonify({"success": False, "error": str(e)}), 500


@app.route('/api/status', methods=['GET'])
def get_status():
    """获取服务器状态"""
    return jsonify({
        "status": "running",
        "start_time": "2024-01-15 10:30:25",
        "connections_served": "统计信息..."
    })




def save_image_to_local(image, original_filename, client_ip):
    """
    将接收到的图片保存到本地
    """
    try:
        # 创建保存目录（如果不存在）
        save_dir = "received_images"
        if not os.path.exists(save_dir):
            os.makedirs(save_dir)
            logging.info(f"创建图片保存目录: {save_dir}")
        
        # 处理文件名：确保安全且唯一
        # 移除路径分隔符等危险字符
        safe_filename = "".join(c for c in original_filename if c.isalnum() or c in ('-', '_', '.'))
        if not safe_filename:
            safe_filename = "unknown"
        
        # 添加时间戳和IP地址确保唯一性
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        ip_part = client_ip.replace('.', '_')
        
        # 确定文件扩展名
        if safe_filename.lower().endswith(('.png', '.jpg', '.jpeg', '.bmp', '.gif')):
            # 如果原文件名有扩展名就使用
            file_extension = safe_filename[safe_filename.rfind('.'):]
            final_filename = f"{safe_filename[:-len(file_extension)]}_{timestamp}_{ip_part}{file_extension}"
        else:
            # 默认使用jpg格式
            final_filename = f"{safe_filename}_{timestamp}_{ip_part}.jpg"
        
        # 完整的保存路径
        save_path = os.path.join(save_dir, final_filename)
        
        # 保存图片（根据原格式保存，如果没有扩展名则保存为JPEG）
        if original_filename.lower().endswith('.png'):
            image.save(save_path, 'PNG')
        else:
            image.save(save_path, 'JPEG')
        
        logging.info(f"图片保存成功 - 路径: {save_path} | 大小: {os.path.getsize(save_path)} 字节")
        
        return save_path
        
    except Exception as e:
        logging.error(f"保存图片失败: {str(e)}")
        return None

if __name__ == '__main__':
    logging.info("Flask 服务器启动...")
    app.run(host='0.0.0.0', port=5000, debug=True)
