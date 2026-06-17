# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

warm-flow-plugin 是 Warm-Flow 工作流引擎的插件聚合模块，包含三类插件：JSON 序列化适配、表达式/模式扩展（Spring Boot / Solon）、流程设计器 Web UI。

父 pom: `org.dromara.warm:warm-flow:1.8.7`，本模块为 `<packaging>pom</packaging>` 聚合。

## Commands

```bash
# 全量构建插件模块（含依赖模块 warm-flow-core）
mvn clean install -pl warm-flow-plugin -am

# 仅构建 JSON 插件
mvn clean install -pl warm-flow-plugin/warm-flow-plugin-json -am

# 仅构建 modes 插件
mvn clean install -pl warm-flow-plugin/warm-flow-plugin-modes -am

# 仅构建 UI 插件
mvn clean install -pl warm-flow-plugin/warm-flow-plugin-ui -am

# 构建单个具体模块（例：jackson3 JSON 适配器）
mvn clean install -pl warm-flow-plugin/warm-flow-plugin-json/warm-flow-plugin-json-jackson3 -am
```

## Architecture

### 模块树

```
warm-flow-plugin/ (pom, 聚合)
├── warm-flow-plugin-json/                 — JSON 序列化适配，通过 Java SPI 注册
│   ├── warm-flow-plugin-json-jackson3/    — Jackson 3 (tools.jackson.core)
│   └── warm-flow-plugin-json-v1/          — Jackson 1/FastJson/Gson/Snack3/Snack4
├── warm-flow-plugin-modes/                — 表达式策略 + Bean 注册，分框架实现
│   ├── warm-flow-plugin-modes-sb/         — Spring Boot: SpEL 表达式 + @Bean 注册
│   └── warm-flow-plugin-modes-solon/      — Solon: SnEl 表达式 + Solon Plugin
└── warm-flow-plugin-ui/                   — 设计器 Web UI
    ├── warm-flow-plugin-ui-core/          — 纯 Java 服务逻辑（无框架依赖）
    ├── warm-flow-plugin-ui-sb-web/        — Spring Boot Controller + 静态资源
    ├── warm-flow-plugin-ui-solon-web/     — Solon Controller + 静态资源
    └── warm-flow-plugin-vue3-ui/          — 前端构建产物（warm-flow-ui 编译后拷贝到此）
```

### JSON 插件：Java SPI 注册

JSON 适配器实现了 `org.dromara.warm.flow.core.json.JsonConvert` 接口，**通过 Java ServiceLoader (SPI) 注册**，而非 Spring @Component：

- `META-INF/services/org.dromara.warm.flow.core.json.JsonConvert` 文件中列出实现类全限定名
- `json-v1` 注册了 5 个实现（Snack/Snack4/Jackson/FastJson/Gson），按 SPI 顺序优先匹配第一个可用的
- `json-jackson3` 单独拆出，因为 Jackson 3 与 Jackson 1/2 的 Maven 坐标不同（`tools.jackson.core` vs `com.fasterxml.jackson.core`），运行时只能二选一

核心引擎通过 `FlowEngine.jsonConvert` (全局静态) 调用，`WarmFlow.init()` 时通过 `ServiceLoader.load(JsonConvert.class)` 加载。

### Modes 插件：双框架 Bean 注册

两个子模块做同一件事：向 `FlowEngine` 注册所有 DAO/Service/Entity 的运行时实现。结构完全对称：

| 关注点 | Spring Boot (`modes-sb`) | Solon (`modes-solon`) |
|--------|--------------------------|------------------------|
| 配置类 | `BeanConfig` (@Configuration) | `BeanConfig` (@Condition) |
| 入口 | Spring AutoConfiguration | Solon Plugin (`WarmFlowModesSolonPlugin`) |
| 表达式引擎 | SpEL (`SpelHelper`) | SnEl (`SnElHelper`) |
| Bean 获取 | `SpringUtil.getBean()` | `Solon.context().getBean()` |
| 配置获取 | `Environment.getProperty()` | `Solon.cfg().get()` |
| 启动条件 | `warm-flow.enabled=true` (默认) | `${warm-flow.enabled:true} = true` |

**BeanConfig 初始化流程（两边一致）：**

