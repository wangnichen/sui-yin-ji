// 把端侧离线识别运行时打包为单个 ESM 模块：assets/offline-ai/offline-ai.mjs
// 包含：PaddleOCR.js(PP-OCR-Tiny) + ONNX Runtime Web + OpenCV.js + wllama(llama.cpp WASM)
// 全部本地运行，不请求任何云端接口。
import { build } from 'esbuild';
import { fileURLToPath } from 'url';
import path from 'path';

const toolsDir = path.dirname(fileURLToPath(import.meta.url));
const out = path.resolve(toolsDir, '../assets/offline-ai/offline-ai.mjs');

const entry = `
import { PaddleOCR } from '@paddleocr/paddleocr-js';
import { Wllama } from '@wllama/wllama';
import * as ort from 'onnxruntime-web';
// 运行时默认离线化：ORT 的 wasm 指向本地目录，强制单线程（兼容 WebView/未隔离页面）
try {
  ort.env.wasm.wasmPaths = new URL('./ocr/ort/', import.meta.url).href;
  ort.env.wasm.numThreads = 1;
} catch (e) { console.warn('[offline-ai] ort env init', e); }
export { PaddleOCR, Wllama, ort };
`;

await build({
  stdin: { contents: entry, resolveDir: toolsDir, loader: 'js' },
  bundle: true,
  format: 'esm',
  platform: 'browser',
  minify: true,
  plugins: [{
    name: 'stub-node-builtins',
    setup(b) {
      b.onResolve({ filter: /^(fs|path)$/ }, () => ({ path: 'node-builtin-stub', namespace: 'stub' }));
      b.onLoad({ filter: /.*/, namespace: 'stub' }, () => ({ contents: 'module.exports = {};', loader: 'js' }));
    },
  }],
  legalComments: 'none',
  outfile: out,
  logLevel: 'info',
  define: { 'process.env.NODE_ENV': '"production"' },
});

console.log('offline-ai bundle ->', out);
