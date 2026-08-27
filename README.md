# OfficeBox

一站式开源综合办公工具箱。

## 当前进度

### Web
- Vue 3 + TypeScript + Vite
- 响应式工作台
- 工具搜索
- PDF 合并工作台
- PDF 拆分 / 删除页面 / 旋转工作台
- 深色模式

### API
- Java 21 + Spring Boot 3
- PDF 合并：`POST /api/pdf/merge`
- PDF 拆分：`POST /api/pdf/split?page=1`
- 删除页面：`POST /api/pdf/delete-pages?pages=2,4-6`
- 旋转：`POST /api/pdf/rotate?degrees=90`

## Roadmap

- [x] 项目基础架构
- [x] 产品首页
- [x] PDF 合并
- [x] PDF 拆分 / 删除页面 / 旋转
- [ ] PDF 压缩 / 水印 / 加密
- [ ] 图片工具
- [ ] Office 转换
- [ ] OCR
- [ ] 任务中心
- [ ] Docker 一键部署
- [ ] AI 办公
