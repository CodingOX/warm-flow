# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> 这是 `warm-flow-core` 模块的 CLAUDE.md，专注于引擎核心内部架构。全项目架构和构建命令见父级 `warm-flow/CLAUDE.md`。

## 包结构 (包: `org.dromara.warm.flow.core`)

```
core/
├── FlowEngine.java          — 全局静态单例中枢，提供服务获取和实体工厂
├── condition/               — 9 种条件策略实现 (eq/ne/gt/lt/ge/le/like/notLike + 抽象基类)
├── config/
│   └── WarmFlow.java        — yml 配置映射类，init() 触发监听器/策略/SPI 加载
├── constant/
│   ├── FlowCons.java        — 核心常量 (分隔符、SPEL/SNEL标识、雪花ID类型、表单标识)
│   └── FlowConfigCons.java  — 配置键常量
├── dto/                     — 数据传输对象
│   ├── FlowParams.java      — 流程跳转参数聚合类 (skipType/nodeCode/handler/variable/message/ignore等)
│   ├── FlowCombine.java     — 流程定义+节点+跳转的完整数据聚合
│   ├── FlowDto.java         — 表单数据返回 DTO
│   ├── DefJson.java         — 流程定义导入导出的 JSON 结构
│   ├── NodeJson.java        — 节点 JSON 序列化结构
│   ├── SkipJson.java        — 跳转 JSON 序列化结构
│   └── PathWayData.java     — 流程图连线元数据 (含连线路径坐标)
├── entity/                  — 8 个实体接口 (纯接口，无实现，ORM 模块提供具体类)
│   ├── RootEntity.java      — 基础实体 (id/createTime/updateTime/tenantId/delFlag)
│   ├── Definition.java      — 流程定义
│   ├── Node.java            — 流程节点
│   ├── Skip.java            — 跳转条件
│   ├── Instance.java        — 流程实例
│   ├── Task.java            — 待办任务
│   ├── HisTask.java         — 历史任务
│   └── User.java            — 办理人权限
├── enums/                   — 枚举
│   ├── NodeType.java        — 节点类型: START(0)/BETWEEN(1)/END(2)/SERIAL(3)/PARALLEL(4)/INCLUSIVE(5)
│   ├── SkipType.java        — 审批动作: PASS/REJECT/NONE
│   ├── FlowStatus.java      — 流程状态
│   ├── CooperateType.java   — 协作类型: 转办(2)/委派(3)/加签(6)/减签(7)
│   ├── ConditionType.java   — 条件表达式类型
│   ├── UserType.java        — 办理人类型
│   ├── ActivityStatus.java  — 激活状态 (挂起/激活)
│   ├── PublishStatus.java   — 发布状态 (未发布/已发布/已失效)
│   ├── ChartStatus.java     — 流程图状态颜色
│   ├── ModelEnum.java       — 设计器模型 (CLASSICS/MIMIC)
│   └── FrameworkType.java   — 框架类型 (SpringBoot/Solon)
├── exception/
│   └── FlowException.java   — 流程异常
├── handler/                 — Handler 扩展接口
│   ├── PermissionHandler.java — 办理人权限解析 (permissions + handler 标识)
│   ├── DataFillHandler.java   — 实体字段自动填充
│   ├── TenantHandler.java     — 多租户 ID 获取
│   └── DefaultHandlerStrategy.java — 默认办理人表达式策略
├── invoker/
│   └── FrameInvoker.java    — 跨框架桥接 (getBean/getCfg)，核心与框架解耦的关键
├── json/
│   └── JsonConvert.java     — JSON 序列化抽象接口，SPI 加载实现
├── keygen/                  — ID 生成器 (SnowFlakeId14/15/19位)
├── listener/                — 监听器体系
│   ├── GlobalListener.java  — 全局监听器 (start/assignment/finish/create 四个钩子)
│   ├── Listener.java        — 节点级监听器接口 + 生命周期类型常量
│   ├── ListenerVariable.java — 监听器间传递的上下文变量
│   └── ValueHolder.java     — 值持有者接口
├── orm/
│   ├── dao/
│   │   ├── WarmDao.java            — 通用 DAO 接口 (CRUD + 分页 + 批量)
│   │   ├── FlowDefinitionDao.java  — 流程定义 DAO (扩展 WarmDao)
│   │   ├── FlowNodeDao.java        — 节点 DAO
│   │   ├── FlowSkipDao.java        — 跳转 DAO
│   │   ├── FlowInstanceDao.java    — 实例 DAO
│   │   ├── FlowTaskDao.java        — 任务 DAO
│   │   ├── FlowHisTaskDao.java     — 历史任务 DAO
│   │   ├── FlowUserDao.java        — 办理人 DAO
│   │   └── FlowFormDao.java        — 表单 DAO
│   ├── agent/
│   │   └── WarmQuery.java          — 查询包装器 (排序/条件)
│   └── service/
│       ├── IWarmService.java       — 通用 Service 接口
│       └── impl/WarmServiceImpl.java — 通用 Service 实现 (委托到 WarmDao)
├── service/                 — 核心业务 Service 接口
│   ├── DefService.java      — 流程定义 (导入/导出/发布/复制)
│   ├── InsService.java      — 流程实例 (启动/删除/挂起/激活/清除变量)
│   ├── NodeService.java     — 节点 (获取下一节点/网关判断/路径计算)
│   ├── SkipService.java     — 跳转条件
│   ├── TaskService.java     — 待办任务 (通过/退回/任意跳转/撤销/终止/转办/委派/加签/减签/暂存/驳回)
│   ├── HisTaskService.java  — 历史任务
│   ├── UserService.java     — 办理人
│   ├── FormService.java     — 表单
│   └── ChartService.java    — 流程图元数据
│   └── impl/                — 各 Service 实现 (均 extends WarmServiceImpl)
├── strategy/                — 策略接口 (SPI 扩展点)
│   ├── ExpressionStrategy.java — 表达式策略基类 (getType/eval)
│   ├── ConditionStrategy.java  — 条件表达式，extend ExpressionStrategy<Boolean>
│   ├── HandlerStrategy.java    — 办理人表达式，extend ExpressionStrategy<List<String>>
│   ├── ListenerStrategy.java   — 监听器策略
│   └── VoteSignStrategy.java   — 票签策略
└── utils/                   — 工具类 (CollUtil, StringUtils, ObjectUtil, ExpressionUtil, ListenerUtil 等)
```

