# OfficeBox V1 功能基线

> 基线来源：`main` 分支当前前端入口、前端 API 调用、后端实现与项目公开文档交叉核对。
>
> 原则：代码中存在 Controller/Service，但没有产品入口或没有完成验证的功能，不计入“V1 正式功能”。

## V1 正式功能

### PDF

- 合并 PDF
- 拆分 PDF
- 压缩 PDF
- 旋转 PDF
- 加密 PDF
- 解密 PDF

### 图片

- 图片压缩
- 图片尺寸调整
- JPG/JPEG/PNG/WebP 图片处理
- 图片格式转换（当前 UI 主要提供 JPG/PNG 输出）

### Office

- Word → PDF
- Excel → PDF
- PowerPoint → PDF
- 批量 Office → PDF

### OCR

- 图片 OCR
- PDF OCR
- OCR 结果编辑
- OCR 结果复制
- TXT 下载
- 生成可搜索 PDF
- 中文 + English OCR

## 后端存在但暂不计入 V1 正式能力

以下功能在代码层可能存在相关 Controller 或 Service，但当前基线不把它们视为 V1 正式产品能力，直到前端入口和实际测试均确认：

- PDF 水印
- PDF 页面高级编辑
- PDF 安全相关扩展能力
- PDF 元数据/高级工具
- 其他仅后端暴露、前端未开放的接口

## V2 原则

V2 不重复重写上述 V1 功能。已有 V1 Engine 应逐步接入统一 Task/Storage 架构：

```text
Upload → Task → Processing → Result → Cleanup
```

V2 新能力优先级：

1. PDF → Word / Excel / PPT
2. PDF 签名、水印、修复、PDF/A
3. 图片高级格式与批处理
4. Office 双向/互转
5. OCR 版面分析、表格识别、结构化输出
6. AI 文档总结、翻译、问答、合同/发票理解

## V2 工程基础

- `main`：V1 稳定线
- `develop`：V2 开发线
- 统一 API Response
- 统一异常处理
- Task 生命周期
- 文件 Storage 与自动清理
- 测试与 CI 质量门禁
