# SparkCore Web 服务使用指南

## 1. 快速开始

### 1.1 启用 Web 服务

1. **编辑配置文件**

   打开 `config/spark_core-common.toml` 文件，确保以下配置：

   ```toml
   [webServer]
       # 启用 Web 服务器
       enableWebServer = true
       # 设置端口（默认 8081）
       webServerPort = 8081
       # 启用跨域支持（用于 Web 前端）
       webServerCorsEnabled = true
   ```

2. **启动游戏**

   启动 Minecraft 客户端或服务器，Web 服务将自动启动。

3. **验证服务**

   在浏览器中访问 `http://localhost:8081/health`，应该看到：
   ```json
   {
       "success": true,
       "data": "OK",
       "message": "Web服务器运行正常"
   }
   ```

### 1.2 基础 API 测试

访问 `http://localhost:8081/api/v1/` 查看所有可用的 API 端点。

## 2. 常用场景

### 2.1 动画控制

#### 播放动画

```bash
curl -X POST http://localhost:8081/api/v1/animation/play \
  -H "Content-Type: application/json" \
  -d '{
    "name": "idle",
    "transTime": 1000,
    "entityId": 123
  }'
```

#### 混合动画

```bash
curl -X POST http://localhost:8081/api/v1/animation/blend \
  -H "Content-Type: application/json" \
  -d '{
    "anim1": "idle",
    "anim2": "walk",
    "weight": 0.5
  }'
```

### 2.2 模型管理

#### 加载自定义模型

```bash
curl -X POST http://localhost:8081/api/v1/model/load \
  -H "Content-Type: application/json" \
  -d '{
    "path": "spark_core:custom_player_model",
    "entityId": 123
  }'
```

### 2.3 资源查询

#### 获取所有资源

```bash
curl http://localhost:8081/api/v1/resources/list
```

#### 查看注册表状态

```bash
curl http://localhost:8081/api/v1/resources/registries
```

### 2.4 日志管理

#### 搜索日志

```bash
curl -X POST http://localhost:8081/api/v1/logs/search \
  -H "Content-Type: application/json" \
  -d '{
    "level": "INFO",
    "regex": ".*动画.*",
    "maxLines": 50
  }'
```

#### 获取日志统计

```bash
curl http://localhost:8081/api/v1/logs/stats
```

## 3. Web 前端集成

### 3.1 JavaScript 示例

```html
<!DOCTYPE html>
<html>
<head>
    <title>SparkCore 控制面板</title>
</head>
<body>
    <h1>SparkCore 动画控制</h1>
    
    <div>
        <input type="text" id="animName" placeholder="动画名称" value="idle">
        <input type="number" id="transTime" placeholder="过渡时间" value="1000">
        <button onclick="playAnimation()">播放动画</button>
    </div>
    
    <div id="result"></div>

    <script>
        const API_BASE = 'http://localhost:8081/api/v1';
        
        async function playAnimation() {
            const name = document.getElementById('animName').value;
            const transTime = parseInt(document.getElementById('transTime').value);
            
            try {
                const response = await fetch(`${API_BASE}/animation/play`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({
                        name: name,
                        transTime: transTime
                    })
                });
                
                const result = await response.json();
                document.getElementById('result').innerHTML = 
                    `<pre>${JSON.stringify(result, null, 2)}</pre>`;
                    
            } catch (error) {
                document.getElementById('result').innerHTML = 
                    `<div style="color: red;">错误: ${error.message}</div>`;
            }
        }
    </script>
</body>
</html>
```

### 3.2 React 组件示例

```jsx
import React, { useState } from 'react';

const SparkCoreController = () => {
    const [animName, setAnimName] = useState('idle');
    const [transTime, setTransTime] = useState(1000);
    const [result, setResult] = useState(null);
    const [loading, setLoading] = useState(false);

    const playAnimation = async () => {
        setLoading(true);
        try {
            const response = await fetch('http://localhost:8081/api/v1/animation/play', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    name: animName,
                    transTime: transTime
                })
            });
            
            const data = await response.json();
            setResult(data);
        } catch (error) {
            setResult({ success: false, message: error.message });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div>
            <h2>SparkCore 动画控制</h2>
            <div>
                <input 
                    type="text" 
                    value={animName} 
                    onChange={(e) => setAnimName(e.target.value)}
                    placeholder="动画名称"
                />
                <input 
                    type="number" 
                    value={transTime} 
                    onChange={(e) => setTransTime(parseInt(e.target.value))}
                    placeholder="过渡时间"
                />
                <button onClick={playAnimation} disabled={loading}>
                    {loading ? '播放中...' : '播放动画'}
                </button>
            </div>
            
            {result && (
                <div style={{ 
                    marginTop: '20px', 
                    padding: '10px', 
                    backgroundColor: result.success ? '#d4edda' : '#f8d7da',
                    border: `1px solid ${result.success ? '#c3e6cb' : '#f5c6cb'}`,
                    borderRadius: '4px'
                }}>
                    <pre>{JSON.stringify(result, null, 2)}</pre>
                </div>
            )}
        </div>
    );
};

export default SparkCoreController;
```

## 4. 移动应用集成

### 4.1 Android (Kotlin) 示例

