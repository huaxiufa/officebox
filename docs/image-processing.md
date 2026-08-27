# 图片处理能力

已加入：裁剪、水平翻转、垂直翻转。

API：
- `POST /api/image/transform/crop`：`file, x, y, width, height`
- `POST /api/image/transform/flip`：`file, direction=horizontal|vertical`

批量图片处理继续使用 `/api/image/batch/process`，最多 50 张并输出 ZIP。

下一阶段：EXIF 清理、压缩前后大小对比、批量裁剪/翻转。