## 核心架构要点

### 实体抽象 + Supplier 工厂

所有 8 张表对应实体以 **纯接口** 定义（无框架依赖），ORM 模块负责提供具体类（如 MyBatis-Plus 的 `@TableName` 实体）。

通过 `FlowEngine` 的 Supplier 模式创建实体实例：

```java
// ORM starter 在初始化时注入 Supplier
FlowEngine.setNewDef(() -> new WarmFlowDefinition());
FlowEngine.setNewTask(() -> new WarmFlowTask());

// 核心引擎中使用
Definition def = FlowEngine.newDef();
Task task = FlowEngine.newTask();
```

接口继承链: `RootEntity` → `Definition/Node/Skip/Instance/Task/HisTask/User/Form`

### Service 层继承

```
IWarmService<T>          — 通用 CRUD 接口
  └── WarmServiceImpl<D extends WarmDao<T>, T> — 委托到 DAO 的通用实现
        └── DefServiceImpl / InsServiceImpl / TaskServiceImpl / ...
              (通过 setDao() 注入具体 FlowXxxDao)
```

### 流程跳转状态机 (skip 链路)

核心入口 `TaskService.skip(FlowParams, Task)`：

1. 参数校验 + 获取流程定义全数据 (`FlowCombine`)
2. 执行 `start` 监听器
3. 委派处理 (`handleDepute`)
4. 权限校验 (`checkAuth`)
5. 协作处理 — 或签/会签/票签 (`cooperate`)
6. 获取下一节点 (含网关判断)
7. 构建新增待办任务 (`addTask`)
8. 办理人变量替换 (`ExpressionUtil.evalVariable`)
9. 执行 `assignment` 监听器
10. 持久化：任务→历史任务，实例更新
11. 执行 `finish` / `create` 监听器

### Agent 抽象 (WarmDao → ORM 适配)

核心只依赖 `WarmDao<T>` 接口（位于 `orm/dao/`），定义了 CRUD + 分页 + 批量操作的抽象。ORM 模块通过实现该接口（如 MyBatis-Plus Mapper）桥接到具体持久层框架。`WarmQuery<T>` 提供排序和条件包装。

`FrameInvoker` 提供容器无关的 Bean/配置获取能力，ORM starter 启动时调用 `setBeanFunction` / `setCfgFunction` 注入。

### 5 种策略扩展点

| 策略 | 基类 | 用途 | SPI 注册方式 |
|------|------|------|------------|
| ConditionStrategy | `ExpressionStrategy<Boolean>` | 条件分支判断(eq/ne/gt/lt/ge/le/like/notLike) | 自动注册到 `EXPRESSION_STRATEGY_LIST` |
| HandlerStrategy | `ExpressionStrategy<List<String>>` | 办理人表达式解析 | 同上 |
| ExpressionStrategy | — | 表达式基类(支持自定义) | `setExpression()` |
| ListenerStrategy | — | 节点级监听器 | SPI ServiceLoader |
| VoteSignStrategy | — | 票签策略 | SPI ServiceLoader |

### 条件表达式语法

格式: `{条件类型}@@{字段}|{值}`，例如 `eq@@formData.leaveDays|4`

支持 SPEL 和 SNEL 两种表达式引擎，配置在 `FlowCons` 中定义。

### WarmFlow Config 初始化流程

`WarmFlow.init()` (由 ORM starter AutoConfig 调用):
1. `initTenantHandler()` — 租户处理器
2. `initDataFillHandler()` — 数据填充
3. `initPermissionHandler()` — 办理人权限
4. `initGlobalListener()` — 全局监听器
5. `printBanner()` — 打印版本横幅
6. `ChartStatus.initCustomColor()` — 流程图颜色
7. `spiLoad()` — 通过 ServiceLoader 加载 JsonConvert 实现

## 仅有的测试

核心模块仅包含条件表达式策略的单元测试 (`src/test/.../condition/AbstractConditionStrategyTest.java`)，覆盖嵌套 formData 变量的条件求值。完整集成测试在独立仓库 `warm-flow-test`。
