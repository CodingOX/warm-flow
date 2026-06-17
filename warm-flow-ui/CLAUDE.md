# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Warm-Flow 前端设计器，基于 Vue 3.3 + Vite 5 + LogicFlow 2.x + Element Plus 2.4。支持经典 BPMN 拖拽模式和仿钉钉卡片模式两种流程图设计方式，内嵌动态表单设计器（@form-create），编译后打包为后端 Jar 包（`warm-flow-plugin-ui-sb-web`），业务系统通过 iframe 集成。

## Commands

```bash
yarn --registry=https://registry.npmmirror.com  # 安装依赖
yarn dev                                          # 启动开发服务器 (端口 8083)
yarn build:prod                                   # 生产构建 → dist/
```

`yarn dev` 启动后自动打开浏览器。Vite 代理 `/dev-api` → `http://localhost:8080`。

## Architecture

### 路由：URL `?type=` 参数分流

项目不使用 vue-router，而是通过 `App.vue` 读取 URL 中的 `type` 参数，动态挂载对应视图组件：

| `?type=` | 组件 | 用途 |
|---|---|---|
| *(无)* | 入口导航页 | 展示三个入口卡片 |
| `design` | `views/flow-design/index.vue` | 流程设计器（经典/仿钉钉） |
| `form` | `views/form-design/index.vue` | 动态表单设计器 |
| `formCreate` | `views/form-design/formCreate.vue` | 表单填报/审批 |
| `FlowChart` | `views/flow-design/flowChart.vue` | 只读流程图查看（带状态着色） |

### 双模式流程设计器

`views/flow-design/index.vue` 是核心，通过 `modelValue`（`"classics"` / `"mimic"`）切换模式：

- **经典模式** (`components/design/classics/`) — BPMN 风格拖拽，带左侧节点侧边栏（`DiagramSidebar`），使用 LogicFlow 内置 `DndPanel` 替代方案，支持键盘删除
- **仿钉钉模式** (`components/design/mimic/`) — 卡片式流程，连线悬浮弹出 EdgeTooltip 选择添加节点类型（between/serial/parallel/inclusive），节点双击打开属性设置抽屉

共用组件在 `components/design/common/`：`propertySetting.vue`（属性配置抽屉）、`baseInfo.vue`（流程基础信息表单）、`selectUser.vue`（办理人选择器）、`nodeExtList.vue`（扩展属性列表）。

每种模式都有独立的自定义 LogicFlow 节点注册（`start`、`between`、`serial`、`parallel`、`inclusive`、`end`、`skip`），分别在 `classics/js/` 和 `mimic/js/` 下实现。

### 数据流

1. **启动时**：`App.vue` → `store/app.js` `fetchTokenName()` → 调用 `GET /warm-flow-ui/config` 获取后端 Token 名称/框架类型 → 从 URL 参数提取 token 存入 localStorage
2. **流程设计器加载**：读取 `?id=` 参数 → `GET /warm-flow/query-def/{id}` 获取流程 JSON → `tool.js` `json2LogicFlowJson()` 转换为 LogicFlow 格式 → 渲染画布
3. **保存**：`logicFlowJsonToWarmFlow()` 将画布数据转回后端格式 → `POST /warm-flow/save-json` → 成功后通过 `postMessage({ method: "close" })` 通知父页面

### API 层

`src/utils/request.js` — axios 实例，baseURL 来自 `VITE_APP_BASE_API` 环境变量。拦截器自动注入多 token（通过 `getTokenName()` 读取存储的 token 名称列表），含基于 sessionStorage 的重复提交防护。

- `src/api/flow/definition.js` — 流程定义 CRUD、办理人权限、监听器列表、已发布表单
- `src/api/form/form.js` — 表单内容保存/加载、流程办理/已办查看
- `src/api/anony.js` — `/warm-flow-ui/config` 获取后端配置

### iframe 通信契约

业务系统通过 iframe 嵌入，使用 `window.postMessage` 通信：

- **formInit**: iframe → 父页面请求初始化数据 → 父页面回传 `{ taskId, formId, type, disabled }`
- **submitSuccess**: 审批提交成功后 iframe → 父页面通知刷新
- **close**: 保存成功后 iframe → 父页面关闭弹窗

### 全局对象

- `$cache` — sessionStorage/localStorage 缓存工具（`src/plugins/cache.js`）
- `$modal` — 模态框/消息提示工具（`src/plugins/modal.js`）
- `$dark` — 暗黑模式 composable（`src/composables/useDark.js`），支持从 URL 参数 `?dark=true` 或 `postMessage` 切换

### 移动端适配

流程设计器内建移动端/平板的触摸事件桥接（`initTouchEventBridge()` — Pointer Events → Mouse Events 转换），支持：
- 画布拖动和缩放（fitView 自适应 + 延迟 resize）
- 节点/边点击和双击
- 长按模拟右键菜单
- 抽屉打开时禁止画布误触
- 屏幕旋转自动重绘（防抖）
