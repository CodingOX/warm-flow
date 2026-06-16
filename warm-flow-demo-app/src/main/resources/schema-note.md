# Local DB Bootstrap

1. 创建数据库 `warm_flow_demo`
2. 执行 `sql/mysql/warm-flow-all.sql`
3. 如果是旧库增量升级，再执行 `sql/mysql/v1-upgrade/warm-flow_1.8.2.sql`
4. 启动 `warm-flow-demo-app`
5. 访问 `/warm-flow-ui/index.html`
