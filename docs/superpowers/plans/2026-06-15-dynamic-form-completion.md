# Dynamic Form Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 Warm-Flow 设计器里动态表单配置态闭环，明确 `formCustom = 'Y'` 时 `formPath` 存储动态表单主键 ID，并保证定义级配置、节点级配置、保存回显和运行态查看/办理链路一致。

**Architecture:** 继续复用现有 `queryDef() -> formPathList` 作为设计器统一数据源，不在设计器组件内新增第二套表单列表请求。后端只收口 `FormPathService`/`queryDef()` 的语义，前端聚焦文案修正、`N/Y` 切换清值、默认值推断修正和保存回显一致性，运行态保持按 `formId`/`id` 读取表单内容。

**Tech Stack:** Vue 3 + Vite + Element Plus, Java + Maven, Warm-Flow UI plugin, JUnit 4（仅 Java 基础测试依赖）

---

### Task 1: 收口动态表单数据语义

**Files:**
- Modify: `FormPathService.java`
- Modify: `WarmFlowService.java`
- Optional Modify: 业务侧 `FormPathService` 实现类（若当前仓库无实现，则仅在计划中注明由接入方实现）
- Verify: `README.md`

- [ ] **Step 1: 核对 `FormPathService` 的输出契约**

确认 `FormPathService.java` 当前只声明了树形列表接口，没有约束 `Tree.value` 语义：

```java
public interface FormPathService {

    /**
     * 查询自定义表单路径
     *
     * @return 自定义表单路径
     */
    List<Tree> queryFormPath();
}
```

目标契约：`formCustom = 'Y'` 时，前端写入 `formPath` 的值必须是动态表单主键 `id`，展示名称用 `name`/`label`，不能再按 `formCode+version` 理解。

- [ ] **Step 2: 为接口补充明确注释**

在 `FormPathService.java` 注释中明确要求：

```java
/**
 * 查询设计器可选的动态表单树。
 *
 * 约定：
 * 1. Tree.value 必须为已发布动态表单主键 ID；
 * 2. Tree.label/name 用于前端展示；
 * 3. 该值会在 formCustom = 'Y' 时写入 definition/node 的 formPath 字段。
 */
List<Tree> queryFormPath();
```

- [ ] **Step 3: 在 `queryDef()` 邻近位置补注释，固定 `formPathList` 语义**

修改 `WarmFlowService.java`，在 `queryDef()` 聚合 `formPathList` 的代码前补中文注释：

```java
// 设计器里的动态表单下拉统一走 formPathList。
// 约定 Tree.value 为动态表单主键 ID，前端在 formCustom = 'Y' 时写入 formPath。
FormPathService formPathService = FrameInvoker.getBean(FormPathService.class);
if (formPathService != null) {
    List<Tree> treeList = formPathService.queryFormPath();
    defJson.setFormPathList(TreeUtil.buildTree(treeList));
    if (id == null) {
        defJson.setFormCustom(FormCustomEnum.Y.name());
    }
}
```

- [ ] **Step 4: 手工检查 README / 现有说明是否与新语义冲突**

Run: `rg -n "formCode\\+version|表单唯一标识|动态表单" README.md warm-flow-ui/README.md warm-flow-ui/src -S`

Expected:
- 找出所有把 `Y` 模式描述成“唯一标识 / formCode+version”的位置
- 记录到后续前端文案任务统一修正

- [ ] **Step 5: Commit**

```bash
git add warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-ui-core/src/main/java/org/dromara/warm/flow/ui/service/FormPathService.java \
        warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-ui-core/src/main/java/org/dromara/warm/flow/ui/service/WarmFlowService.java
git commit -m "docs: clarify dynamic form id contract"
```

### Task 2: 收口定义级配置页文案与切换行为

**Files:**
- Modify: `baseInfo.vue`
- Verify: `flow-design/index.vue`

- [ ] **Step 1: 写出预期交互清单**

定义级页面目标行为：

```text
1. formCustom = N 时，formPath 表示页面路径；
2. formCustom = Y 时，formPath 表示动态表单主键 ID；
3. N -> Y 切换时清空旧路径字符串；
4. Y -> N 切换时清空旧表单 ID；
5. Y 模式文案不再叫“表单唯一标识”；
6. 下拉为空时给出明确提示。
```

- [ ] **Step 2: 修改 `baseInfo.vue` 模板文案**

将以下位置统一改名：

```vue
<el-form-item label="动态表单" prop="formPath" v-else-if="form.formCustom === 'Y'">
  <el-tree-select
    v-model="form.formPath"
    :data="formPathList"
    :props="{ value: 'id', label: 'name', children: 'children' }"
    value-key="id"
    placeholder="请选择已发布动态表单"
    check-strictly
  />
</el-form-item>
```

同时把“选择自定义表单”改为“选择动态表单”，避免再出现“唯一标识”“formCode+version”等误导语义。

- [ ] **Step 3: 在 `baseInfo.vue` 增加 `formCustom` 切换清值逻辑**

