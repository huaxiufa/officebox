# OfficeBox

一站式开源综合办公工具箱，面向日常办公文件处理，支持 PDF、Office、图片与 OCR，并提供 Docker 私有化部署能力。

## ✨ 当前能力

### PDF
- PDF 合并、拆分、删除页面、旋转
- PDF OCR 文字提取
- 扫描 PDF → 可搜索 PDF

### 图片
- 多张图片 → PDF
- PDF → PNG ZIP
- 图片批量选择与下载

### Office
- Office 转换工作台（按仓库当前实现提供的格式为准）

### OCR
- 图片 OCR
- PDF OCR
- 中文 `chi_sim` + English `eng`
- OCR 结果编辑、复制、TXT 下载
- 可搜索 PDF 输出

### Web
- Vue 3 + TypeScript + Vite
- 工具分类首页
- 工具搜索
- 拖拽文件入口
- 深色模式
- API 在线 / 离线状态检测（每 30 秒检查）
- `VITE_API_BASE_URL` 支持自定义后端地址

### 部署
- Java 21 + Spring Boot 3
- LibreOffice
- Tesseract OCR（中文 + English）
- Docker / Docker Compose
- 容器 `/api/health` 健康检查

## 🚀 Docker 快速启动

```bash
git clone https://github.com/huaxiufa/officebox.git
cd officebox
docker compose up -d --build
```

浏览器访问 `http://localhost:8080`。

检查容器状态：

```bash
docker compose ps
```

健康接口：

```bash
curl http://localhost:8080/api/health
```

预期返回：

```json
{"status":"ok","service":"officebox"}
```

## 🧩 API 示例

```text
POST /api/pdf/merge
POST /api/pdf/split?page=1
POST /api/pdf/delete-pages?pages=2,4-6
POST /api/pdf/rotate?degrees=90
POST /api/ocr/image
POST /api/ocr/pdf
POST /api/ocr/searchable-pdf
POST /api/image/to-pdf
POST /api/image/to-png
```

文件接口均使用 `multipart/form-data`，具体字段以对应控制器实现为准。

## ⚙️ 前端开发

```bash
cd frontend
npm install
npm run dev
```

如后端不是同源地址，可设置：

```bash
VITE_API_BASE_URL=http://localhost:8080
```

## 🏗️ 后端开发

```bash
cd backend
mvn spring-boot:run
```

OCR 本地运行需要系统安装 Tesseract，并准备 `chi_sim` 与 English 语言包；Docker 镜像已包含运行时依赖。

## 🔒 隐私

OfficeBox 设计为本地优先：文件处理可以在自有服务器完成，不要求上传到第三方文件处理平台。实际部署时请结合你的反向代理、访问控制和磁盘策略做好安全配置。

## 📌 v1.0 收尾说明

第一版重点完成 PDF、图片、Office、OCR、Docker 与统一 Web 工作台。视频、音频、AI 办公等分类作为后续扩展入口，不在 v1.0 中宣称已经完整实现。

## License

以仓库现有 License 文件为准。
