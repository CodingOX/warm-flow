# Warm-Flow 流程设计器自建集成指南

## 1. 概述

Warm-Flow 流程设计器基于 **LogicFlow 2.x** 构建，不是自研的绘图引擎，而是在 LogicFlow 基础上的深度二次封装。设计器源码全部可见，位于 `warm-flow-ui/src/components/design/`，约 **37 个文件 / 8,000 行代码**。

### 1.1 设计器架构

```
warm-flow-ui/src/components/design/
├── classics/            # 经典模式 (BPMN 拖拽风格): 7 个自定义节点 + 侧边栏
│   ├── js/              # LogicFlow 节点/边的注册
│   └── initClassicsData.json   # 初始画布模板
├── mimic/               # 仿钉钉模式 (卡片式): 自动布局 + Vue 组件渲染
│   ├── js/              # 节点/边注册 + 布局引擎 (mimic.js 557行)
│   ├── vue/             # 节点 Vue 组件
│   └── initMimicData.json      # 初始画布模板
└── common/              # 双模式共享
    ├── js/tool.js       # 核心: DefJson ↔ LogicFlow 数据互转 (347行)
    └── vue/             # 属性面板、办理人选择器、扩展属性等
        ├── propertySetting.vue  (441行)
        ├── baseInfo.vue         (1269行)
        ├── selectUser.vue       (1323行)
        ├── between.vue          (1220行)
        ├── start.vue            (189行)
        ├── gateway.vue / end.vue / skip.vue
        └── nodeExtList.vue      (576行)
```

### 1.2 设计器主入口

`views/flow-design/index.vue`（1,634 行）是整个设计器的**胶水代码**，负责：

1. 初始化 LogicFlow 画布实例（注册扩展、配置主题、网格吸附）
2. 根据 `modelValue`（`"CLASSICS"` / `"MIMIC"`）注册对应模式的 14 个自定义节点
3. 加载流程数据（`GET /warm-flow/query-def/{id}`）并转换渲染
4. 提供工具栏（缩放/撤销/清空/截图/下载）
5. 提供保存功能（`logicFlowJsonToWarmFlow` → `POST /warm-flow/save-json`）
6. 提供移动端触摸事件桥接

---

## 2. 集成方式对比

### 方案 A：独立页面部署（推荐）

将 `warm-flow-ui` 作为独立前端项目部署，业务系统通过**新标签页**打开设计器。

| 维度 | 说明 |
|------|------|
| **做法** | 直接使用 `warm-flow-ui` 项目，配置 API 代理指向你的后端 |
| **优势** | 零代码修改，设计器独立升级，不污染业务项目 |
| **劣势** | 需要多维护一个前端项目，跨页通信需要 URL 参数传 token |
| **适合场景** | 设计器是后台管理工具，不需要嵌入到业务流程中 |

### 方案 B：组件引入到现有 Vue 项目（推荐）

将设计器源码（37 个文件）复制到你的 Vue 项目中，作为组件使用。

| 维度 | 说明 |
|------|------|
| **做法** | 复制 `design/` 目录 + 入口文件 + API + 工具函数到项目 |
| **优势** | 设计器和业务系统在同一个页面内，无需跨域/跨页通信 |
| **劣势** | 需要对齐依赖版本，后续 Warm-Flow 升级需要同步维护 |
| **适合场景** | 设计器需要和业务页面深度交互 |

### 方案 C：iframe 嵌入

具体细节见 [warm-flow-iframe-vs-selfbuild.md](warm-flow-iframe-vs-selfbuild.md)。本文重点讲解**方案 B**。

---

## 3. 方案 B：组件引入到 Vue 项目 — 完整步骤

### 3.1 前置条件

你的 Vue 项目需满足以下技术栈：

| 依赖 | 版本要求 | 用途 |
|------|---------|------|
| Vue | 3.x | 框架必须一致 |
| Element Plus | 2.4.x | 属性面板、办理人选择器等 UI |
| `@logicflow/core` | ^2.1.11 | 画布引擎 |
| `@logicflow/extension` | ^2.1.15 | 截图、菜单、连线插入扩展 |
| Pinia | 2.x | 状态管理（或可替换为其他方案） |
| axios | 任何版本 | API 调用 |
| `file-saver` | 2.x | 可选，JSON 下载 |

