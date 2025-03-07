import asyncio
import websockets
import json
import logging
from typing import Optional, Dict, Any

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class AnimationClient:
    def __init__(self, url: str = "ws://localhost:8080/rpc"):
        self.url = url
        self.ws = None

    async def connect(self):
        """连接到 WebSocket 服务器"""
        try:
            self.ws = await websockets.connect(self.url)
            logger.info("Connected to RPC server")
            return True
        except Exception as e:
            logger.error(f"Failed to connect: {e}")
            return False

    async def close(self):
        """关闭连接"""
        if self.ws:
            await self.ws.close()
            logger.info("Disconnected from RPC server")

    async def send_rpc(self, action: str, data: Dict[str, Any]) -> Dict[str, Any]:
        """发送 RPC 请求并等待响应"""
        if not self.ws:
            raise RuntimeError("Not connected to server")

        message = {
            "action": action,
            "data": data
        }
        
        try:
            await self.ws.send(json.dumps(message))
            response = await self.ws.recv()
            return json.loads(response)
        except Exception as e:
            logger.error(f"RPC error: {e}")
            raise

    async def play_animation(self, name: str, trans_time: int = 0, entity_id: Optional[int] = None):
        """播放动画"""
        data = {
            "name": name,
            "transTime": trans_time
        }
        if entity_id is not None:
            data["entityId"] = entity_id
        
        return await self.send_rpc("playAnimation", data)

    async def replace_state_animation(self, state: str, animation: str, entity_id: Optional[int] = None):
        """替换状态动画"""
        data = {
            "state": state,
            "animation": animation
        }
        if entity_id is not None:
            data["entityId"] = entity_id
        
        return await self.send_rpc("replaceStateAnimation", data)

    async def blend_animations(self, anim1: str, anim2: str, weight: float = 0.5, entity_id: Optional[int] = None):
        """混合动画"""
        data = {
            "anim1": anim1,
            "anim2": anim2,
            "weight": weight
        }
        if entity_id is not None:
            data["entityId"] = entity_id
        
        return await self.send_rpc("blendAnimations", data)

    async def load_model(self, path: str):
        """加载模型"""
        return await self.send_rpc("loadModel", {"path": path})

async def main():
    # 使用示例
    client = AnimationClient()
    
    try:
        # 连接服务器
        if not await client.connect():
            return

        # 加载模型
        response = await client.load_model("minecraft:wukong")
        logger.info(f"Load model response: {response}")

        # 播放动画
        response = await client.play_animation("UseAnim/crossbow_right", trans_time=0)
        logger.info(f"Play animation response: {response}")

        # 等待一段时间
        await asyncio.sleep(2)

        # 混合动画
        response = await client.blend_animations("EntityState/walk", "run", weight=0.7)
        logger.info(f"Blend animations response: {response}")

    except Exception as e:
        logger.error(f"Error during animation test: {e}")
    finally:
        await client.close()

if __name__ == "__main__":
    asyncio.run(main())