# 共情星 · 通用会话业务模块

从 [共情星安卓版](https://github.com/lxz666441/empathy-star-android) 提取的独立业务库。输入普通消息数据，提供整句理解、三条建议回复、原文知识点和所选消息总结。无需微信、QQ、无障碍节点或手机即可构建核心库和运行离线示例。

**这是可继续迭代的源码模块，尚未发布到 Maven，也不是已经完成多应用验收的SDK。完整版App目前仍使用原有内部实现，尚未反向接入本库。**

## 分层

| 模块 | 包含内容 | 不承担的职责 |
| --- | --- | --- |
| `core` | Java业务数据、提示词、解析校验、上下文、模型客户端、授权/来源复核 | 应用识别、节点、坐标、星标定位、锁屏与窗口管理 |
| `ui-android` | 可选的知识点独立解释页/返回栈、总结多选/结果/来源卡片 | 读屏、启动应用、悬浮窗、自动发送 |
| `examples` | 合成消息和注入返回值的完整离线演示 | 实际模型调用、真实聊天验收 |

宿主适配器只需把已获准读取的消息转换为 `Conversation`。微信、QQ、飞书的读取差异应在宿主适配层解决，不能进入核心库。读取失败时，不应构造成功消息或触发模型。

## 运行

只验证业务：JDK17，不需要Android SDK。首次构建需下载Gradle和依赖，示例本身不会访问模型服务。

```sh
./gradlew :core:test :core:jar :examples:run
```

Android卡片另行启用，需要SDK36；Windows请克隆到纯英文路径。

```sh
./gradlew -PwithAndroid=true :ui-android:assembleDebug :ui-android:lintDebug
```

Windows使用 `./gradlew.bat`。Wrapper固定Gradle8.13并校验发行包SHA-256。核心JAR位于 `core/build/libs/`，可选AAR位于 `ui-android/build/outputs/aar/`。

## 最小接入

```java
ConversationOrigin origin = new ConversationOrigin("opaque-visible-session", 1);
Conversation frame = new Conversation(origin, Arrays.asList(
    new Conversation.Message(7, "我们用 RAG 给大模型补充外部知识。", Conversation.Side.INCOMING),
    new Conversation.Message(9, "我先整理资料。", Conversation.Side.OUTGOING)
), false);

// 来源、revision和授权均由宿主管理；不能用恒为true代替真实用户授权。
ConversationSelection selected = ConversationSelection.from(frame)
    .select(new HashSet<>(Arrays.asList(7, 9)));
```

包名为 `com.lxz.empathystar.core`。完整可运行演示见 [OfflineDemo.java](examples/src/main/java/example/OfflineDemo.java)。实际网络接入、卡片用法及取消请求见 [接入指南](docs/INTEGRATION.md)。Android自带 `org.json`；桌面宿主需要添加 `org.json:json:20240303` 运行时依赖，示例模块已配置。

## 核心规则

- 单句：所选消息最多6000字符、此前最多8条可见上下文，组装提示不超过16000字符。调用方负责保证上下文确实来自所选消息之前，工具方法 `precedingContext` 可按出现位置截取。
- 结果：整句解释、谨慎的沟通线索、三条不同回复；知识点最多5个，必须在原文匹配并通过完整词边界校验。位置使用Java UTF-16索引 `[start,end)`。
- 总结：先本地多选，明确确认后才调用。最多30条、12000字符，保留原始消息ID与顺序，重复文字不合并。来源引用只能指向本次所选ID。
- 明确上传同意、来源版本一致且未取消时才发起请求。HTTPS，不重试、不自动更换服务商、不记录内容、不持久化密钥、不执行回复发送。
- 文本凭据过滤只是一层启发式检查，不能保证识别所有敏感内容；模型结果、来源编号有效也不代表语义一定正确。

## 文档与状态

- [API与接入指南](docs/INTEGRATION.md)
- [提取来源与实现差异](docs/PROVENANCE.md)
- [安全与验证边界](docs/SAFETY.md)
- [本次构建和测试记录](docs/VERIFICATION.md)

不内置API Key，不承诺任何模型免费，不在测试中调用真实模型。项目尚未指定开源许可证；第三方依赖许可由各项目维护。