**不需要的依赖**（如果你的项目只引入设计器）：
- `@form-create/designer` — 流程设计器不需要动态表单设计器
- `@form-create/element-ui` — 同上
- `sass` — 设计器 CSS 不使用 SCSS
- `vite-plugin-svg-icons` — 设计器支持替换为纯文本/Element Icon

### 3.2 文件映射清单

将以下文件从 `warm-flow-ui` 复制到你的项目中：

```
# ===== 设计器核心（必须） =====
src/components/design/
├── classics/js/start.js
├── classics/js/between.js
├── classics/js/end.js
├── classics/js/serial.js
├── classics/js/parallel.js
├── classics/js/inclusive.js
├── classics/js/skip.js
├── classics/js/sidebarIcons.js
├── classics/initClassicsData.json
├── mimic/js/start.js
├── mimic/js/between.js
├── mimic/js/end.js
├── mimic/js/serial.ts
├── mimic/js/parallel.ts
├── mimic/js/inclusive.ts
├── mimic/js/gatewayModel.ts
├── mimic/js/gatewayView.ts
├── mimic/js/skip.ts
├── mimic/js/mimic.js
├── mimic/js/baseNodeModel.js
├── mimic/js/baseNodeView.js
├── mimic/vue/baseNode.vue
├── mimic/vue/EdgeTooltip.vue
├── mimic/initMimicData.json
├── common/js/tool.js
├── common/vue/propertySetting.vue
├── common/vue/baseInfo.vue
├── common/vue/start.vue
├── common/vue/between.vue
├── common/vue/gateway.vue
├── common/vue/end.vue
├── common/vue/skip.vue
├── common/vue/selectUser.vue
├── common/vue/nodeExtList.vue
├── common/vue/DiagramSidebar.vue
└── common/vue/LogoWarm.vue    # 如果存在

# ===== 主入口组件 =====
src/views/flow-design/index.vue       # 设计器主入口 (必须)

# ===== API 定义（必须） =====
src/api/flow/definition.js             # 流程定义 API

# ===== 辅助代码（必须） =====
src/utils/request.js                   # axios 实例 (或适配为你自己的)
src/utils/auth.js                      # Token 管理 (或适配为你自己的)
src/utils/ruoyi.js                     # 工具函数 (tansParams, parseTime)
src/plugins/cache.js                   # sessionStorage 缓存封装
src/plugins/modal.js                   # Element Plus 消息封装
src/plugins/index.js                   # 插件注册入口

# ===== 状态管理 =====
src/store/app.js                       # app store (appParams/fetchTokenName)

# ===== 暗黑模式（可选） =====
src/composables/useDark.js             # 暗黑模式 composable
src/config/themeConfig.js              # 主题颜色配置
```

### 3.3 代码改造要点

#### 3.3.1 去掉 `?type=` URL 参数依赖

原设计器通过 `App.vue` 解析 URL 的 `?type=design&id=xxx` 参数来加载。在组件化引入中，改为 props / route params 传参：

```vue
<template>
  <FlowDesigner
    :definition-id="definitionId"
    :disabled="false"
    @saved="onSaved"
  />
</template>
```

需要修改 `views/flow-design/index.vue` 中 `onMounted` 内的 `appParams` 读取逻辑，改为从 props 接收。

#### 3.3.2 替换 API 调用地址

原设计器调用 `warm-flow/query-def`、`warm-flow/save-json` 等接口。在你的项目中，需要确保 axios baseURL 指向你的后端地址。

如果使用你自己的 axios 实例，将 `src/api/flow/definition.js` 中的 `request` 替换为你项目的实例。

#### 3.3.3 替换认证机制

原设计器通过 `GET /warm-flow-ui/config` 获取 token name，然后从 URL 参数取 token 存入 localStorage。

在你的项目中，直接使用你已有的 Token 传递机制即可（如从 cookie 或 header 注入）。

