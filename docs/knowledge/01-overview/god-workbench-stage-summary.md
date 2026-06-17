# God Workbench 第一阶段总结

## 1. 目标与结果

本轮目标是把 `demo-god-workbench` 从静态 mock 推到“真实可走通一轮流程”的第一版闭环。

最终完成的闭环是：

1. 父页面加载已发布流程定义。
2. 父页面发起真实流程实例。
3. `formCreate` iframe 加载当前任务表单。
4. 父页面通过 `postMessage` 采集 iframe 当前表单快照。
5. 父页面调用 demo 专用上帝模式接口，以 `ignore(true)` 推进流程。
6. 父页面刷新运行态流程图、当前任务表单和历史步骤列表。
7. 支持点击历史步骤，回看已填写表单。

对应事实源：

- 页面入口：`DemoFlowWorkbenchController.java`
- 宿主页脚本：`demo-god-workbench.js`
- 宿主页结构：`demo-god-workbench.html`
- 表单快照契约：`formCreate.vue`
- 后端上帝接口：`DemoGodWorkbenchSubmitController.java`

## 2. 过程中遇到的关键问题

### 2.1 首个任务误显示审批意见

现象：

- 发起流程后，申请人继续填写的第一步表单里，出现了“审批意见 / 审批通过 / 退回”。

根因：

- `formCreate.vue` 的运行态有明确类型语义：
  - `type = "0"`：审批人待办
  - `type = "1"`：历史表单
  - `type = "3"`：申请人继续填写
- 第一版宿主页最初把首个任务也当成了 `type = "0"`，导致共享组件按审批态渲染。

修复：

- 在 `demo-god-workbench.js` 发起实例后，把首个任务显式标记成 `formType: "3"`。
- 后续提交完成后，下一步待办再切回 `formType: "0"`。

结论：

- God Workbench 不能只拿 `taskId`，还必须正确维护“当前表单视图类型”。

### 2.2 “采集表单快照超时”并不是单一问题

现象：

- 点击“提交演练”后日志出现：`提交演练失败：采集表单快照超时`

本轮确认过有三类根因。

#### 根因 A：运行时加载的是旧前端 bundle

现象：

- 源码里已经加了 `collectFormData` 契约，但运行中的 `/warm-flow-ui/index.html` 对应的 JS 包不包含这段逻辑。

根因：

- 本地 `8080` 运行时真正加载的不是 `warm-flow-ui/dist`，而是插件模块里的静态资源目录。
- 在 IDE/本地启动场景下，实际入口常常来自：
  - `warm-flow-plugin-vue3-ui/src/main/resources/warm-flow-ui`
  - `warm-flow-plugin-vue3-ui/target/classes/META-INF/resources/warm-flow-ui`

修复：

- 重新执行 `yarn build:prod`
- 把 `dist` 同步覆盖到上述两个运行时目录
- 再用 `curl` 确认线上 bundle 名和内容已更新

结论：

- 改 `formCreate.vue` 之后，只改源码不够；必须确认运行时实际 bundle 已替换。

#### 根因 B：当前停在历史表单视图，却直接点击提交

现象：

- 先点历史步骤打开历史表单，再点“提交演练”，会超时。

根因：

- 历史表单是 `type = "1"`。
- 父页面给历史表单发送 `allowSnapshot = false`。
- 历史视图不会响应 `collectFormData`，因此父页面一直等不到 `formDataSnapshot`。

修复：

- `submitRun()` 执行前，先调用 `ensureCurrentTaskFormReady()`：
  - 强制把视图切回当前待办
  - 重新刷新当前任务表单 iframe
  - 等待当前任务表单 ready 后再采集

结论：

- “当前正在看的表单”和“当前可提交的任务”是两个不同概念，不能混用。

#### 根因 C：iframe 还没初始化完成就开始采快照

现象：

- 刚发起、刚刷新或刚切换视图后，立刻点提交，有概率超时。

根因：

- 父页面之前只知道 iframe 已经加载 URL，不知道里面的 `formCreate` 是否完成 `formInit`、数据回填和校验器挂载。

修复：

