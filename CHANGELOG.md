# Changelog

> 基线 commit：`52d97985` — `feat(ui-core): 动态表单条件字段引用校验`（Warm-Flow v1.8.7）
>
> **全文重点标识**：🛠️ = 对原生源码的修改 | ✨ = 新增附属内容（Demo/文档/配置）

---

## 一、🛠️ 对原生源码的修改

以下是对 Warm-Flow 现有文件的直接修改，按模块排列。

### 1.1 核心引擎 (warm-flow-core)

**AbstractConditionStrategy.java** — 条件表达式支持点路径变量
- 新增 `getVariableValue()` 方法，递归解析 `formData.leaveDays` 这类点路径变量
- `preEval()` / `afterEval()` 改为通过该方法取值，兼容扁平变量和嵌套 Map 变量
- 影响范围：条件分支表达式解析路径，向前兼容原有 `${handler}` 格式

### 1.2 插件层 (warm-flow-plugin-ui)

**WarmFlowService.java** — 动态表单草稿自动创建 + 编码生成
- `saveFormContent()` 重构：若表单不存在则自动创建草稿记录，不再要求前端预建
- 新增 `createDraftForm()` / `ensureDraftId()` — 草稿自动补全
- 新增 `normalizeFormCode()` / `extractFormName()` — 按表单名称生成可读编码（如 `leave_form_1`），回退为 `FORM_{时间戳}`
- `getFormContent()` 空安全修复：`form == null` 不抛 NPE
- 新增 `FORM_CODE_MAX_LENGTH = 40` 常量

**WarmFlowController.java** (sb3 + sb4)
- `getFormContent` 接口微调，与 WarmFlowService 修改同步

**前端编译产物更新** (warm-flow-plugin-ui-sb3)
- `index.html` + JS/CSS 文件替换，反映 warm-flow-ui 前端变更

### 1.3 前端设计器 (warm-flow-ui)

**App.vue** — 入口导航页 + 路由重整
- 无 `type` 参数时展示导航选单（表单设计器 / 流程图 / 流程设计器），替代原先直接跳入流程设计器
- `type=design` 显式映射流程设计器路由

**baseInfo.vue** — 节点基础信息面板
- 节点属性中 `formCustom` 切换（N/Y）时清空 `formPath`，避免页面路径与动态表单 ID 串值
- 文案统一：`表单唯一标识` → `动态表单`，`选择自定义表单` → `选择动态表单`
- placeholder 提示改为 `请选择已发布动态表单`
- 新增 `formData.字段Key` 使用提示

**between.vue** — 分支条件配置面板
- `formCustom` 切换清空 `formPath`（同上，修复两个面板的同一问题）
- 文案统一，placeholder + tooltip 改为动态表单语义

**propertySetting.vue** — 属性设置
- 移除 `formPathList` 非空时自动推断 `formCustom=Y` 的逻辑，防止误设

**skip.vue** — 跳转条件配置
- 条件名 placeholder 改为提示 `动态表单请以 formData. 开头`
- 默认/SpEL/SNEL 表达式示例统一为 `formData.leaveDays > 3` 格式
- 新增 `formData.字段Key` 使用提示条

**design.vue** — 表单设计器
- 保存成功后通过 `window.parent.postMessage({ method: "close" })` 通知父页面关闭弹窗
- 保存后回写 `formId` 到 URL 参数和 AppStore，保证下次加载自动定位
- 加载逻辑扁平化，移除非必要的外层条件判断

**formCreate.vue** — 表单填写组件
- **申请人办理模式**：新增 `type=3`，加载任务数据但只显示业务表单 + 提交按钮，不显示审批意见
- **数据快照收集**：新增 `collectFormDataSnapshot()` / `emitFormDataSnapshot()` 方法，与原生提交共享校验口径
- **通信增强**：新增 `formReady` 消息通知；`allowSnapshot` 开关由父页面通过 `formInit.data` 控制
- `type=0` / `type=3` 的 `showApprovalFields` / `showApplicantSubmit` 控制分离

### 1.4 构建配置

**根 pom.xml**
- 新增 Lombok 1.18.38 依赖声明 + maven-compiler-plugin annotationProcessorPaths
- 注册 `warm-flow-demo-app` 子模块
- MyBatis 3.5.15 → 3.5.19

**ORM starter pom.xml (5 个文件)**
- 移除所有 `<fork>true</fork>` + `<executable>${java17.path}\javac</executable>` 硬编码配置：
  - `warm-flow-mybatis-sb3-starter`
  - `warm-flow-mybatis-sb4-starter`
  - `warm-flow-mybatis-plus-sb3-starter`
  - `warm-flow-easy-query-sb3-starter`
  - `warm-flow-easy-query-sb4-starter`

### 1.5 数据库 SQL

- `sql/mysql/v1-upgrade/warm-flow_form.sql` — 升级脚本补全 `create_by` / `update_by` 审计字段
- `sql/mysql/warm-flow-all.sql` — 完整建表 SQL 新增 `flow_form` 表定义

