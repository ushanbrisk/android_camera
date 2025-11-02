import requests
import base64
import json

def simple_test():
    """简化版测试函数"""
    # 服务器地址
    server_url = "http://localhost:5000"
    
    # 测试图片路径（修改为你的图片路径）
    image_path = "flight.jpg"
    
    try:
        # 读取并编码图片
        with open(image_path, "rb") as f:
            image_data = base64.b64encode(f.read()).decode('utf-8')
        
        # 准备请求数据
        payload = {
            "image": image_data,
            "filename": "test_image.jpg"
        }
        
        print("📤 发送请求到服务器...")
        response = requests.post(
            f"{server_url}/api/recognize",
            json=payload,
            timeout=30
        )
        
        print(f"📥 服务器响应状态: {response.status_code}")
        
        if response.status_code == 200:
            result = response.json()
            print("✅ 识别结果:")
            print(json.dumps(result, indent=2, ensure_ascii=False))
        else:
            print(f"❌ 请求失败: {response.text}")
            
    except Exception as e:
        print(f"❌ 测试失败: {e}")

if __name__ == "__main__":
    simple_test()
