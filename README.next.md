# OfficeBox

> 开源、一站式、可私有化部署的办公工具箱。

## 当前进度

- Vue 3 + TypeScript + Vite 工作台
- 响应式首页、工具搜索、拖拽上传入口、深色模式
- Java 21 + Spring Boot 3 API
- PDF 合并 API：`POST /api/pdf/merge`
- 文件上传限制：单文件 100MB，请求 500MB

## 本地运行

```bash
cd frontend && npm install && npm run dev
cd backend && mvn spring-boot:run
```

前端：`http://localhost:5173`，后端：`http://localhost:8080`

## Roadmap

- [x] 项目基础架构
- [x] 产品首页
- [x] PDF 合并 API
- [ ] PDF 拆分 / 压缩 / 水印 / 加密
- [ ] 图片工具
- [ ] Office 转换
- [ ] OCR
- [ ] 任务中心
- [ ] Docker 一键部署
- [ ] AI 办公