- `formCreate.vue` 在 `nextTick` 后新增 `formReady` 消息。
- 父页面维护 `formFrameReadyState`，只有收到匹配的 `taskId + type` 之后，才允许进入快照采集。

结论：

- iframe 集成里，“页面已加载”和“业务表单已准备好”必须拆成两个状态。

### 2.3 提交没报错，但流程没有进入下一步

现象：

- 点击提交后看起来没有报错，但流程图和表单没有明显变化，容易误判为“没成功”。

根因：

- 之前失败时，实际上卡在了提交前的快照采集阶段，后端 `submit` 根本没有被调用。
- 另外，若业务表单值不完整，后端分支条件求值也可能失败。

本轮确认过的真实例子：

- 请假流程依赖 `formData.leaveDays`
- 如果提交空 `{}`，后端条件如 `gt@@formData.leaveDays|3` 会失败

修复：

- 采快照前复用 `formCreate` 原生校验逻辑
- 拿到快照后再调用 `/demo/god-workbench/submit`
- 成功后统一刷新：
  - 当前实例/任务状态
  - FlowChart iframe
  - formCreate iframe
  - 历史步骤列表

结论：

- God Workbench 不是“只要能点提交就行”，而是“先保证表单值完整，再推进流程”。

### 2.4 “预览当前表单”价值不高，最终删除

现象：

- 用户体验上看不出“预览当前表单”和普通刷新有什么本质差异。

结论：

- 该按钮没有形成清晰价值，不应该继续保留在第一版。

处理：

- 删除按钮
- 删除对应事件绑定和逻辑

经验：

- demo 页面宁可少一点，也不要保留语义不清、行为重复的入口。

### 2.5 历史已填写表单不是引擎没有，而是宿主页没接出来

现象：

- 一开始以为“当前之前的所有已填写表单”还没有实现能力。

真实情况：

- 引擎与 UI 已经有历史表单能力：
  - `type = "1"`
  - `hisLoad(taskId)`
  - `/warm-flow/execute/hisLoad/{hisTaskId}`

缺口：

- 宿主页没有“按实例拉历史任务列表，再点开某条历史表单”的父页面能力。

修复：

- 后端新增 `/demo/god-workbench/history/{instanceId}`
- 父页面新增历史步骤面板，点击某条记录时以 `type = "1"` 重新初始化 iframe

结论：

- 这类问题要先分清“底层能力不存在”还是“宿主页没把已有能力组装出来”。

### 2.6 `FlowParams.variable(...)` 和 `FlowParams.formData(...)` 不是一回事

现象：

- 方案评审时曾有人建议把表单值直接塞进 `.variable(request.getFormData())`

根因：

- `.variable(...)` 会把 map 展开为多个顶层变量。
- `.formData(...)` 会把业务表单放在 `variable.formData` 下。
- 现有 UI / 条件表达式 / 办理链路依赖的是 `formData.xxx` 语义。

最终处理：

- 后端上帝接口明确使用 `.formData(request.getFormData())`

结论：

- 这不是风格问题，而是运行时数据结构契约问题。

## 3. 当前实现边界

本轮明确保留的边界如下：

### 已完成

- 真实页面入口 `/demo/god-workbench`
- 已发布流程定义加载
- 发起实例
- 当前任务表单渲染
- 表单快照采集
- `ignore(true)` 流转
- FlowChart / 表单 / 历史步骤同步刷新
- 历史表单回看

### 明确不做

- 不把 God Mode 逻辑扩散进核心引擎
- 不把共享 `formCreate` 改成默认可导出快照
- 不实现 iframe 内原始 controls 的视觉预览开关
- 不把 demo 页面直接做成通用产品能力

## 4. 本轮最重要的经验

1. 真实问题往往不在“源码是否写了”，而在“运行时是否真的加载到了这份源码”。
2. iframe 集成必须把 `URL 已加载`、`业务数据已初始化`、`当前视图类型正确` 三个状态分开管理。
3. 动态表单链路里，`formData` 的结构契约比普通字段更重要，不能随手改成别的传参方式。
4. demo 宿主页的责任是“把已有能力按真实使用顺序编排出来”，而不是在第一版里追求大而全。
