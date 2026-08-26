# 点检盘点助手

Android 盘点工具，支持本地台账导入、连续拍照、照片水印、单资产 PDF 和 ZIP 导出。

## 线上评估系统同步

“线上同步”会打开 `ty.zhrdc.net` 登录页。登录后选择评估项目，App 自动扫描项目下全部公司和资产基础法科目，仅将远端“是否盘点=是”的条目同步到对应本地项目。同步采用远端项目 ID 和稳定行键增量合并，不会因重复同步删除已有照片或 PDF。

PDF 生成后进入持久化上传队列。上传前会显示项目、公司、科目和资产信息；缺少盘点索引、行键冲突或会话失效的任务会被阻止或保留待重试，不修改评估系统底稿字段。

## 本地构建

1. 使用 Android Studio 或 JDK 17+ 打开项目。
2. 确认 `local.properties` 指向 Android SDK。
3. 运行 `gradlew.bat assembleDebug` 或 `gradlew.bat testDebugUnitTest`。

`.env`、签名文件、Cookie/Token、照片/PDF 和构建缓存不应提交到 Git。