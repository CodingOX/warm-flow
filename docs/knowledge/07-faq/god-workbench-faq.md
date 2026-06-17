# God Workbench FAQ

## Q1：为什么发起后的第一步不应该出现审批意见？

因为第一步不是审批人待办，而是申请人继续填写业务表单。

对应语义：

- `type = "3"`：申请人继续填写
- `type = "0"`：审批人待办

如果首个任务误用 `type = "0"`，`formCreate` 就会显示审批意见区和审批按钮。

参考：

- `god-workbench-stage-summary.md`
- `god-workbench-config-reference.md`

## Q2：为什么“采集表单快照超时”不能只看前端源码？

因为本轮至少确认过三类根因：

1. 运行中的 `8080` 仍在使用旧 bundle
2. 当前停在历史表单视图，历史表单不允许导出快照
3. iframe 还没完成 `formInit` / 数据回填 / 校验器挂载

所以排查顺序应该是：

1. 先看浏览器真实加载的是哪个 bundle
2. 再看当前视图是不是历史表单
3. 再看是否收到了 `formReady`

参考：

- `god-workbench-stage-summary.md`
- `god-workbench-config-reference.md`

## Q3：为什么点了提交看起来“没反应”？

常见原因有两个：

1. 实际上提交前就失败了，卡在快照采集阶段
2. 表单值不完整，后端分支条件求值失败

最直接的判断方式不是看按钮，而是看日志区有没有出现：

- `开始采集表单快照`
- `子页面返回 formDataSnapshot`
- `正常流转完成`

如果缺第二条，通常是 iframe 没返回快照。

参考：

- `god-workbench-stage-summary.md`
- `god-workbench-config-reference.md`

## Q4：为什么空表单可能导致后端 500？

因为真实流程定义里可能依赖业务字段做条件分支。

例如请假流程会判断：

- `formData.leaveDays > 3`
- `formData.leaveDays <= 3`

如果 `formData` 里缺这个字段，条件求值就可能失败。

所以 God Workbench 不能绕开表单校验直接提交空数据。

参考：

- `god-workbench-stage-summary.md`
- `god-workbench-config-reference.md`

## Q5：为什么 `.variable(formData)` 不能替代 `.formData(formData)`？

因为这两者写入的运行时结构不同。

- `.variable(...)`
  - 往变量根层塞字段
- `.formData(...)`
  - 往 `variable.formData` 下塞业务表单

而现有表单与流程条件表达式依赖的是 `formData.xxx` 语义。

这是契约问题，不是代码风格问题。

参考：

- `god-workbench-stage-summary.md`

## Q6：为什么历史表单能力说“已有”，但页面上之前看不到？

因为底层能力早就有，缺的是宿主页把它组装出来。

已有能力：

- `type = "1"`
- `hisLoad(taskId)`
- `/warm-flow/execute/hisLoad/{hisTaskId}`

第一版缺的是：

- 根据 `instanceId` 拉历史步骤
- 在右侧显示列表
- 点击某条历史记录后重新初始化 `formCreate`

参考：

- `god-workbench-stage-summary.md`
- `god-workbench-config-reference.md`

## Q7：为什么改了 `formCreate.vue`，页面还是没变化？

最常见原因是浏览器加载到的不是这份新源码构建出来的资源。

本地运行时要重点看：

- `warm-flow-ui/dist`
- `warm-flow-plugin-vue3-ui/src/main/resources/warm-flow-ui`
- `warm-flow-plugin-vue3-ui/target/classes/META-INF/resources/warm-flow-ui`

尤其是最后一个目录，在 IDE 本地启动场景里经常才是真正被访问到的资源。

参考：

- `god-workbench-config-reference.md`

## Q8：为什么第一版没有保留“预览当前表单”按钮？

因为它没有形成清晰价值。

实际体验里，这个动作与“刷新当前表单/刷新 iframe”差异不明显，用户也感知不到真正收益。

第一版更适合只保留：

- 发起演练
- 提交演练
- 刷新两个 iframe
- 点击历史表单

参考：

- `god-workbench-stage-summary.md`
