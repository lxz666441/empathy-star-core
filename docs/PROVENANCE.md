# 提取来源与实现差异

来源为 [empathy-star-android的1.55源码快照](https://github.com/lxz666441/empathy-star-android/tree/cb4cf5345e344729ab6307ede72e7cac89385bc6)，于2026-11-03整理。未复制Jev源码、OCR、桌面高亮、日程、第三方Bot、设备日志或真实消息数据。

保留的业务实现：`ChatAnalysis`的提示词与结果解析，`KnowledgeTerms`的原文匹配和边界校验，`ChatContentPolicy`的启发式过滤，`KnowledgeDisclosure`的展开状态，`ConversationSummary`的分类与引用校验，以及HTTPS客户端的请求格式、上限和失败处理。

为独立复用作出的改动：

- 使用独立包名与公开Java API；来源改为通用 `ConversationOrigin`，输入改为 `Conversation`，不依赖 `ChatSnapshot` 或 `ScreenReadResult`。
- `ConversationSelection`使用宿主传入的出现ID，显式选择与本地预览分开，总结请求拒绝未经选择的整帧输入。
- `BusinessPolicy`只保留业务上下文和HTTPS检查，不带应用、节点、坐标和窗口条件。
- `RequestGate`明确复核上传授权、来源和取消，收到结果后也复核，连接结束后释放引用。
- `ChatModelClient`可注入；默认HTTPS实现有仅供包内测试的内存连接替身，测试不调用服务商。
- 两个普通Android卡片迁入可选 `ui-android`。纯Java构建默认不加载Android插件。

这是可独立构建的提取版本。完整版仍有自己的内部副本，未变更手机上的安装和行为，也尚未改为依赖此仓库。以后修改通用实现后，需要明确集成和回归；两个仓库不会自动同步。

当前没有发布兼容性承诺或Maven坐标。JAR/AAR只是本地构建产物，不是公开的软件包发行。