新增 watcher，最小实现如下：

```js
const lastFormCustom = ref(form.value.formCustom)

watch(() => form.value.formCustom, (next) => {
  if (lastFormCustom.value === next) return

  // N -> Y 或 Y -> N 都清空旧值，避免单字段双语义串值
  form.value.formPath = ""
  lastFormCustom.value = next
})
```

如果已有更贴近现有风格的初始化逻辑，可用 `onMounted + watch` 组合避免首次回填时被误清空。

- [ ] **Step 4: 在 `getFormData()` 前保证空值格式稳定**

确认 `getFormData()` 返回前不再保留历史脏值：

```js
function getFormData() {
  form.value.listenerType = form.value.listenerRows.map(row => row.listenerType).join(",")
  form.value.listenerPath = form.value.listenerRows.map(row => row.listenerPath).join("@@")
  form.value.formPath = form.value.formPath ? String(form.value.formPath).trim() : ""
  return form.value
}
```

- [ ] **Step 5: 构建前端验证模板语法无误**

Run: `cd warm-flow-ui && yarn build:prod`

Expected:
- Vite build 成功
- 无 `baseInfo.vue` 模板编译错误

- [ ] **Step 6: Commit**

```bash
git add warm-flow-ui/src/components/design/common/vue/baseInfo.vue
git commit -m "feat: normalize definition dynamic form selection"
```

### Task 3: 收口节点级配置页行为与默认值推断

**Files:**
- Modify: `between.vue`
- Modify: `propertySetting.vue`

- [ ] **Step 1: 修正文案与提示**

在 `between.vue` 中将这类提示：

```vue
content="填写自定义表单的唯一标识：如formCode+version"
```

改成：

```vue
content="选择已发布动态表单，保存值为动态表单主键 ID"
```

并把标签“表单唯一标识”改成“动态表单”。

- [ ] **Step 2: 增加节点级 `formCustom` 切换清值**

在 `between.vue` 中新增节点级清值逻辑：

```js
const lastFormCustom = ref(form.value.formCustom)

watch(() => form.value.formCustom, (next) => {
  if (lastFormCustom.value === next) return
  form.value.formPath = ""
  lastFormCustom.value = next
})
```

目标：节点级配置与定义级配置保持同一规则。

- [ ] **Step 3: 修正 `propertySetting.vue` 默认值推断**

把当前逻辑：

```js
n.properties.formCustom = JSON.stringify(n.properties) === "{}" ? "N" : (n.properties.formCustom ?
    n.properties.formCustom : props.formPathList && props.formPathList.length > 0 ? "Y" :"N");
```

改成更保守的规则：

```js
n.properties.formCustom = JSON.stringify(n.properties) === "{}"
  ? "N"
  : (n.properties.formCustom ? n.properties.formCustom : "N");
```

理由：是否有可选动态表单列表，不应该自动推导“当前节点必须用动态表单”。

- [ ] **Step 4: 保持 `formPath` 更新逻辑不变，但确认 `Y` 模式写入的是 ID**

确认 `propertySetting.vue` 里以下监听仍保留：

```js
watch(() => form.value.formPath, (n) => {
  props.lf.setProperties(objId.value, {
    formPath: n
  })
})
```

这里只更新值，不做额外转换；值语义已经在上游约束为动态表单主键 ID。

- [ ] **Step 5: 构建前端验证节点属性面板编译**

Run: `cd warm-flow-ui && yarn build:prod`

Expected:
- `between.vue` / `propertySetting.vue` 编译通过

- [ ] **Step 6: Commit**

```bash
git add warm-flow-ui/src/components/design/common/vue/between.vue \
        warm-flow-ui/src/components/design/common/vue/propertySetting.vue
git commit -m "feat: normalize node dynamic form behavior"
```

### Task 4: 核对序列化与回显链路一致性

**Files:**
- Modify: `tool.js`
- Verify: `flow-design/index.vue`
- Verify: `baseInfo.vue`
- Verify: `propertySetting.vue`

- [ ] **Step 1: 核对定义级序列化/反序列化字段**

确认 `tool.js` 中定义级字段双向保持一致：

```js
graphData.formCustom = definition.formCustom
graphData.formPath = definition.formPath
```

和：

```js
definition.formCustom = data.formCustom
definition.formPath = data.formPath
```

若无额外转换需求，不要引入 `Number()` 强转，避免破坏现有兼容性。

- [ ] **Step 2: 核对节点级序列化/反序列化字段**

确认节点级仍按原字段透传：

```js
lfNode.properties.formCustom = node.formCustom
lfNode.properties.formPath = node.formPath
```

及：

```js
node.formCustom = anyNode.properties.formCustom
if (anyNode.properties.formPath && String(anyNode.properties.formPath).trim()) {
  node.formPath = anyNode.properties.formPath.trim()
}
```

如果需要修改，只允许做 `trim()`/空值规范化，不要新增与 ID 语义无关的映射逻辑。

- [ ] **Step 3: 人工验证回显路径**

