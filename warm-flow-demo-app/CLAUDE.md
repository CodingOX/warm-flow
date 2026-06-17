# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Warm-Flow Demo App — 本地联调壳模块，不修改核心框架源码。提供两个工作台页面承载 iframe 设计器和上帝模式演练。

## Commands

```bash
# 编译（跳过测试）
mvn -pl warm-flow-demo-app -am -DskipTests compile

# 编译 + 运行测试
mvn -pl warm-flow-demo-app -am test

# 运行单个测试类
mvn -pl warm-flow-demo-app -am test -Dtest=DemoGodWorkbenchSubmitControllerTest

# 启动应用（端口 8080）
mvn -pl warm-flow-demo-app -am spring-boot:run

# JS 单元测试
node --test src/test/js/demo-god-workbench-state.test.js
```

数据库初始化见 README.md。

## Architecture

### 模块定位

`warm-flow-demo-app` 是 warm-flow 父 POM 的子模块，依赖两个关键 starter：
- `warm-flow-mybatis-plus-sb3-starter` — 引擎 ORM 层
- `warm-flow-plugin-ui-sb-web` — 设计器 UI 内嵌 Jar

自身不打包 warm-flow 源码，纯粹通过 `@SpringBootApplication` + 静态页组装演示环境。

### 包结构（4 个功能域）

```
src/main/java/org/dromara/warm/demo/
├── WarmFlowDemoApplication.java          — 入口
├── definition/DemoFlowDefinitionController.java  — 已发布流程查询 + 发起
├── form/
│   ├── DemoFormCatalogController.java    — /demo/forms/published
│   ├── FormPathQueryServiceImpl.java     — 实现 FormPathService，为设计器提供表单树
│   ├── DemoPublishedFormReader.java      — 接口抽象
│   └── WarmFlowPublishedFormReader.java  — 调用 FlowEngine.formService()
└── workbench/
    ├── DemoFlowWorkbenchController.java  — forward 到静态页
    ├── DemoGodWorkbenchSubmitController.java  — 上帝模式提交 + 历史查询
    └── GodSubmitRequest.java             — 提交请求体
```

### 上帝模式（God Workbench）

三层协作模式，是 demo-app 最核心的功能：

1. **父页面** (`demo-god-workbench.html` + `.js`) — 统一控制流转，iframe 管理、postMessage 通信、表单快照采集
2. **FlowChart iframe** — `/warm-flow-ui/index.html?type=FlowChart&id={instanceId}`，运行态流程图
3. **formCreate iframe** — `/warm-flow-ui/index.html?type=formCreate`，通过 postMessage 加载待办表单

**postMessage 通信契约：**
- `formInit`：iframe 请求初始化 → 父页面回传 `{taskId, type, allowSnapshot}`
- `formReady`：iframe 通知父页面加载完成
- `collectFormData`：父页面请求采集表单快照
- `formDataSnapshot` / `formDataSnapshotError`：iframe 返回快照数据或拒绝
- `getOffsetHeight`：iframe 请求动态高度

**提交链路：** 父页面 `submitRun()` → 等待 `formReady` → `collectFormData` → 子页面返回 `formDataSnapshot` → POST `/demo/god-workbench/submit` → 更新运行时状态 → 刷新两个 iframe

**关键设计决策：**
- `GodWorkbenchSubmitFacade` 作为内部 `@Component` 留在 demo 模块内，不扩散到核心引擎
- `FlowParams.ignore(true)` 跳过办理人权限校验，实现上帝模式任意流转
- `formData` 挂在 `FlowParams.formData`（key 为 `variable.formData`），与现有 UI 办理链路一致
- 流程结束后 active view 的 `type` 和 `taskId` 清空，避免 iframe 按旧待办加载已归档任务

### 静态资源

所有静态页直接放在 `src/main/resources/static/`，由 Spring Boot 默认 Mapping。文件名即 URL 路径：
- `demo-workbench.html` → `/demo-workbench.html`
- `demo-god-workbench.html` + `.js` + `.css` + `.state.js` + `-mock.html` → 上帝模式全套

### 配置

`application.yml` 仅配置 datasource 和 `warm-flow.ui: true`（开启 UI 插件），无额外 bean 注册。依赖 `warm-flow-mybatis-plus-sb3-starter` 的 AutoConfiguration 完成引擎初始化。

### 测试

**Java 测试：** `@WebMvcTest` 切片测试，MockBean 替代真实业务组件。
- `DemoFlowWorkbenchControllerTest` — 验证 forward URL
- `DemoGodWorkbenchSubmitControllerTest` — 验证提交校验 + 返回运行时状态
- `FormPathQueryServiceImplTest` — 纯单元测试，手动构造 Reader

**JS 测试：** Node.js `node:test` 运行 `src/test/js/` 下的测试，验证 `resolveCurrentTaskFormView` 逻辑。

### 关键约定

- Long 类型 ID 序列化时必须转 String 传给前端，避免 JS Number 精度丢失
- `formCustom = Y` 时流程节点通过动态表单主键 ID 绑定系统表单
- 旧库升级必须补 `warm-flow_1.8.2.sql`，否则 `flow_form` 缺审计列导致 500
