# 接入指南

## 依赖方向

```text
host reader / import adapter
          ↓ plain text + occurrence ID + direction + opaque origin/revision
      Conversation
          ↓
ChatModelClient / result validators
          ↓
host UI or optional ui-android cards
          ↓ user explicitly chooses a reply
host validates source and empty draft, then offers fill (sending stays with user)
```

`core`不接收Android节点或像素边界；`ui-android`不向下调用读取器。宿主自己处理应用授权、锁屏、前台状态、版本变化、星标位置和草稿写入。若接入应用没有可用节点，这个库不会让它自动变得可读取。

## 输入与来源

`ConversationOrigin(sourceId, revision)`：`sourceId`是宿主生成的不透明会话标识，切换账号/窗口/会话必须变更；消息内容或顺序改变时递增revision。不要以包名或显示名作为唯一会话身份。库只比较宿主提供的数据，不能独立证明来源。

`Conversation.Message(id,text,side,speakerName)`：id为本帧内唯一正整数，表示一次消息出现，不能按文字去重。显示名可省略，同名不等于同一身份；疑似凭据或超80字符的显示名被省略。

`ConversationSelection.from(frame)`只在本地过滤空白与疑似凭据。调用 `select(ids)` 才产生可上传总结的明确选择；超限会报错，不静默截断。过滤不改变其他消息ID。

## 实际模型请求

`ChatModelClient`可由宿主注入，默认实现为 `new ChatModelClient.Https()`，使用兼容OpenAI Chat Completions的HTTPS接口。服务需要支持 `response_format: json_object`；默认实现不进行接口猜测或服务商兜底。

```java
// 在宿主后台执行器运行；不要在Android主线程调用。
RequestGate gate = new RequestGate(
    frame.origin,
    () -> host.currentOriginOrNull(),
    () -> host.hasReadAndModelConsentForThisSource()
);
ChatModelClient client = new ChatModelClient.Https();
ChatAnalysis result = client.analyze(
    endpoint, modelId, keyFromHostSecureStorage,
    selectedText, precedingVisibleText, gate
);
// 回到UI线程前再次检查来源；宿主仍需防止晚到结果覆盖新卡片。
gate.check(null);
host.render(result);
```

`host`是接入方自己的接口示意，不是本库提供的全局对象。密钥从宿主安全存储读取，不写进源码、日志或示例。不要用本段伪代码取代实际来源和授权实现。

总结使用 `client.summarize(endpoint,modelId,key,selected,gate)`。gate期望的origin必须与selected匹配。总结的每次上传同意应在本地多选预览中取得，并与账号/来源的模型授权共同检查。

在卡片关闭、切换会话、撤销授权或锁屏时调用 `gate.cancel()`；并移除宿主悬浮窗。取消会尽力断开连接，已经送达服务商的数据无法撤回。授权回调本身不会自动侦听系统事件，需要宿主在状态变化时主动取消。

请求在连接/写入前及收到结果后复核授权与来源。超时为连接10秒、读取20秒；响应最多64KiB，不跟随重定向。`Failure.code`含固定错误，如 `UPLOAD_CONSENT_REQUIRED`、`STALE_SOURCE`、`REQUEST_CANCELLED`、`HTTP_429`、`NETWORK_TIMEOUT`、`OUTPUT_TRUNCATED`、`RESULT_JSON`、`RESULT_SCHEMA`。其他输入错误为 `IllegalArgumentException`。不要把原始服务端响应/异常直接展示或记录。

## 不使用默认传输

实现 `ChatModelClient`即可注入宿主已有模型网关；返回 `ChatAnalysis` 或 `ConversationSummary`。解析入口是 `ChatAnalysis.parse(json,originalText)` 与 `ConversationSummary.parse(json,selected)`。自定义实现必须自行遵守同样的授权、来源、长度、过滤与取消规则，不能因为实现了接口就假定具备默认传输的约束。离线示例使用此方式，不联网。

纯解析器只验证结果结构、概念来源和引用，不承担用户授权或网络控制。总结来源编号有效不能证明模型归纳正确；模型也可能忽略提示中的否定、条件或时间限制，结果应允许用户核对原文。

## Android展示组件

包名 `com.lxz.empathystar.widgets`。启用 `-PwithAndroid=true` 后构建，Android最低API29。两个组件都是普通View，不要求无障碍服务或悬浮窗权限。

### 知识点独立解释页

```java
AnalysisCardStack stack = new AnalysisCardStack(context, hostAnalysisView, closeCallback);
// 点击经过校验的知识点时调用：
stack.showKnowledge(term.text, term.explanation, originalText);
// 解释页的返回按钮也会调用这一逻辑，原分析View及其滚动状态保留。
stack.returnToAnalysis();
```

完整分析卡片的回复列表由宿主渲染。这个模块没有复制主App的整个悬浮窗服务；不能把这两个View当作已适配所有聊天软件的UI。

### 总结多选、结果和来源

```java
ConversationSummaryCard card = new ConversationSummaryCard(
    context, ConversationSelection.from(frame), endpoint, modelId,
    modelAllowed, closeCallback,
    chosen -> host.requestSummaryInBackground(chosen)
);
// 回调完成后，在主线程且再次核对来源：
card.showResult(validatedSummary);
// 失败只传固定、可公开展示的提示：
card.showFailure("请求失败，请稍后重试。");
```

卡片含消息多选和本次上传勾选。点击引用编号打开覆盖原卡片的来源页，可返回；知识点页同样本地切换，不增加模型调用。`showResult`应只用于该卡片提交所选消息后的有效响应，不能对尚未提交的卡片强行注入结果。宿主负责将请求/取消绑定到卡片生命周期。

模型提示词与输出结构位于 `ChatAnalysis.SYSTEM_PROMPT` 和 `ConversationSummary.SYSTEM_PROMPT`。当前的温度、token预算和可选官方DeepSeek参数是默认客户端实现细节；若服务协议不同，应注入自己的客户端。