#### 3.3.4 改造 store 依赖

原设计器使用 Pinia store 读取 `appParams`。在你的项目中有两种处理方式：

- **方式 1**：也使用 Pinia，创建一个类似的 `appStore`，通过 props 或 inject 传入参数
- **方式 2**：修改 `index.vue` 中的 `appParams` 引用，改为从 route query 或 props 读取

最小改造方案：在 `index.vue` 中将 `appParams` 改为 props：

```vue
<script setup name="Design">
const props = defineProps({
  definitionId: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  onlyDesignShow: { type: Boolean, default: true },
  modelValue: { type: String, default: 'CLASSICS' },
})

const emit = defineEmits(['saved', 'close'])
</script>
```

#### 3.3.5 改造 postMessage 通信

原设计器保存成功后调用 `window.parent.postMessage({ method: "close" }, "*")`。在组件化引入中，改为 emit 事件：

```js
// 原代码
function close() {
  window.parent.postMessage({ method: "close" }, "*");
}

// 改造后
const emit = defineEmits(['close'])
function close() {
  emit('close', { method: "close" })
}
```

### 3.4 最小集成示例

```vue
<!-- FlowDesigner.vue — 封装后的设计器组件 -->
<template>
  <div class="designer-wrapper">
    <Design ref="designRef" />
  </div>
</template>

<script setup>
import Design from '@/views/flow-design/index.vue'
import { defineProps } from 'vue'

const props = defineProps({
  definitionId: { type: String, default: '' },
})

// 需要确保 index.vue 能通过某种方式拿到 definitionId
// 改造点: 将原 appParams.value.id → props.definitionId
</script>
```

### 3.5 必须改造的文件明细

| 文件 | 改造点 | 难度 |
|------|--------|------|
| `views/flow-design/index.vue` | 将 `appParams` 替换为 props；将 `postMessage` 替换为 emit | ⭐⭐ |
| `api/flow/definition.js` | 替换 `request` 为你项目的 axios 实例 | ⭐ |
| `store/app.js` | 简化或移除，改为 props 传参 | ⭐ |
| `composables/useDark.js` | 根据你的主题方案决定是否保留 | ⭐ |
| `plugins/modal.js` | 如果你已有 $modal/ElMessage 则直接替换 | ⭐ |
| `plugins/cache.js` | 如果你不需要重复提交防护则可移除 | ⭐ |

### 3.6 不需要改动的文件

以下文件**纯复制即可**，不依赖项目特定上下文：

| 文件 | 原因 |
|------|------|
| `design/classics/js/*.js` | 纯 LogicFlow 节点注册，仅依赖 `@logicflow/core` |
| `design/mimic/js/*.js/ts` | 同上，依赖 LogicFlow + Vue |
| `design/mimic/vue/baseNode.vue` | 纯 Vue 组件，依赖 Element Plus |
| `design/common/js/tool.js` | 纯数据格式转换函数，零依赖 |
| `design/common/vue/*.vue` | 纯 UI 组件，依赖 Element Plus |
| `api/*.js`（除 request 实例） | 纯 API 调用定义 |

---

## 4. 核心数据流

### 4.1 加载流程（编辑已有流程）

```
初始化 → GET /warm-flow/query-def/{id}
            ↓
        DefJson (nodeList + skipList)
            ↓
    tool.js → json2LogicFlowJson()   ← 坐标解码
            ↓
        LogicFlow 格式 { nodes, edges }
            ↓
    lf.render(logicJson)             ← LogicFlow 渲染
```

### 4.2 保存流程

```
lf.getGraphData()                    ← 获取画布数据
    ↓
tool.js → logicFlowJsonToWarmFlow()  ← 坐标编码
    ↓
POST /warm-flow/save-json
    ↓
emit('saved') / 提示成功
```

### 4.3 坐标编解码格式

