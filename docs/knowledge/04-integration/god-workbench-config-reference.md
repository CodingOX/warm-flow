# God Workbench 前后端配置参考手册

## 1. 页面与入口

### 后端页面入口

- `/demo/workbench`
  - 普通宿主页
  - 代码：`DemoFlowWorkbenchController.java`
- `/demo/god-workbench`
  - 上帝模式工作台
  - 代码：`DemoFlowWorkbenchController.java`

### 静态页面文件

- `demo-god-workbench.html`
  - 页面结构
- `demo-god-workbench.css`
  - 页面样式
- `demo-god-workbench.js`
  - 页面状态、请求、消息契约、刷新逻辑
- `demo-god-workbench-state.js`
  - 视图状态辅助逻辑

## 2. 前端 iframe 类型语义

`/warm-flow-ui/index.html` 通过 `type` 参数区分页面模式。

本轮实际用到的类型如下：

| type | 含义 | 使用位置 |
|------|------|----------|
| 默认/无 type | 流程设计器 | 设计器场景 |
| `FlowChart` | 运行态流程图 | 宿主页中间区域 |
| `formCreate` | 表单办理/回看 | 宿主页右侧 iframe |

`formCreate` 内部再依赖父页面传入的数据类型：

| data.type | 含义 | `formCreate.vue` 行为 |
|-----------|------|----------------------|
| `"0"` | 审批人待办 | 显示审批意见与审批动作 |
| `"1"` | 历史表单 | 只读，不显示审批动作 |
| `"2"` | 已发布表单预览 | 只看定义 |
| `"3"` | 申请人继续填写 | 显示“提交表单”，不显示审批意见 |

## 3. 前端宿主页状态模型

宿主页至少维护下面四类状态，缺一类都会出现误判或超时。

### 3.1 当前运行态

字段来源：

- `instanceId`
- `taskId`
- `currentNodeCode`
- `flowStatus`
- `ended`
- `formType`

维护位置：

- `demo-god-workbench.js`

说明：

- `formType` 不是后端直接给的业务字段，而是宿主页自己维护的“当前任务应该按哪种表单身份打开”。

### 3.2 当前表单视图

字段：

- `currentFormView.type`
- `currentFormView.taskId`
- `currentFormView.title`

说明：

- 当前正在看的，可能是“当前待办”，也可能是“历史表单”。
- 提交时不能盲信这个视图，必须先切回当前待办。

### 3.3 iframe ready 状态

字段：

- `formFrameReadyState.taskId`
- `formFrameReadyState.type`
- `formFrameReadyState.ready`

说明：

- 只有收到 `formReady`，才表示右侧表单已经完成初始化，可安全采集快照。

### 3.4 历史步骤列表

字段：

- `historyItems`

来源：

- `/demo/god-workbench/history/{instanceId}`

用途：

- 点击某条历史步骤时，用 `type = "1"` 回看历史表单。

## 4. 后端接口参考

### 4.1 已发布流程定义

- `GET /demo/definitions/published`

用途：

- 宿主页加载左侧流程定义列表。

### 4.2 发起实例

- `POST /demo/definitions/start/{definitionId}`

用途：

- 宿主页点击“发起演练”时创建真实实例，并返回首个任务。

### 4.3 上帝模式提交

- `POST /demo/god-workbench/submit`

请求体：

```json
{
  "taskId": "2066899492326322179",
  "message": "同意，流程配置验证通过。",
  "formData": {
    "leaveDays": 5,
    "reason": "测试"
  }
}
```

关键实现点：

- `FlowParams.build().formData(request.getFormData()).ignore(true)`
- 代码：`DemoGodWorkbenchSubmitController.java`

返回体核心字段：

- `instanceId`
- `taskId`
- `currentNodeCode`
- `flowStatus`
- `ended`

### 4.4 历史步骤

- `GET /demo/god-workbench/history/{instanceId}`

返回字段：

- `hisTaskId`
- `nodeCode`
- `nodeName`
- `skipType`
- `message`
- `createTime`

用途：

- 宿主页显示历史步骤列表，并支持点开历史表单。

## 5. `formCreate` 的父子页面消息契约

### 5.1 iframe -> 父页面