### 1.6 README

- 根 `README.md` 精简：移除推广和对比内容，聚焦核心说明

---

## 二、✨ 新增附属内容

以下为基于 Warm-Flow 新增的独立内容，**未修改框架核心**。

### 2.1 Demo 应用模块 (warm-flow-demo-app)

新增独立 Maven 模块，用于可视化联调，不污染框架核心代码：

| 文件 | 用途 |
|---|---|
| `WarmFlowDemoApplication.java` | 启动入口 |
| `DemoFlowWorkbenchController.java` | 流程调试入口，路由转发 |
| `DemoGodWorkbenchSubmitController.java` | 上帝视角提交链路后端 |
| `GodSubmitRequest.java` | 提交请求 DTO |
| `DemoFormCatalogController.java` | 表单目录接口 |
| `FormPathQueryServiceImpl.java` | 表单路径查询实现 |
| `WarmFlowPublishedFormReader.java` / `DemoPublishedFormReader.java` | 已发布流程读取 |
| 宿主页 HTML/CSS/JS (5 个文件) | iframe 承载 + 状态管理 |
| 单元测试 (4 个文件) | MockMvc + Node.js 状态解析 |

### 2.2 项目文档

- `CLAUDE.md` — AI 协作项目指南
- `AGENTS.md` — 多 Agent 协作说明

### 2.3 知识库 (docs/knowledge/)

- `01-overview/god-workbench-stage-summary.md` — 阶段性结论
- `02-reference/god-workbench-config-reference.md` — 配置参考
- `07-faq/god-workbench-faq.md` — 常见问题
- `README.md` — 知识库索引

### 2.4 实施计划与设计文档

- `docs/archives/` — 多份实施计划、验收清单、测试设计文档
- `samples/` — 流程设计样例（HTML Demo + Markdown 分析）

### 2.5 warm-flow-ui README 补充

- iframe 集成契约文档（路由分流、formInit/submitSuccess/close 通信协议）

---

## 三、变更统计

| 类别 | 文件数 | 变更行数 |
|---|---|---|
| 🛠️ 对原生源码的修改 | 23 个已有文件修改 | +694 / -234 |
| ✨ 新增附属内容 | 45 个新文件 | +11,244 |
| **总计** | **68 个文件** | **+11,938 / -234** |

---

## 四、提交索引

```
🛠️ 对原生源码的修改（标注 * 为仅修改原生源码的提交）：

cb3673ac  fix(designer): 动态表单 formCustom 切换时清空 formPath 并统一文案      🛠️
16ac24da  refactor(build): 移除 ORM starter 中硬编码的 JDK 17 编译器 fork 配置    🛠️
0dadcdbd  fix(form): 修正动态表单审批字段初始化                                🛠️
b3279383  fix(sql): flow_form 表升级脚本补全审计字段                            🛠️
0f65e937  feat(form): 动态表单草稿自动创建与表单设计器联动                        🛠️
7aad6127  chore(deps): 升级 MyBatis 3.5.15 → 3.5.19                           🛠️
9103b8f2  chore(pom): 添加 lombok 编译支持                                      🛠️
7d3d5e89  feat(form): 动态表单草稿支持按表单名称生成可读编码                       🛠️
5bb32fa4  feat(form): 支持申请人查看视角模式 (type=3)                           🛠️
66a38f9c  feat(form): type=3 升级为申请人办理模式，支持提交业务表单                🛠️
932e609c  fix(condition): 条件表达式支持 formData.leaveDays 点路径变量解析       🛠️
9257087b  feat(form): 动态表单支持数据快照收集                                 🛠️

✨ 新增附属内容（含 demo/文档/配置）：

6eb499e5  chore(project): 添加 CLAUDE.md 项目指南
af2fd68d  chore(demo): 新增 warm-flow 可视化联调 demo 应用
87a9c547  feat(demo): 工作台新增已发布流程列表与发起功能
fe41b60f  feat(demo): 添加 Warm-Flow 上帝视角演练台 Mock 页面
3fedfa77  feat(god-workbench): 上帝视角演练台提交链路闭环
e74666ed  docs(knowledge): 建立 God Workbench 知识库并清理 README

📄 纯文档提交：

82360253  docs(ui): 动态表单实施说明与 iframe 集成指南
4958fc41  docs(warm-flow): 添加动态表单设计分析与流程样例
4e9abe27  docs(warm-flow): 归档动态表单实施说明并更新验证测试设计
5805f3ca  docs(warm-flow): 添加可视化 demo 实施计划与验收清单
8d1cea60  docs(demo): 补充动态表单升级说明和本地连接配置
d763452d  docs(agents): 新增 AGENTS.md 项目指导文档
e188cf55  docs(god-workbench): 上帝视角演练台第二阶段开发计划
```

基线：`52d97985 feat(ui-core): 动态表单条件字段引用校验`