1. `setNewEntity()` — 注册 8 种实体的 Supplier，如 `FlowEngine.setNewDef(FlowDefinition::new)`
2. `FrameInvoker.setCfgFunction(...)` / `FrameInvoker.setBeanFunction(...)` — 桥接框架能力
3. 创建/获取 `WarmFlow` 配置对象，调用 `warmFlow.init()` → 加载 SPI、初始化策略、全局监听器
4. `setExpression()` — 注册所有 `ExpressionStrategy` 实现到 `ExpressionUtil`
5. `FlowEngine.setFlowConfig(warmFlow)` — 设置全局配置

**SpEL 沙箱安全机制** (`SafeTypeLocator` + `SafeMethodResolver`)：Spring Boot 的 SpEL 表达式执行使用白名单类型访问 + 受限方法解析，防止表达式注入攻击。

### UI 插件：设计器集成架构

**warm-flow-plugin-ui-core** — 核心服务层，无框架依赖：
- `WarmFlowService`：**全部 public static 方法**，不是 Spring Bean。涵盖流程定义 CRUD、流程图渲染、办理人选择、表单管理、审批执行、动态表单校验。
- 6 个扩展点接口（位于 `org.dromara.warm.flow.ui.service`），供业务系统通过 `FrameInvoker.getBean()` 实现：
  - `HandlerSelectService` — 办理人选择器（tabs、查询、回显）
  - `HandlerDictService` — 办理人快捷选项
  - `CategoryService` — 流程分类树
  - `FormPathService` — 动态表单路径列表
  - `ChartExtService` — 流程图扩展信息（三原色、提示内容）
  - `NodeExtService` — 节点扩展属性
  - `ListenerListService` — 监听器列表
- 约定：`FrameInvoker.getBean(XxxService.class)` 返回 null 时使用默认行为

**warm-flow-plugin-ui-sb-web** — Spring Boot 适配：
- `WarmFlowUiConfig`：`@Configuration` + `@ConditionalOnProperty("warm-flow.ui")`，通过 `spring.factories` 自动装配
- 实现 `WebMvcConfigurer`，注册 `/warm-flow-ui/**` → `classpath:/META-INF/resources/warm-flow-ui/` 静态资源映射
- 两个 Controller：
  - `WarmFlowUiController` (`@RequestMapping("/warm-flow-ui")`)：匿名访问，仅 `/config` 端点（返回框架类型、tokenName 等配置）
  - `WarmFlowController` (`@RequestMapping("/warm-flow")`)：需认证，16 个 API 端点（流程/表单/审批/办理人/节点扩展等）

**warm-flow-plugin-ui-solon-web** — Solon 适配：
- `WarmFlowUiSolonPlugin` 实现 Solon `Plugin`，注册静态资源映射并触发 bean 扫描
- 同样的两个 Controller，使用 Solon 注解（`@Controller` + `@Mapping`）

**warm-flow-plugin-vue3-ui** — 纯资源模块：
- 将 `src/main/resources/` 内容打包到 `META-INF/resources/` 下
- 实际运行时由 `ui-sb-web` 或 `ui-solon-web` 的静态资源处理器对外暴露

**动态表单校验链** (`WarmFlowService.saveJson` → `validateDynamicFormConditions`)：
保存流程定义时，校验条件表达式中引用的 `formData.<field>` 字段是否存在于关联的动态表单中，字段名不匹配或使用旧版 `form.<field>` 格式会抛出 `FlowException`。

### 框架适配模式总结

| 机制 | 适用插件 | 说明 |
|------|---------|------|
| Java SPI (`ServiceLoader`) | json | `JsonConvert` 实现发现 |
| Spring `spring.factories` | ui-sb-web | 自动装配 `WarmFlowUiConfig` |
| Solon Plugin | modes-solon, ui-solon-web | `AppContext.beanMake` / `beanScan` |
| `FrameInvoker` | modes | 桥接 Bean 获取和配置读取 |

## 关键注意点

- **JSON 插件由 SPI 驱动，不是 Spring Bean**。修改 `JsonConvert` 实现时不要加 `@Component`，要在 `META-INF/services/...` 文件中注册。
- `WarmFlowService` 的所有方法都是 static，它不是 Spring Bean，依赖 `FlowEngine.*` 的全局静态单例。
- 业务扩展接口通过 `FrameInvoker.getBean()` 获取，未实现时返回 null，服务层会降级到默认行为。
- 前端静态资源在 `vue3-ui/src/main/resources/warm-flow-ui/`，运行时由 sb-web/solon-web 模块对外暴露 `/warm-flow-ui/**`。
- 条件表达式中动态表单字段引用必须使用 `formData.<field>` 形式，旧版 `form.<field>` 会报错。
