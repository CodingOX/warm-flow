<p align="center">
	<img alt="logo" src="https://foruda.gitee.com/images/1726820610127990120/c8c5f3a4_2218307.png" width="100">
</p>
<h1 align="center" style="margin: 30px 0 30px; font-weight: bold;">Warm-Flow工作流 v1.2.7</h1>
<p align="center">
	<a href="https://gitee.com/dromara/warm-flow/stargazers"><img src="https://gitee.com/dromara/warm-flow/badge/star.svg?theme=dark"></a>
</p>

## 介绍

Warm-Flow国产工作流引擎🎉，其特点简洁轻量，五脏俱全，灵活扩展性强，是一个可通过jar引入设计器的工作流。

1. 简洁易用：只有7张表，代码量少，可快速上手和集成
2. 审批功能：支持通过、退回、任意跳转、转办、终止、会签、票签、委派和加减签、互斥和并行网关
3. 监听器与流程变量：支持五种监听器，可应对不同场景，灵活可扩展，参数传递，动态权限
4. 流程图：流程引擎自带流程图，可在不集成流程设计器情况下使用
5. 条件表达式：内置常见的和spel条件表达式，并且支持自定义扩展
6. 办理人表达式：内置${handler}和spel格式的表达式，可满足不同场景，灵活可扩展
7. orm框架扩展：目前支持MyBatis、Mybatis-Plus、Mybatis-Flex和Jpa，后续会由社区提供其他支持，扩展方便
8. 数据库支持：目前支持MySQL 、Oracle 和PostgreSQL，后续会继续支持其他数据库或者国产数据库
9. 多租户与软删除：流程引擎自身维护多租户和软删除实现，也可使用对应orm框架的实现方式
10. 支持角色、部门和用户等权限配置
11. 同时支持spring和solon
12. 兼容java8和java17,理论11也可以
13. 官方提供基于ruoyi-vue封装实战项目，很实用

```shell
希望一键三连，你的⭐️ Star ⭐️是我持续开发的动力，项目也活的更长
```
## 前端运行

```bash

# 安装依赖
yarn --registry=https://registry.npmmirror.com

# 启动服务
yarn dev

# 构建测试环境 yarn build:stage
# 构建生产环境 yarn build:prod
# 前端访问地址 http://localhost:80
```

## 动态表单集成与 iframe 挂载指南

本前端项目（`warm-flow-ui`）会被编译并内置在后端的 Jar 包（`warm-flow-plugin-ui-sb-web`）中。在实际项目开发中，您**无需**手动部署或修改此前端项目，只需在业务系统（如 Ruoyi / hh-vue）中使用 `iframe` 进行集成即可。

### 1. 挂载入口与路由分流

主入口 [App.vue](src/App.vue) 会读取 URL 中的 `type` 参数，并在加载时进行动态组件挂载：
* **流程设计器**（默认）：`iframe.src = "/warm-flow-ui/index.html"`
* **表单设计器**（`type=form`）：`iframe.src = "/warm-flow-ui/index.html?type=form&id=表单定义ID"`
* **流程轨迹图**（`type=FlowChart`）：`iframe.src = "/warm-flow-ui/index.html?type=FlowChart&id=流程实例ID"`
* **表单填报/查看**（`type=formCreate`）：`iframe.src = "/warm-flow-ui/index.html?type=formCreate"`

---

### 2. iframe 通信契约

在业务系统中使用 iframe 嵌入设计器或填报页时，需要通过 `window.postMessage` 进行主从页面的事件监听与数据交互。

#### 场景一：表单设计器 (`type=form`)
* **保存逻辑**：当设计器保存成功并写入后端数据库后，会向父页面投递关闭通知：
  ```javascript
  window.parent.postMessage({ method: "close" }, "*");
  ```
  父页面只需在 `message` 事件中监听到 `method: "close"` 时关闭当前弹窗即可。

#### 场景二：表单填报与审批页 (`type=formCreate`)
* **表单初始化 (formInit)**：
  1. iframe 载入后，向父页面请求初始化数据：
     ```javascript
     window.parent.postMessage({ method: "formInit" }, "*");
     ```
  2. 父页面捕获此消息，并将相关业务参数（如 `taskId`、表单加载类型等）回传给 iframe：
     ```javascript
     iframe.contentWindow.postMessage({
       method: "formInit",
       data: {
         taskId: "任务ID",
         formId: "表单ID",
         type: "0",      // 0:待办办理, 1:已办历史, 2:已发布表单预览, 3:申请人查看
         disabled: false // 是否禁用表单输入
       }
     }, "*");
     ```
* **审批提交成功 (submitSuccess)**：
  当用户在填报页点击“审批通过”或“退回”并由后台处理成功后，iframe 会通知父页面：
  ```javascript
  window.parent.postMessage({ method: "submitSuccess" }, "*");
  ```
  父页面收到此消息后应执行刷新待办列表、关闭弹窗等收尾工作。
