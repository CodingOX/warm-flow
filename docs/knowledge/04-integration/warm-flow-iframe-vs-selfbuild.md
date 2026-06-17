# Warm-Flow 集成架构决策：iframe 方案 vs 自建方案

## 1. 背景

Warm-Flow 内置了一套完整的前端 UI（流程设计器、表单渲染、流程图预览、审批操作栏），集成方式默认为 iframe 嵌入。但在实际业务集成中，iframe 存在性能、通信复杂度和交互灵活性等顾虑。本次分析基于源码深度阅读，给出"完全自建前端"的技术可行性评估。

## 2. 核心结论

**完全可行。iframe 是可选的 UI 实现方案，不是必选方案。**

Warm-Flow 的前端 UI 和后端引擎之间**没有硬耦合**。iframe 内的所有功能，都可以直接调对应后端 API 实现。

```
┌─────────────────────────────────────────────┐
│  自建前端 (Vue/React/SVG/form-create)       │  ← 你完全控制
├─────────────────────────────────────────────┤
│  REST Controller (封装引擎 API)              │  ← 自己写，很薄的一层
├─────────────────────────────────────────────┤
│  FlowEngine (def/ins/task/hisTask/chart)    │  ← Warm-Flow 核心
│  PermissionHandler / GlobalListener          │  ← 你实现接口
└─────────────────────────────────────────────┘
```

## 3. 核心功能模块拆解

### 3.1 流程图预览

| 维度 | 说明 |
|------|------|
| **后端能力** | `FlowEngine.chartService()` 提供：节点坐标、连线路径、每个节点/连线的 status（0=未办/1=待办/2=已办） |
| **坐标来源** | 坐标由**设计器拖拽时决定**，存入 `flow_node.coordinate` / `flow_skip.coordinate`，后端原样返回，不做自动布局 |
| **后端真正做的自动化** | 只处理**状态着色**——根据审批经过的路径，标记每个节点和连线的 status，前端按颜色渲染即可 |
| **自建方案** | 前端用 SVG / Canvas / D3.js 直接渲染 `nodeList` 和 `skipList`，根据 coordinate 定位，根据 status 着色 |
| **难度** | ⭐ 低 |

坐标数据格式：

```java
// NodeJson.coordinate — 节点坐标
"x,y|textX,textY"          // 例如: "400,200|400,180"

// SkipJson.coordinate — 连线路径
"x1,y1;x2,y2;...|textX,textY"  // 例如: "400,240;400,280|400,260"
```

**后端不做自动布局**，坐标完全由前端设计器拖拽产生。如果你需要一个"自动排列"的流程图查看器，前端需要自行实现拓扑布局算法，或者使用 dagre.js 等布局库。

### 3.2 动态表单

| 维度 | 说明 |
|------|------|
| **底层库** | form-create（`@form-create/element-ui`） |
| **存储格式** | form-create 可渲染的 JSON 规则（字段列表、布局、校验规则） |
| **自建方案** | 在项目中直接引入 `@form-create/element-ui`，从后端获取 JSON 规则后直接渲染，完全不需要 iframe |
| **难度** | ⭐⭐ 中等（引入一个 npm 包即可） |

```js
// 自建方案核心代码 — 不需要 iframe
import formCreate from '@form-create/element-ui'

// 从后端拿到表单配置 JSON
const rule = await api.getFormConfig(definitionId, nodeCode)
// 直接渲染
formCreate.render(rule, { onSubmit: (formData) => { /* 保存 */ } })
```

### 3.3 审批操作栏

| 维度 | 说明 |
|------|------|
| **iframe 里的行为** | 通过/驳回/转办/加签/减签/委派 + 意见填写 |
| **本质** | 全部是 `FlowEngine.taskService()` 的 API 调用 |
| **自建方案** | 前端写几个按钮 + 一个意见输入框，调对应 API |
| **难度** | ⭐ 低 |

核心 API 映射：