Run: `rg -n "formCustom|formPath" warm-flow-ui/src/components/design/common/js/tool.js warm-flow-ui/src/views/flow-design/index.vue -S`

Expected:
- 只存在一套定义级和节点级字段透传
- 没有第二套 `formId` 本地状态与 `formPath` 混用逻辑

- [ ] **Step 4: 如无需修改代码，则记录“仅验证，无改动”**

若本任务只完成了核对而无需改动，请在工作记录中注明：

```text
tool.js 当前已满足“formPath 原样透传”的要求，本轮不做逻辑改动。
```

- [ ] **Step 5: Commit**

```bash
git add warm-flow-ui/src/components/design/common/js/tool.js
git commit -m "refactor: keep dynamic form serialization consistent"
```

### Task 5: 补最小验证入口并完成手工联调清单

**Files:**
- Optional Create: `warm-flow-plugin/.../src/test/...`（若选择补 Java 最小测试）
- Modify: `2026-06-15-动态表单补齐实施说明.md`
- Verify: `formCreate.vue`
- Verify: `form.js`

- [ ] **Step 1: 明确当前自动化验证边界**

当前仓库可直接使用的验证手段：

```text
1. 前端：仅有 `yarn build:prod`，无 vitest/jest/playwright 现成脚本；
2. Java：存在 JUnit 依赖，但未发现现成覆盖此链路的测试目录；
3. 因此本轮至少要保留一份明确的手工联调清单。
```

- [ ] **Step 2: 手工核对运行态链路代码**

重点确认：

```js
response = await getFormContent(data.formId)
```

和：

```js
url: urlPrefix + 'warm-flow/form-content/' + id
```

这证明运行态按表单主键 `id` 读取内容，和本轮 `formPath` 语义收口一致。

- [ ] **Step 3: 在实施说明里追加“已确认语义”**

给 `2026-06-15-动态表单补齐实施说明.md` 追加一句明确说明：

```md
- 当前仓库中，`formCustom = 'Y'` 时，`formPath` 按动态表单主键 ID 使用，并由运行态 `getFormContent(id)` 读取表单内容。
```

- [ ] **Step 4: 执行前端构建验证**

Run: `cd warm-flow-ui && yarn build:prod`

Expected:
- 构建成功

- [ ] **Step 5: 如选择补 Java 最小测试，新增一个聚焦 `queryDef()` 的契约测试**

测试目标最小化为：`FormPathService` 返回树列表时，`queryDef(null)` 能把 `formPathList` 带回并在新增定义默认 `formCustom = Y`。

示例测试骨架：

```java
@Test
public void queryDef_shouldFillFormPathList_whenFormPathServiceExists() {
    // 使用可控 stub / mock 注入 FormPathService
    // 断言返回的 defJson.formPathList 非空，且 formCustom = "Y"
}
```

如果当前模块测试基建不足以低成本完成，则记录为后续专项，不在本轮强行扩展。

- [ ] **Step 6: 完成手工联调清单**

至少验证：

```text
1. 新增流程定义，N 模式保存/回显正常；
2. 新增流程定义，Y 模式可选动态表单并回显；
3. 节点级 N/Y 切换不串值；
4. formCustom = Y 的待办办理可打开动态表单；
5. formCustom = Y 的已办查看可展示历史表单；
6. 空列表不报错，有明确提示。
```

- [ ] **Step 7: Commit**

```bash
git add 2026-06-15-动态表单补齐实施说明.md
git commit -m "docs: record dynamic form validation contract"
```

### Task 6: 最终验证与收口

**Files:**
- Verify: 以上所有改动文件

- [ ] **Step 1: 运行前端构建总验证**

Run: `cd warm-flow-ui && yarn build:prod`

Expected:
- `vite build` 成功

- [ ] **Step 2: 运行 Java 模块最小编译验证**

Run: `mvn -pl warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-ui-core,warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-ui-sb-web -am test -DskipTests`

Expected:
- 相关模块编译成功
- 若命令失败，至少定位是环境问题还是代码编译问题

- [ ] **Step 3: 记录未覆盖风险**

在交付说明中明确：

```text
1. 当前前端缺少现成 UI 自动化测试；
2. 运行态全链路仍需手工联调；
3. 若接入方自定义 FormPathService 返回的不是表单主键 ID，则本轮语义收口会失效。
```

- [ ] **Step 4: 最终提交**

```bash
git add warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-ui-core/src/main/java/org/dromara/warm/flow/ui/service/FormPathService.java \
        warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-ui-core/src/main/java/org/dromara/warm/flow/ui/service/WarmFlowService.java \
        warm-flow-ui/src/components/design/common/vue/baseInfo.vue \
        warm-flow-ui/src/components/design/common/vue/between.vue \
        warm-flow-ui/src/components/design/common/vue/propertySetting.vue \
        warm-flow-ui/src/components/design/common/js/tool.js \
        2026-06-15-动态表单补齐实施说明.md
git commit -m "feat: complete dynamic form configuration flow"
```