```kotlin
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.net.http.*
import java.net.URI

@Serializable
data class AnimationPlayRequest(
    val name: String,
    val transTime: Int = 0,
    val entityId: Int? = null
)

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T,
    val message: String,
    val timestamp: String
)

class SparkCoreApiClient {
    private val baseUrl = "http://192.168.1.100:8081/api/v1"  // 替换为实际 IP
    private val httpClient = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun playAnimation(name: String, transTime: Int = 1000): ApiResponse<Boolean> {
        return withContext(Dispatchers.IO) {
            val request = AnimationPlayRequest(name, transTime)
            val requestBody = json.encodeToString(request)
            
            val httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/animation/play"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
            
            val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            json.decodeFromString<ApiResponse<Boolean>>(response.body())
        }
    }
}

// 使用示例
class MainActivity : AppCompatActivity() {
    private val apiClient = SparkCoreApiClient()
    
    private fun playIdleAnimation() {
        lifecycleScope.launch {
            try {
                val result = apiClient.playAnimation("idle", 1000)
                if (result.success) {
                    showToast("动画播放成功")
                } else {
                    showToast("动画播放失败: ${result.message}")
                }
            } catch (e: Exception) {
                showToast("网络错误: ${e.message}")
            }
        }
    }
}
```

### 4.2 iOS (Swift) 示例

```swift
import Foundation

struct AnimationPlayRequest: Codable {
    let name: String
    let transTime: Int
    let entityId: Int?
    
    init(name: String, transTime: Int = 0, entityId: Int? = nil) {
        self.name = name
        self.transTime = transTime
        self.entityId = entityId
    }
}

struct ApiResponse<T: Codable>: Codable {
    let success: Bool
    let data: T
    let message: String
    let timestamp: String
}

class SparkCoreApiClient {
    private let baseURL = "http://192.168.1.100:8081/api/v1"  // 替换为实际 IP
    
    func playAnimation(name: String, transTime: Int = 1000) async throws -> ApiResponse<Bool> {
        guard let url = URL(string: "\(baseURL)/animation/play") else {
            throw URLError(.badURL)
        }
        
        let request = AnimationPlayRequest(name: name, transTime: transTime)
        let requestData = try JSONEncoder().encode(request)
        
        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = "POST"
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.httpBody = requestData
        
        let (data, _) = try await URLSession.shared.data(for: urlRequest)
        let response = try JSONDecoder().decode(ApiResponse<Bool>.self, from: data)
        
        return response
    }
}

// 使用示例
class ViewController: UIViewController {
    private let apiClient = SparkCoreApiClient()
    
    @IBAction func playIdleAnimation(_ sender: UIButton) {
        Task {
            do {
                let result = try await apiClient.playAnimation(name: "idle", transTime: 1000)
                
                DispatchQueue.main.async {
                    if result.success {
                        self.showAlert(title: "成功", message: "动画播放成功")
                    } else {
                        self.showAlert(title: "失败", message: result.message)
                    }
                }
            } catch {
                DispatchQueue.main.async {
                    self.showAlert(title: "错误", message: "网络请求失败: \(error.localizedDescription)")
                }
            }
        }
    }
}
```

## 5. 故障排除

### 5.1 常见问题

#### 问题 1: Web 服务器无法启动

**症状**: 游戏启动后无法访问 Web API

**解决方案**:
1. 检查配置文件中 `enableWebServer = true`
2. 确认端口 8081 没有被其他程序占用
3. 查看游戏日志中的错误信息

#### 问题 2: CORS 错误

**症状**: Web 前端无法访问 API，浏览器控制台显示 CORS 错误

**解决方案**:
1. 确保配置中 `webServerCorsEnabled = true`
2. 检查请求的 Origin 是否被允许

#### 问题 3: API 返回 503 错误

**症状**: 动画和模型 API 返回 "服务器环境不可用"

**解决方案**:
1. 确保游戏世界已加载
2. 确保有玩家在线
3. 检查服务器是否正常运行

### 5.2 调试技巧

#### 启用详细日志

在配置文件中添加：
```toml
[logging]
    webServerDebug = true
```

#### 使用调试端点

- `/api/v1/debug/info` - 查看系统状态
- `/api/v1/debug/config` - 查看当前配置
- `/api/v1/logs/search` - 搜索相关日志

#### 网络连接测试

```bash
# 测试基础连接
curl -v http://localhost:8081/health

# 测试 JSON 解析
curl -X POST http://localhost:8081/api/v1/animation/play \
  -H "Content-Type: application/json" \
  -d '{"name":"test"}' \
  -v
```

## 6. 安全注意事项

### 6.1 网络安全

- **本地访问**: 默认只绑定到 localhost，外部无法直接访问
- **防火墙**: 如需外部访问，请配置防火墙规则
- **HTTPS**: 生产环境建议使用反向代理添加 HTTPS

### 6.2 访问控制

- **认证**: 当前版本不包含认证机制，仅适用于受信任环境
- **授权**: 所有 API 端点都是公开的，请谨慎暴露到公网

### 6.3 最佳实践

1. **仅在需要时启用**: 不使用 Web API 时建议关闭
2. **监控日志**: 定期检查访问日志和错误日志
3. **网络隔离**: 在隔离的网络环境中使用
