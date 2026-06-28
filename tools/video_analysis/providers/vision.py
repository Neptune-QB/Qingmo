"""视觉理解 Provider 可插拔接口

支持：
- 豆包多模态（默认）
- 无视觉模型时返回空 caption
"""

from dataclasses import dataclass
from typing import Optional


@dataclass
class FrameCaption:
    """关键帧画面描述"""
    timestamp_ms: int
    caption: str


class VisionCaptionProvider:
    """视觉理解基础接口"""

    def caption(self, image_paths: list[str]) -> list[FrameCaption]:
        raise NotImplementedError

    @property
    def name(self) -> str:
        raise NotImplementedError


class NoOpVisionProvider(VisionCaptionProvider):
    """空视觉提供者：不做画面描述"""

    @property
    def name(self) -> str:
        return "noop"

    def caption(self, image_paths: list[str]) -> list[FrameCaption]:
        results = []
        for _path in image_paths:
            results.append(FrameCaption(
                timestamp_ms=0,
                caption="（画面描述暂未启用）",
            ))
        return results


class DoubaoVisionProvider(VisionCaptionProvider):
    """基于豆包多模态的画面描述（使用 Responses API）"""

    def __init__(self, api_key: str, model: str = "doubao-seed-2-0-lite-260428"):
        from openai import OpenAI
        self.client = OpenAI(api_key=api_key, base_url="https://ark.cn-beijing.volces.com/api/v3")
        self.model = model

    @property
    def name(self) -> str:
        return f"doubao-vision({self.model})"

    def caption(self, image_paths: list[str]) -> list[FrameCaption]:
        import re, base64
        results = []
        for img_path in image_paths:
            try:
                # 读取图片 → base64 编码
                with open(img_path, "rb") as f:
                    img_b64 = base64.b64encode(f.read()).decode("utf-8")

                response = self.client.responses.create(
                    model=self.model,
                    input=[{
                        "role": "user",
                        "content": [
                            {
                                "type": "input_image",
                                "image_url": f"data:image/jpeg;base64,{img_b64}",
                            },
                            {
                                "type": "input_text",
                                "text": "忽略画面中的备案号文字和观剧提示。只用一句话描述：画面中的人物在做什么、什么表情、什么场景氛围。",
                            },
                        ],
                    }],
                )
                caption = response.output_text or "（解析失败）"
            except Exception as e:
                caption = f"（画面描述失败: {e}）"

            # 从路径中提取时间戳
            ts_match = re.search(r'frame_(\d+)', img_path)
            ts = int(ts_match.group(1)) if ts_match else 0

            results.append(FrameCaption(timestamp_ms=ts, caption=caption))
        return results


def create_vision_provider(provider_type: str = "noop", **kwargs) -> VisionCaptionProvider:
    """工厂方法"""
    if provider_type == "noop" or not provider_type:
        return NoOpVisionProvider()
    elif provider_type == "doubao":
        api_key = kwargs.get("api_key") or ""
        if not api_key:
            raise ValueError("DOUBAO_API_KEY required for doubao vision provider")
        return DoubaoVisionProvider(api_key=api_key, model=kwargs.get("model", "doubao-seed-2-0-lite-260428"))
    else:
        raise ValueError(f"Unknown vision provider: {provider_type}")
