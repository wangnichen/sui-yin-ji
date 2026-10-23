# offline-ai 资产说明

端侧离线 AI 的运行时与小体积模型随仓库分发；LLM 主权重因体积过大（约 469MB）不入库，需按下述步骤手动放置。

## 随仓库分发（无需操作）

| 路径 | 说明 |
| --- | --- |
| `llm/wasm/wllama.wasm` | wllama 运行时（llama.cpp 的 WASM 移植） |
| `ocr/models/PP-OCRv6_tiny_det.tar` | PaddleOCR PP-OCRv6 tiny 检测模型 |
| `ocr/models/PP-OCRv6_tiny_rec.tar` | PaddleOCR PP-OCRv6 tiny 识别模型 |
| `ocr/ort/ort-wasm-simd-threaded.jsep.mjs/.wasm` | ONNX Runtime Web（SIMD / 多线程 / JSEP） |
| `offline-ai.mjs` | 端侧 AI 编排层（模型加载 / 推理调度 / OCR 管线） |

## 需手动放置：`llm/model.gguf`

1. 在 HuggingFace 或 ModelScope 搜索 **Qwen2.5-0.5B-Instruct GGUF q8_0** 量化版；
2. 下载后放置为 `android/app/src/main/assets/offline-ai/llm/model.gguf`；
3. 校验：文件字节数应与 [MainActivity.java](../../java/com/xiaozhangben/app/MainActivity.java) 中的
   `MODEL_ASSET_SIZE` 常量一致（`491400032`，约 469MB）；
4. 重新执行 `./build_apk.sh`，模型会随 APK 打包，首次启动由 Native 释放到应用私有目录。

> 说明：模型不随仓库分发是出于仓库体积与分发许可考虑；本应用所有推理均在端侧完成，无任何网络请求。