```java
// 通过
FlowEngine.taskService().skip(taskId, FlowParams.build()
    .skipType("PASS").message("同意"));

// 驳回（指定目标节点）
FlowEngine.taskService().skip(taskId, FlowParams.build()
    .skipType("REJECT").nodeCode("node_start").message("退回"));

// 退回上一级
FlowEngine.taskService().rejectLast(taskId, FlowParams.build()
    .message("退回修改"));

// 转办
FlowEngine.taskService().transfer(taskId, FlowParams.build()
    .addHandlers(List.of("newUserId")).message("请帮忙处理"));

// 加签
FlowEngine.taskService().addSignature(taskId, FlowParams.build()
    .addHandlers(List.of("newUserId")).message("请协助审批"));
```

### 3.4 审批轨迹/时间轴

| 维度 | 说明 |
|------|------|
| **后端能力** | `FlowEngine.hisTaskService().getByInsId(instanceId)` 返回完整审批历史 |
| **返回字段** | hisTaskId、nodeCode、nodeName、skipType（PASS/REJECT）、message、createTime、cooperateType |
| **自建方案** | 前端调 API 拿数据，渲染时间轴组件 |
| **难度** | ⭐⭐ 中等 |

### 3.5 流程设计器（编辑/拖拽）

| 维度 | 说明 |
|------|------|
| **底层库** | LogicFlow 2.x（`@logicflow/core` + `@logicflow/extension`） |
| **Warm-Flow 自研代码** | 37 个文件，约 8,023 行 |
| **目录** | `warm-flow-ui/src/components/design/` |
| **自建方案 A** | iframe 嵌入（仅设计器页面用） |
| **自建方案 B** | 拷贝源码到自己的 Vue 项目，适配依赖和 API 路径 |
| **难度** | ⭐⭐⭐ 中等偏高（方案 B） |

设计器源码结构：

```
design/
├── classics/js/                    # 经典模式: 7 种节点的 SVG 自定义渲染
│   ├── start.js (79行)             # 开始节点 (圆形)
│   ├── between.js (143行)          # 中间节点 (圆角矩形)
│   ├── end.js (79行)               # 结束节点 (双环)
│   ├── serial.js (88行)            # 互斥网关 (菱形+X)
│   ├── parallel.js (96行)          # 并行网关 (菱形+十字)
│   ├── inclusive.js (93行)         # 包容网关 (菱形+同心圆)
│   ├── skip.js (46行)              # 连线
│   └── sidebarIcons.js (111行)     # 侧边栏图标
├── mimic/                          # 仿钉钉模式: 卡片式 + 自动布局
│   ├── js/
│   │   ├── baseNodeModel.js (29行)
│   │   ├── baseNodeView.js (70行)
│   │   ├── mimic.js (557行)        # 自动布局引擎（自研核心）
│   │   ├── start.js / between.js / end.js
│   │   ├── serial.ts / parallel.ts / inclusive.ts
│   │   ├── gatewayModel.ts / gatewayView.ts
│   │   └── skip.ts (271行)
│   └── vue/
│       ├── baseNode.vue (189行)    # 卡片式节点外观
│       └── EdgeTooltip.vue (155行) # 连线悬浮面板
└── common/                         # 双模式共享
    ├── js/tool.js (347行)          # DefJson ↔ LogicFlow 数据转换
    └── vue/
        ├── propertySetting.vue (441行)     # 属性配置抽屉
        ├── baseInfo.vue (1269行)           # 流程基础信息
        ├── start.vue / between.vue / ...   # 各节点属性面板
        ├── selectUser.vue (1323行)         # 办理人选择器
        └── nodeExtList.vue (576行)         # 扩展属性
```

### 3.6 后端接口完整清单

设计器涉及的后端接口（来源：`warm-flow-ui/src/api/flow/definition.js`）：

| 接口 | 方法 | 用途 |
|------|------|------|
| `warm-flow/save-json` | POST | 保存设计器画布数据 |
| `warm-flow/query-def/{id}` | GET | 加载流程定义 |
| `warm-flow/query-flow-chart/{id}` | GET | 加载只读流程图（含着色） |
| `warm-flow/handler-type` | GET | 办理人权限类型列表 |
| `warm-flow/handler-result` | GET | 办理人查询结果 |
| `warm-flow/handler-feedback` | GET | 办理人名称回显 |
| `warm-flow/handler-dict` | GET | 办理人选择项 |
| `warm-flow/published-form` | GET | 已发布表单定义列表 |
| `warm-flow/node-ext` | GET | 扩展属性配置项 |
| `warm-flow/listener-list` | GET | 监听器列表 |

## 4. 推荐方案