| method | 含义 | 说明 |
|--------|------|------|
| `formInit` | 请求初始化 | iframe 启动后先问父页面要 `taskId/type` |
| `getOffsetHeight` | 通知高度 | 父页面据此调 iframe 高度 |
| `formReady` | 表单初始化完成 | 本轮新增，用于解决提交前时序问题 |
| `formDataSnapshot` | 返回表单快照 | 本轮新增，仅在 `allowSnapshot=true` 下触发 |
| `formDataSnapshotError` | 快照失败 | 本轮新增，通常是未初始化或校验不通过 |
| `submitSuccess` | 原生提交成功 | 共享办理页原有契约 |

### 5.2 父页面 -> iframe

| method | 含义 | 关键字段 |
|--------|------|----------|
| `formInit` | 初始化当前表单 | `taskId`, `type`, `disabled`, `allowSnapshot` |
| `collectFormData` | 请求导出快照 | 无 body |
| `reset` | 重置表单 | 共享能力，本轮未作为主流程使用 |

### 5.3 `allowSnapshot` 的作用

这是本轮最重要的新前端配置之一。

规则：

- 默认 `false`
- 只有 God Workbench 父页面显式传入 `true` 时，`formCreate` 才会响应 `collectFormData`

目的：

- 不把“导出当前表单值”变成普通业务页默认开放能力
- 避免共享组件被误用成通用数据提取器

## 6. 运行时静态资源目录

这是本轮最容易踩坑的地方。

### 源码目录

- `warm-flow-ui/src/components/form/formCreate.vue`

### 前端构建产物

- `warm-flow-ui/dist`

### 插件静态资源目录

- `warm-flow-plugin-vue3-ui/src/main/resources/warm-flow-ui`

### 本地运行时常见真实资源目录

- `warm-flow-plugin-vue3-ui/target/classes/META-INF/resources/warm-flow-ui`

结论：

- 如果本地 `8080` 是 IDE 直接启动，真正被浏览器访问到的，往往是 `target/classes/META-INF/resources/warm-flow-ui`
- 只改 `dist` 或只改源码目录，不足以保证浏览器拿到最新包

## 7. 本轮用到的构建与验证命令

### 后端测试

```bash
mvn -pl warm-flow-demo-app -Dtest=DemoFlowWorkbenchControllerTest,DemoGodWorkbenchSubmitControllerTest test
```

### 前端构建

```bash
cd warm-flow-ui
yarn build:prod
```

### God 页脚本语法检查

```bash
node --check warm-flow-demo-app/src/main/resources/static/demo-god-workbench.js
```

### 查看 `8080` 当前真正引用的 bundle

```bash
curl -s http://127.0.0.1:8080/warm-flow-ui/index.html | rg -o 'index-[A-Za-z0-9_-]+\\.js|index-[A-Za-z0-9_-]+\\.css'
```

### 查看运行中的 God 页脚本是否已包含目标逻辑

```bash
curl -s http://127.0.0.1:8080/demo-god-workbench.js | rg 'collectFormData|formDataSnapshot|history'
```

### 查看 `8080` 监听进程

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

### 覆盖运行时静态资源

```bash
rm -rf warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-vue3-ui/src/main/resources/warm-flow-ui/*
cp -R warm-flow-ui/dist/. warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-vue3-ui/src/main/resources/warm-flow-ui/

rm -rf warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-vue3-ui/target/classes/META-INF/resources/warm-flow-ui/*
cp -R warm-flow-ui/dist/. warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-vue3-ui/target/classes/META-INF/resources/warm-flow-ui/
```

## 8. 下次继续优化时优先看哪里

### 如果想优化交互

先看：

- `demo-god-workbench.html`
- `demo-god-workbench.css`
- `demo-god-workbench.js`

重点：

- 历史步骤面板
- 日志呈现
- 当前任务与历史表单切换提示

### 如果想优化流程推进链路

先看：

- `DemoGodWorkbenchSubmitController.java`
- `GodSubmitRequest.java`
- `formCreate.vue`

重点：

- `formData` 结构
- `ignore(true)` 是否继续保留为 demo 专用
- 快照校验口径是否仍与原生提交一致

### 如果想继续扩展历史能力

先看：

- `/demo/god-workbench/history/{instanceId}`
- `historyItems` 渲染逻辑
- `type = "1"` 的回看能力

可扩展方向：

- 历史步骤分页
- 高亮当前节点之前/之后的阶段
- 区分申请节点、审批节点、网关后节点
