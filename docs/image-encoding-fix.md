# 图片编码修复记录

批量图片处理必须保证输出格式与文件扩展名一致。

- JPG：使用 ImageIO ImageWriter 的 compression quality。
- PNG：使用 PNG 编码器，不伪装为 WebP。
- WebP：在项目引入可靠 WebP ImageIO 编码器后再开放输出选项。
- 批量接口继续限制最多 50 个文件，并统一 ZIP 下载。