### 4.1 混合方案（推荐）

```
┌────────────────┬──────────────────────────────┐
│ 功能            │ 方案                        │
├────────────────┼──────────────────────────────┤
│ 待办列表        │ 自建页面                     │
│ 审批页面        │ 自建页面（流程图 + 操作栏）  │
│ 审批操作栏      │ 自建页面（按钮 + API）       │
│ 审批轨迹        │ 自建页面（时间轴）           │
│ 动态表单        │ 自建页面（form-create 直连） │
│ 流程设计器      │ 新标签页打开 / iframe 嵌入    │
└────────────────┴──────────────────────────────┘
```

**理由**：流程设计器是唯一一个和业务页面"无交互"的独立工具——管理员在后台配置流程，不需要嵌入到业务操作流程中。即使要用 iframe，也是在管理后台新开一个 tab 或弹窗，不存在性能和通信复杂度问题。

### 4.2 全自建方案（激进）

适用于：设计器需要深度定制的场景（自定义节点外观、自定义属性面板、特殊校验逻辑）。

**前置条件**：
- 你的项目已是 Vue 3 + Element Plus 技术栈
- 或愿意为设计器部分引入上述依赖

**操作步骤**：
1. 复制 `design/` 目录（37 个文件）到项目
2. 安装 `@logicflow/core` / `@logicflow/extension` / `element-plus` 等依赖
3. 在 `flow-design/index.vue` 中找到初始化入口，复制到自己的路由页面
4. 重新配置 API baseURL 和认证 Token 注入方式
5. 后续 Warm-Flow 升级时需要同步维护这部分源码

**风险**：
- 版本升级维护成本：每次 Warm-Flow 发版，设计器可能有新功能或修正，需要手动 diff 合并
- 依赖版本锁定：LogicFlow 2.x、Element Plus 版本需与源码兼容

## 5. 各场景难度汇总

| 功能模块 | 行数参考 | 自建难度 | 说明 |
|---------|---------|---------|------|
| 流程图查看（SVG 渲染） | ~200 | ⭐ | 后端已提供坐标，只管渲染和着色 |
| 审批操作栏 | ~100 | ⭐ | 几个按钮 + 文本框 + API 调用 |
| 审批轨迹时间轴 | ~200 | ⭐⭐ | 后端返回历史列表，前端做时间轴 UI |
| 待办/已办列表 | ~300 | ⭐⭐ | CRUD 分页查询 |
| 动态表单渲染 | ~50 | ⭐⭐ | 引入 form-create npm 包即可 |
| 静态业务表单 | 业务而定 | ⭐⭐ | 本来就要写的业务代码 |
| 流程设计器（源码拷贝） | ~8,000 | ⭐⭐⭐ | 文件量大，依赖多，需适配 |
| 流程设计器（自研） | 上万 | ⭐⭐⭐⭐⭐ | 需要实现拖拽引擎，不推荐 |

## 6. 关键文件索引

### 后端
- `ChartService.java` — 流程图状态着色（`warm-flow-core/.../service/ChartService.java`）
- `InsServiceImpl.java` — 流程实例启动（调用 chartService）（`warm-flow-core/.../service/impl/InsServiceImpl.java`）
- `TaskServiceImpl.java` — 审批流转（调用 chartService）（`warm-flow-core/.../service/impl/TaskServiceImpl.java`）
- `WarmFlowService.java` — UI 接口汇总（`warm-flow-plugin-ui-core/.../ui/service/WarmFlowService.java`）
- `WarmFlowController.java` — REST 接口定义（`warm-flow-plugin-ui-sb-web/.../ui/controller/WarmFlowController.java`）

### 前端
- `design/common/js/tool.js` — DefJson ↔ LogicFlow 数据转换核心
- `design/mimic/js/mimic.js` — 仿钉钉模式自动布局引擎
- `views/flow-design/index.vue` — 设计器主入口（1,634 行）
- `views/flow-design/flowChart.vue` — 只读流程图页面（867 行）
- `api/flow/definition.js` — 所有设计器相关 API 定义
- `api/form/form.js` — 表单相关 API 定义

## 7. 相关链接

- [Warm-Flow 引擎集成](warm-flow-engine-integration.md)（TODO）
- [God Workbench 配置参考](god-workbench-config-reference.md)
