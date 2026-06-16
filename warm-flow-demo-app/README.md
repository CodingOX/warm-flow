# warm-flow-demo-app

Warm-Flow 的 demo 壳模块，用于源码联调和本地工作台观察。

## 功能

- `/demo/forms/published`：查询已发布表单
- `/demo/workbench`：打开宿主页
- `/demo-workbench.html`：静态宿主页

## 启动

```bash
mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS warm_flow_demo DEFAULT CHARACTER SET utf8mb4"
mysql -uroot -proot warm_flow_demo < ../sql/mysql/warm-flow-all.sql
mvn -pl warm-flow-demo-app -am -DskipTests compile
mvn -pl warm-flow-demo-app -am spring-boot:run
```

## 页面入口

- 表单设计器：`http://localhost:8080/warm-flow-ui/index.html?type=form`
- 流程设计器：`http://localhost:8080/warm-flow-ui/index.html`
- 宿主页：`http://localhost:8080/demo/workbench`

## 说明

- 该模块只作为 demo 壳，不修改核心框架源码
- 宿主页通过 iframe 承载 `/warm-flow-ui/index.html?type=formCreate`
- `formCustom = Y` 时，流程节点通过动态表单主键 ID 绑定系统表单配置界面产出的表单
