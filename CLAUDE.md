# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Dromara Warm-Flow v1.8.7，轻量级国产工作流引擎。纯 Java 核心（无框架依赖）+ ORM 适配层 + Vue 3 前端设计器。7 张核心表，支持通过/退回/任意跳转/转办/委派/加签/减签/会签/票签/终止等全流程操作。

## Commands

### 后端构建 (Maven, JDK 8+)

```bash
# 全量构建
mvn clean install

# 仅构建核心引擎
mvn clean install -pl warm-flow-core -am

# 构建指定 ORM 适配（以 mybatis-plus 为例）
mvn clean install -pl warm-flow-orm/warm-flow-mybatis-plus -am
```

### 前端设计器 (warm-flow-ui, yarn)

```bash
cd warm-flow-ui
yarn --registry=https://registry.npmmirror.com
yarn dev           # 启动开发服务器
yarn build:prod    # 生产构建
```

- 技术栈: Vue 3.3 + Vite 5 + LogicFlow 2.x + Element Plus 2.4 + Pinia 2.1
- 前端编译后内嵌到后端 Jar 包（`warm-flow-plugin-ui-sb-web`），业务系统通过 iframe 集成

### 测试

测试代码不在本仓库，位于独立的 [warm-flow-test](https://gitee.com/dromara/warm-flow-test) 仓库。本仓库不包含可运行的测试。

## Architecture

### Maven 模块树

```
warm-flow (root, pom)
├── warm-flow-core/           — 纯 Java 引擎核心，无框架依赖
├── warm-flow-orm/
│   ├── warm-flow-mybatis/     — MyBatis 适配 (core + sb-starter + solon-plugin)
│   ├── warm-flow-mybatis-plus/— MyBatis-Plus 适配
│   └── warm-flow-easy-query/  — Easy-Query 适配
└── warm-flow-plugin/
    ├── warm-flow-plugin-json/ — JSON 序列化适配 (jackson/fastjson/gson/snack3)
    ├── warm-flow-plugin-modes/— 双模式扩展 (经典BPMN + 仿钉钉)
    └── warm-flow-plugin-ui/   — Web UI 组件（Jar 包嵌入）
```

### 核心设计

**FlowEngine** — 全局静态单例中枢。`FlowEngine.taskService()`、`FlowEngine.defService()` 等方式获取服务，通过 `FrameInvoker` 从 Spring/Solon 容器延迟获取 Bean，解耦框架依赖。

**实体抽象** — 9 个实体接口（Definition、Node、Skip、Instance、Task、HisTask、User、Form、RootEntity），核心定义接口，ORM 模块提供 MyBatis/JPA 的具体实现类。实体创建通过 `FlowEngine.newTask()` 等 Supplier 模式，避免核心模块直接引用 ORM 类。

**策略接口（5 种）：**
- `ConditionStrategy` — 条件分支表达式（默认 SpEL）
- `HandlerStrategy` — 办理人变量表达式（`${handler}` / SpEL）
- `ExpressionStrategy<T>` — 基类，SPI 自注册
- `ListenerStrategy` — 节点级监听器
- `VoteSignStrategy` — 票签策略

**监听器体系（4 种生命周期）：**
- `GlobalListener` — 全局唯一，`FlowEngine.initGlobalListener()` 注入
- `Listener` — 节点级监听器，通过 JSON 配置在流程定义中
- 生命周期钩子: `start` → `assignment` → `finish` → `create`
- `ListenerVariable` 在监听器间传递上下文，支持三级作用域累加

**Handler 扩展接口：**
- `PermissionHandler` — 办理人权限解析（`permissions()` 返回权限标识列表，`getHandler()` 返回当前用户标识）
- `DataFillHandler` — 实体字段自动填充
- `TenantHandler` — 多租户 ID 获取

**跨框架桥接** — `FrameInvoker`：通过 `setBeanFunction` / `setCfgFunction` 注入框架能力，ORM starter 在启动时调用。核心引擎通过 `FrameInvoker.getBean()` / `FrameInvoker.getCfg()` 获取 Bean 和配置，实现零框架依赖。

**JSON 抽象** — `JsonConvert` 接口，通过 SPI (`ServiceLoader`) 加载实现。插件模块提供 Jackson/Fastjson/Gson/Snack3 适配。`FlowEngine.jsonConvert` 全局持有。

**双模式设计器：**
- 经典模式 (`classics/`)：BPMN 风格拖拽
- 仿钉钉模式 (`mimic/`)：卡片式流程
- 共用组件在 `common/`，模式特定逻辑在对应目录

### 前端设计器关键设计

- 通过 `?type=` URL 参数分流：默认=流程设计器，`type=form`=表单设计器，`type=FlowChart`=流程图，`type=formCreate`=表单填报
- 业务系统通过 iframe 嵌入，使用 `window.postMessage` 通信
- 通信契约:
  - `formInit`: iframe → 父页面请求初始化数据 → 父页面回传 taskId/formId/type
  - `submitSuccess`: 审批提交成功后 iframe → 父页面通知刷新列表
  - `close`: 表单设计器保存后通知父页面关闭弹窗

### 数据表

7 张核心表 + 1 张表单表 + 1 张扩展表：`flow_definition`、`flow_node`、`flow_skip`、`flow_instance`、`flow_task`、`flow_his_task`、`flow_user`、`flow_form`、`flow_ext`

SQL 脚本按数据库分目录：`sql/{mysql,oracle,postgresql,sqlserver}/`，版本升级脚本在 `sql/{db}/v1-upgrade/`。

### 配置入口

`WarmFlow` 配置类（对应 yml 中 `warm-flow.*`），在 ORM starter 的 AutoConfig 中初始化，调用 `warmFlow.init()` 完成策略注册、监听器绑定、SPI 加载。

## Git 提交规范

```
init: 初始化
feat: 增加新功能
fix: 修复问题/BUG
perf: 优化/性能提升
refactor: 重构
revert: 撤销修改
style: 代码风格相关
update: 其他修改
upgrade: 升级版本
```

## 知识库沉淀

位置： docs/knowledge