```js
// 节点坐标
// 编码 (LogicFlow → 后端): 
node.coordinate = `${x},${y}|${textX},${textY}`

// 解码 (后端 → LogicFlow):
const [xy, textXy] = coordinate.split('|')
lfNode.x = parseInt(xy.split(',')[0])
lfNode.y = parseInt(xy.split(',')[1])

// 连线坐标
// 编码:
skip.coordinate = `x1,y1;x2,y2;...|${textX},${textY}`

// 解码:
const points = coordinate.split('|')[0].split(';')
edge.pointsList = points.map(p => ({ x: parseInt(p.split(',')[0]), y: parseInt(p.split(',')[1]) }))
```

---

## 5. 按模块难度评估

### 5.1 只读流程图查看器（自建）

如果只需要**查看**流程图（带审批着色），不需要拖拽编辑：

| 文件 | 行数 | 需要复制 |
|------|------|---------|
| `views/flow-design/flowChart.vue` | 867 | ✅ |
| 14 个自定义节点注册文件 | ~700 | ✅ |
| `common/js/tool.js` | 347 | ✅（只需要 `json2LogicFlowJson`） |
| `api/flow/definition.js` | 94 | ✅（只需要 `queryFlowChart`） |

**查看器的依赖更少**：不需要 `Menu` 和 `InsertNodeInPolyline` 扩展，不需要侧边栏，不需要属性面板，不需要保存功能。

### 5.2 完整设计器（编辑 + 拖拽）

| 子模块 | 难度 | 文件数 | 行数 | 说明 |
|--------|------|-------|------|------|
| LogicFlow 初始化 + 节点注册 | ⭐⭐ | 1+14 | ~1,500 | 核心胶水，需要理解 LogicFlow 生命周期 |
| 经典模式节点 SVG 外观 | ⭐ | 7 | ~600 | 纯复制，不涉及逻辑修改 |
| 仿钉钉模式节点 Vue 渲染 | ⭐⭐ | 4 | ~300 | 需要 Vue + LogicFlow HtmlNode 理解 |
| 仿钉钉模式自动布局引擎 | ⭐⭐⭐ | 1 | 557 | 唯一自研算法 (`mimic.js`)，纯前端，直接复制 |
| 属性配置面板 | ⭐⭐ | 5+ | ~3,000 | 纯 UI 组件，依赖 Element Plus 表单 |
| 办理人选择器 | ⭐⭐ | 1 | 1,323 | 需要对接你的组织架构 API |
| 数据格式转换 | ⭐ | 1 | 347 | 纯函数，零依赖 |
| API 层适配 | ⭐ | 3 | ~150 | 替换 axios 实例 + Token 机制 |
| 暗黑模式适配 | ⭐ | 2 | ~300 | 可选，按需保留 |

### 5.3 改造总工作量估算

| 角色 | 工作量 | 说明 |
|------|--------|------|
| 前端（纯复制适配） | 2-4 小时 | 复制文件、改 import 路径、调通 API |
| 前端（改造 store + props） | 1-2 小时 | 把 URL 参数依赖改为 props |
| 前端（测试 + 微调样式） | 1-2 小时 | 确认双模式切换、保存、属性面板正常 |
| **合计** | **4-8 小时** | 一个人一天以内 |

---

## 6. 常见问题

### 6.1 设计器 API 需要后端提供吗？

设计器涉及的后端接口（共 10 个）：

| 接口 | 是否必须 | 说明 |
|------|---------|------|
| `POST warm-flow/save-json` | ✅ 必须 | 保存流程定义 |
| `GET warm-flow/query-def/{id}` | ✅ 必须 | 加载流程定义 |
| `GET warm-flow/query-flow-chart/{id}` | ❌ 只读查看器 | 流程图带状态着色 |
| `GET warm-flow/handler-type` | ⚠️ 有条件 | 使用办理人选择器时需要 |
| `GET warm-flow/handler-result` | ⚠️ 有条件 | 同上 |
| `GET warm-flow/handler-feedback` | ⚠️ 有条件 | 同上 |
| `GET warm-flow/handler-dict` | ⚠️ 有条件 | 同上 |
| `GET warm-flow/published-form` | ❌ 可选 | 属性面板中选择已发布表单时需要 |
| `GET warm-flow/node-ext` | ❌ 可选 | 扩展属性配置 |
| `GET warm-flow/listener-list` | ❌ 可选 | 监听器配置 |
| `GET warm-flow-ui/config` | ❌ 可替代 | token name 配置，自建时直接用你的认证方案 |

这些接口由 `warm-flow-plugin-ui-sb-web` 模块中的 `WarmFlowController` 提供。如果你自己写 Controller，只需封装 `FlowEngine` 的相关方法即可。

### 6.2 办理人选择器如何对接？

`selectUser.vue`（1,323 行）是设计器中**最大的组件**，它调用了：

- `handlerType()` — 获取左侧 tab 页签（如"部门"、"角色"、"用户"）
- `handlerResult()` — 按关键词查询人员/部门列表
- `handlerFeedback()` — 根据 ID 回显名称

在自建时，**你需要实现这 3 个接口**，返回你组织架构的数据。或者如果你不需要在流程设计器中选择办理人，可以直接简化办理人字段为文本输入框。

### 6.3 设计器风格和你的项目不一致？

设计器样式使用 CSS 变量（`--wf-*`）驱动，在 `baseInfo.vue` 和 `index.vue` 中定义了颜色、圆角、边框等变量。

你可以通过覆盖 CSS 变量来适配：

```css
.designer-wrapper {
  --wf-primary: #409eff;
  --wf-bg-white: #fff;
  --wf-border-light: #e2e8f0;
  --wf-text-primary: #303133;
  --wf-text-regular: #606266;
  --wf-radius: 8px;
}
```

### 6.4 Warm-Flow 升级了怎么办？

设计器源码可能会随 Warm-Flow 版本更新而变化。建议：

1. 在你的项目中标记所有复制的文件的来源版本号（在文件头部注释）
2. 升级时对比 `warm-flow-ui/src/components/design/` 目录的 diff
3. 重点关注 `tool.js`（数据格式可能变化）、`initLogicFlow` 部分（新特性）
4. 14 个节点注册文件通常稳定，变化小

---

## 7. 关键代码索引

| 作用 | 文件 | 行数参考 |
|------|------|---------|
| 设计器主入口（初始化和事件绑定） | `views/flow-design/index.vue` | 1,634 行 |
| 数据格式转换（DefJson ↔ LogicFlow） | `design/common/js/tool.js` | 347 行 |
| 仿钉钉模式自动布局 | `design/mimic/js/mimic.js` | 557 行 |
| 办理人选择器 | `design/common/vue/selectUser.vue` | 1,323 行 |
| 中间节点属性配置 | `design/common/vue/between.vue` | 1,220 行 |
| 流程基础信息 | `design/common/vue/baseInfo.vue` | 1,269 行 |
| 属性配置抽屉容器 | `design/common/vue/propertySetting.vue` | 441 行 |
| API 定义 | `api/flow/definition.js` | 94 行 |
| 经典模式侧边栏 | `design/common/vue/DiagramSidebar.vue` | 376 行 |

---

## 8. 决策树

```
你想用设计器吗？
├── 只查看流程图（带着色）→ 自建 SVG 渲染，调 chartService API
│                             难度: ⭐ 约 200 行代码
│
├── 需要拖拽编辑流程
│   ├── 设计器是后台工具，和业务页面无交互
│   │   └── iframe 嵌入 / 新标签页打开
│   │       难度: ⭐ 零代码
│   │
│   └── 设计器需要深度嵌入业务页面
│       ├── 你的项目是 Vue 3 + Element Plus
│       │   └── 方案 B：拷贝设计器源码
│       │       难度: ⭐⭐ 约 4-8 小时
│       │
│       ├── 你的项目是其他技术栈（React/Antd）
│       │   └── iframe 嵌入（推荐）
│       │
│       └── 你不需要 LogicFlow 拖拽，只是想自定义节点外观
│           └── 仅拷贝 14 个节点注册文件 + tool.js
│               难度: ⭐⭐ 约 2 小时
```

---

## 9. 相关链接

- [Warm-Flow 集成架构决策：iframe vs 自建](warm-flow-iframe-vs-selfbuild.md)
- [Warm-Flow 引擎集成指南](warm-flow-engine-integration.md)（TODO）
