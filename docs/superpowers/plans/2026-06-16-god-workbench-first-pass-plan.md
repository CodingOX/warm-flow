# Warm-Flow 上帝模式第一版闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于当前已存在的 demo 发起能力、`formCreate` iframe 通信能力和 `FlowChart` 查看能力，完成“上帝模式”第一版真实闭环：父页面发起实例、向 iframe 采集表单数据、调用后端 `ignore(true)` 推进流程、刷新两个 iframe。

**Architecture:** 保持 `warm-flow-core` 和 `warm-flow-ui` 的既有职责不变，只做两类增量：一是在 `warm-flow-demo-app` 增加 demo 专用上帝提交接口；二是在 `formCreate.vue` 增加一个极小的 `postMessage` 数据快照契约，供父页面收集当前表单值。真实宿主页单独落在 `warm-flow-demo-app` 静态资源中，保留现有 mock 页面作为设计稿，不直接覆盖。

**Tech Stack:** Spring Boot 3、Warm-Flow Core、Warm-Flow UI Vue3 iframe、MockMvc、原生 HTML/CSS/JS、`window.postMessage`

---

## File Structure

### 新增文件

- `warm-flow-demo-app/src/main/java/org/dromara/warm/demo/workbench/DemoGodWorkbenchSubmitController.java`
  - demo 专用“上帝模式提交”接口，接收 `taskId`、`message`、`formData`，调用 `taskService.skip(..., FlowParams.ignore(true))`
- `warm-flow-demo-app/src/main/java/org/dromara/warm/demo/workbench/GodSubmitRequest.java`
  - 请求 DTO，约束 `taskId`、`message`、`formData`
- `warm-flow-demo-app/src/test/java/org/dromara/warm/demo/workbench/DemoGodWorkbenchSubmitControllerTest.java`
  - controller 层参数校验与返回结构测试
- `warm-flow-demo-app/src/main/resources/static/demo-god-workbench.html`
  - 真实版上帝模式宿主页，保留当前 mock 作为设计稿
- `warm-flow-demo-app/src/main/resources/static/demo-god-workbench.js`
  - 真实版父页面脚本：加载 definition、发起实例、监听 iframe、采集 `formData`、调用提交接口、刷新 iframe
- `warm-flow-demo-app/src/main/resources/static/demo-god-workbench.css`
  - 从当前 mock 页面样式中抽离出的真实版样式文件，便于后续继续收敛

### 修改文件

- `warm-flow-ui/src/components/form/formCreate.vue`
  - 增加“默认关闭、显式开启”的 `collectFormData` -> `formDataSnapshot` 契约；保持现有普通办理逻辑不变
- `warm-flow-demo-app/src/main/java/org/dromara/warm/demo/workbench/DemoFlowWorkbenchController.java`
  - 新增真实页入口 `/demo/god-workbench`
- `warm-flow-demo-app/README.md`
  - 补充上帝模式页面入口和手工验证步骤

### 保持不动的文件

- `warm-flow-demo-app/src/main/resources/static/demo-god-workbench-mock.html`
  - 继续作为布局/交互设计稿，不承载真实调用
- `warm-flow-demo-app/src/main/java/org/dromara/warm/demo/definition/DemoFlowDefinitionController.java`
  - 继续负责 `published` 和 `start`，本轮不把“上帝提交”混进去

---

### Task 1: 补 demo 上帝模式提交接口

**Files:**
- Create: `warm-flow-demo-app/src/main/java/org/dromara/warm/demo/workbench/DemoGodWorkbenchSubmitController.java`
- Create: `warm-flow-demo-app/src/main/java/org/dromara/warm/demo/workbench/GodSubmitRequest.java`
- Create: `warm-flow-demo-app/src/test/java/org/dromara/warm/demo/workbench/DemoGodWorkbenchSubmitControllerTest.java`
- Reference: `warm-flow-demo-app/src/main/java/org/dromara/warm/demo/definition/DemoFlowDefinitionController.java`
- Reference: `warm-flow-core/src/main/java/org/dromara/warm/flow/core/dto/FlowParams.java`
- Reference: `warm-flow-core/src/main/java/org/dromara/warm/flow/core/service/TaskService.java`

- [ ] **Step 1: 写 controller 测试，先约束请求体和返回结构**

```java
package org.dromara.warm.demo.workbench;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoGodWorkbenchSubmitController.class)
class DemoGodWorkbenchSubmitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DemoGodWorkbenchSubmitController.GodWorkbenchSubmitFacade submitFacade;

    @Test
    void submit_shouldRejectBlankTaskId() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", " ");
        body.put("message", "同意");
        body.put("formData", Map.of("leaveDays", 5));

        mockMvc.perform(post("/demo/god-workbench/submit")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void submit_shouldReturnNextRuntimeState() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", "188800020004");
        body.put("message", "同意，流程配置验证通过");
        body.put("formData", Map.of("leaveDays", 5, "reason", "测试"));

        given(submitFacade.submit(any()))
            .willReturn(new DemoGodWorkbenchSubmitController.GodSubmitResult(
                "188800010001",
                "188800020005",
                "manager_approve",
                "RUNNING",
                false
            ));

        mockMvc.perform(post("/demo/god-workbench/submit")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.instanceId").value("188800010001"))
            .andExpect(jsonPath("$.data.taskId").value("188800020005"))
            .andExpect(jsonPath("$.data.currentNodeCode").value("manager_approve"))
            .andExpect(jsonPath("$.data.flowStatus").value("RUNNING"))
            .andExpect(jsonPath("$.data.ended").value(false));
    }
}
```

- [ ] **Step 2: 运行测试，确认接口尚不存在时失败**

Run: `mvn -pl warm-flow-demo-app -Dtest=DemoGodWorkbenchSubmitControllerTest test`
Expected:
- FAIL
- 提示 `DemoGodWorkbenchSubmitController` 或 `GodWorkbenchSubmitFacade` 不存在

- [ ] **Step 3: 创建请求 DTO，明确最小请求体**

```java
package org.dromara.warm.demo.workbench;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 上帝模式提交请求。
 * taskId 必填；formData 由父页面从 iframe 收集；message 对应 FlowParams.message。
 */
public class GodSubmitRequest {

    @NotBlank
    private String taskId;

    private String message;

    @NotNull
    private Map<String, Object> formData = new LinkedHashMap<>();

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getFormData() {
        return formData;
    }

    public void setFormData(Map<String, Object> formData) {
        this.formData = formData;
    }
}
```

- [ ] **Step 4: 创建 controller 和 facade，真正接到 `ignore(true)`**

```java
package org.dromara.warm.demo.workbench;

import jakarta.validation.Valid;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.ApiResult;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/demo/god-workbench")
public class DemoGodWorkbenchSubmitController {

    private final GodWorkbenchSubmitFacade submitFacade;

    public DemoGodWorkbenchSubmitController(GodWorkbenchSubmitFacade submitFacade) {
        this.submitFacade = submitFacade;
    }

    @PostMapping("/submit")
    public ApiResult<Map<String, Object>> submit(@Valid @RequestBody GodSubmitRequest request) {
        GodSubmitResult result = submitFacade.submit(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("instanceId", result.instanceId());
        body.put("taskId", result.taskId());
        body.put("currentNodeCode", result.currentNodeCode());
        body.put("flowStatus", result.flowStatus());
        body.put("ended", result.ended());
        return ApiResult.ok(body);
    }

    record GodSubmitResult(
        String instanceId,
        String taskId,
        String currentNodeCode,
        String flowStatus,
        boolean ended
    ) {}

    @Component
    static class GodWorkbenchSubmitFacade {

        GodSubmitResult submit(GodSubmitRequest request) {
            Long taskId = Long.valueOf(request.getTaskId());
            FlowParams flowParams = FlowParams.build()
                .skipType("PASS")
                .message(request.getMessage())
                // 与现有 UI 办理链路保持一致：业务表单值挂在 variable.formData 下。
                .formData(request.getFormData())
                .ignore(true);

            Instance instance = FlowEngine.taskService().skip(taskId, flowParams);

            Task query = FlowEngine.newTask();
            query.setInstanceId(instance.getId());
            List<Task> tasks = FlowEngine.taskService().list(query);

            Task nextTask = tasks.isEmpty() ? null : tasks.get(0);
            return new GodSubmitResult(
                String.valueOf(instance.getId()),
                nextTask == null ? "" : String.valueOf(nextTask.getId()),
                nextTask == null ? "" : nextTask.getNodeCode(),
                nextTask == null ? "FINISH" : nextTask.getFlowStatus(),
                nextTask == null
            );
        }
    }
}
```

- [ ] **Step 5: 运行 controller 测试，确认通过**

Run: `mvn -pl warm-flow-demo-app -Dtest=DemoGodWorkbenchSubmitControllerTest test`
Expected:
- PASS

- [ ] **Step 6: 提交这一块**

```bash
git add warm-flow-demo-app/src/main/java/org/dromara/warm/demo/workbench/DemoGodWorkbenchSubmitController.java \
        warm-flow-demo-app/src/main/java/org/dromara/warm/demo/workbench/GodSubmitRequest.java \
        warm-flow-demo-app/src/test/java/org/dromara/warm/demo/workbench/DemoGodWorkbenchSubmitControllerTest.java
git commit -m "feat: add god workbench submit api"
```

### Task 2: 给 formCreate 增加父页面采集表单快照契约

**Files:**
- Modify: `warm-flow-ui/src/components/form/formCreate.vue`
- Verify: `warm-flow-ui/src/components/form/formCreate.vue`
- Reference: `warm-flow-demo-app/src/main/resources/static/demo-workbench.js`

- [ ] **Step 1: 先写要补的消息分支注释和最小逻辑草案**

目标逻辑：

```js
const allowSnapshot = ref(false);

function handleMessage(event) {
  switch (event.data.method) {
    case "formInit":
      formInit(event.data.data);
      break;
    case "reset":
      reset();
      break;
    case "collectFormData":
      if (allowSnapshot.value) {
        emitFormDataSnapshot();
      }
      break;
  }
}
```

新增快照函数：

```js
function emitFormDataSnapshot() {
  window.parent.postMessage({
    method: "formDataSnapshot",
    data: {
      taskId: taskId.value,
      formData: formData.value
    }
  }, "*");
}
```

同时在 `formInit(data)` 里显式接收开关：

```js
allowSnapshot.value = !!data.allowSnapshot;
```

这样默认情况下，普通 iframe 办理页即使收到 `collectFormData` 也不会导出任何数据；只有上帝模式宿主页显式传入 `allowSnapshot: true` 才会开启。

- [ ] **Step 2: 修改 `formCreate.vue`，只补契约，不改原有提交流程**

```vue
<script setup name="formCreate">
import { getFormContent, executeLoad, executeHandle, hisLoad } from "@/api/form/form";
import formCreate from "@form-create/element-ui";
const { proxy } = getCurrentInstance();
const disabled = ref(false);
const fApi = ref(null);
// 运行态壳层自行决定是否显示提交动作；这些动作不属于 form-create 的业务表单 schema。
const showApprovalFields = ref(true);
const showApplicantSubmit = ref(false);
const allowSnapshot = ref(false);
const taskId = ref("");
const message = ref("");
const data = reactive({
  formData: {},
  rule: [],
  option: {},
  form: {},
  rules: {}
});
const { formData, rule, option, form, rules } = toRefs(data);

window.addEventListener("message", handleMessage);
window.parent.postMessage({ method: "formInit" }, "*");
onBeforeUnmount(() => {
  window.removeEventListener("message", handleMessage);
});

function handleMessage(event) {
  switch (event.data.method) {
    case "formInit":
      formInit(event.data.data);
      break;
    case "reset":
      reset();
      break;
    case "collectFormData":
      if (allowSnapshot.value) {
        emitFormDataSnapshot();
      }
      break;
  }
}

function emitFormDataSnapshot() {
  // 只有父页面在 formInit 中显式传入 allowSnapshot=true，才允许导出当前表单快照。
  window.parent.postMessage({
    method: "formDataSnapshot",
    data: {
      taskId: taskId.value,
      formData: formData.value
    }
  }, "*");
}

async function formInit(data) {
  let response;
  let formContent;
  taskId.value = data.taskId;
  allowSnapshot.value = !!data.allowSnapshot;
}
```

- [ ] **Step 3: 用真实前端构建确认没有引入脚本错误**

Run: `cd warm-flow-ui && yarn build:prod`
Expected:
- PASS

- [ ] **Step 4: 提交这一块**

```bash
git add warm-flow-ui/src/components/form/formCreate.vue
git commit -m "feat: add formcreate snapshot message contract"
```

### Task 3: 新增真实版上帝模式宿主页并接入真实闭环

**Files:**
- Create: `warm-flow-demo-app/src/main/resources/static/demo-god-workbench.html`
- Create: `warm-flow-demo-app/src/main/resources/static/demo-god-workbench.js`
- Create: `warm-flow-demo-app/src/main/resources/static/demo-god-workbench.css`
- Modify: `warm-flow-demo-app/src/main/java/org/dromara/warm/demo/workbench/DemoFlowWorkbenchController.java`
- Modify: `warm-flow-demo-app/README.md`
- Reference: `warm-flow-demo-app/src/main/resources/static/demo-god-workbench-mock.html`
- Reference: `warm-flow-demo-app/src/main/resources/static/demo-workbench.js`

- [ ] **Step 1: 先给真实页加入口，不动现有 `/demo/workbench`**

```java
package org.dromara.warm.demo.workbench;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * demo 工作台入口，交给静态页渲染。
 */
@Controller
public class DemoFlowWorkbenchController {

    @GetMapping("/demo/workbench")
    public String workbench() {
        return "forward:/demo-workbench.html";
    }

    @GetMapping("/demo/god-workbench")
    public String godWorkbench() {
        return "forward:/demo-god-workbench.html";
    }
}
```

- [ ] **Step 2: 给入口补测试，确认真实页可访问**

```java
@Test
void godWorkbenchPage_shouldBeServed() throws Exception {
    mockMvc.perform(get("/demo/god-workbench"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/demo-god-workbench.html"));
}
```

Run: `mvn -pl warm-flow-demo-app -Dtest=DemoFlowWorkbenchControllerTest test`
Expected:
- PASS

- [ ] **Step 3: 创建真实 HTML 骨架，复用当前 mock 布局结构**

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Warm-Flow 上帝视角演练台</title>
  <link rel="stylesheet" href="/demo-god-workbench.css" />
</head>
<body>
  <main class="shell">
    <header class="topbar">
      <div class="brand">
        <div class="brand-mark" aria-hidden="true"></div>
        <div>
          <h1>Warm-Flow 上帝视角演练台</h1>
          <p class="subtitle">用于配置人员真实走完流程：看定义、看节点、填表单、刷新运行态流程图。</p>
        </div>
      </div>
      <div class="mode-banner">
        <strong>God Mode: ignore(true)</strong>
        <p>父页面统一控制流转，演练提交跳过办理人权限校验。</p>
      </div>
    </header>

    <aside class="panel">
      <div class="panel-head">
        <h2>流程配置中心</h2>
        <p>加载已发布 definition，并从这里发起真实演练实例。</p>
      </div>
      <div class="panel-pad">
        <div class="field">
          <label for="definitionSelect">当前流程定义</label>
          <select id="definitionSelect" class="select"></select>
        </div>
        <div class="button-row">
          <button class="button" id="startBtn" type="button">发起演练</button>
          <button class="button secondary" id="previewBtn" type="button">只看说明</button>
        </div>
        <div class="def-list" id="defList"></div>
      </div>
    </aside>

    <section class="panel">
      <div class="chart-wrap">
        <div class="chart-toolbar">
          <div class="chart-title">
            <strong id="chartName">尚未选择流程</strong>
            <span id="chartMeta">尚未发起演练实例</span>
          </div>
        </div>
        <div class="iframe-shell">
          <div class="iframe-label">
            <strong>流程图 iframe</strong>
            <span class="iframe-url" id="flowFrameUrl">/warm-flow-ui/index.html?type=FlowChart&id=...</span>
          </div>
          <iframe id="flowFrame" class="preview-frame" title="流程图 iframe"></iframe>
        </div>
        <div class="log chart-log" id="log"></div>
      </div>
    </section>

    <aside class="panel right-panel">
      <div class="panel-head">
        <h2>节点操作与流转</h2>
        <p>父页面收集表单快照，再调用 demo 上帝接口推进流程。</p>
      </div>
      <div class="panel-pad">
        <div class="state-card">
          <div class="state-row"><span>当前实例</span><strong class="mono" id="instanceId">未发起</strong></div>
          <div class="state-row"><span>当前任务</span><strong class="mono" id="taskId">未发起</strong></div>
          <div class="state-row identity"><span>演练身份</span><strong class="mono">god-workbench</strong></div>
        </div>
        <div class="iframe-shell form-frame-shell">
          <div class="iframe-label">
            <strong>表单 iframe</strong>
            <span class="iframe-url" id="formFrameUrl">/warm-flow-ui/index.html?type=formCreate</span>
          </div>
          <iframe id="formFrame" class="preview-frame" title="表单 iframe"></iframe>
        </div>
        <div class="form-preview">
          <div class="preview-option">
            <span>
              <strong>本轮不实现 iframe 内 controls 视觉预览</strong>
              `showInnerControls` 保留在 mock 设计稿中，真实第一页只做父页面统一流转，不额外改动共享 `formCreate` 的审批区语义。
            </span>
          </div>
          <div class="field">
            <label for="message">流转意见（可选）</label>
            <textarea id="message" class="textarea">同意，流程配置验证通过。</textarea>
          </div>
          <div class="action-stack">
            <button class="button orange" id="submitBtn" type="button">提交演练：正常流转</button>
            <button class="button ghost" id="previewCurrentBtn" type="button">预览当前表单</button>
            <button class="button secondary" id="viewBtn" type="button">刷新两个 iframe</button>
          </div>
        </div>
      </div>
    </aside>
  </main>
  <script src="/demo-god-workbench.js"></script>
</body>
</html>
```

- [ ] **Step 4: 创建真实 JS 脚本，建立父子契约和后端调用**

```js
(function () {
  const logEl = document.getElementById('log');
  const defListEl = document.getElementById('defList');
  const selectEl = document.getElementById('definitionSelect');
  const flowFrame = document.getElementById('flowFrame');
  const formFrame = document.getElementById('formFrame');
  const messageEl = document.getElementById('message');

  let definitions = [];
  let currentRun = null;
  let pendingFormSnapshotResolve = null;

  function log(message) {
    const line = document.createElement('div');
    line.className = 'log-line';
    line.innerHTML = `<strong>${new Date().toLocaleTimeString()}</strong> · ${message}`;
    logEl.prepend(line);
  }

  function formCreateUrl() {
    return '/warm-flow-ui/index.html?type=formCreate';
  }

  function flowChartUrl(instanceId) {
    return `/warm-flow-ui/index.html?type=FlowChart&id=${instanceId}`;
  }

  function updateRuntimeState(nextRun) {
    currentRun = nextRun;
    document.getElementById('instanceId').textContent = nextRun ? nextRun.instanceId : '未发起';
    document.getElementById('taskId').textContent = nextRun ? (nextRun.taskId || '已结束') : '未发起';
    document.getElementById('chartMeta').textContent = nextRun
      ? `instanceId=${nextRun.instanceId} · taskId=${nextRun.taskId || 'END'} · current=${nextRun.currentNodeCode || 'END'}`
      : '尚未发起演练实例';
    document.getElementById('flowFrameUrl').textContent = nextRun
      ? flowChartUrl(nextRun.instanceId)
      : '/warm-flow-ui/index.html?type=FlowChart&id=...';
    document.getElementById('formFrameUrl').textContent = nextRun
      ? `${formCreateUrl()} + postMessage(taskId=${nextRun.taskId || ''})`
      : '/warm-flow-ui/index.html?type=formCreate';
  }

  function renderDefinitions() {
    selectEl.innerHTML = definitions.map(def => `<option value="${def.id}">${def.flowName} ${def.flowCode} v${def.version || '1'}</option>`).join('');
    defListEl.innerHTML = definitions.map(def => `
      <article class="def-card ${selectEl.value === def.id ? 'active' : ''}" data-id="${def.id}">
        <strong>${def.flowName}<span class="tag green">已发布</span></strong>
        <small>${def.flowCode} v${def.version || '1'}</small>
      </article>
    `).join('');
    defListEl.querySelectorAll('.def-card').forEach(card => {
      card.addEventListener('click', () => {
        selectEl.value = card.dataset.id;
        const def = definitions.find(item => item.id === card.dataset.id);
        document.getElementById('chartName').textContent = def ? def.flowName : '未选择';
        renderDefinitions();
      });
    });
  }

  async function loadDefinitions() {
    const resp = await fetch('/demo/definitions/published');
    const body = await resp.json();
    definitions = body.data.list || [];
    renderDefinitions();
    if (definitions[0]) {
      selectEl.value = definitions[0].id;
      document.getElementById('chartName').textContent = definitions[0].flowName;
      renderDefinitions();
    }
    log(`已加载 ${definitions.length} 个已发布流程`);
  }

  function previewDefinitionNotice() {
    const def = definitions.find(item => item.id === selectEl.value);
    if (!def) {
      log('当前没有可说明的流程定义');
      return;
    }
    log(`只看说明：当前选中 definition=<strong>${def.flowName}</strong>（${def.flowCode} v${def.version || '1'}），本页不使用 defId 直接打开运行态 FlowChart。`);
  }

  async function startRun() {
    const defId = selectEl.value;
    if (!defId) {
      log('没有可发起的流程定义');
      return;
    }
    const resp = await fetch(`/demo/definitions/start/${defId}`, { method: 'POST' });
    const body = await resp.json();
    const run = {
      instanceId: body.data.instanceId,
      taskId: body.data.taskId,
      currentNodeCode: 'CURRENT',
      flowStatus: 'RUNNING'
    };
    updateRuntimeState(run);
    refreshFrames();
    log(`发起真实演练实例：instanceId=<strong>${run.instanceId}</strong>，taskId=<strong>${run.taskId}</strong>`);
  }

  function sendFormInit() {
    if (!currentRun || !formFrame.contentWindow || !currentRun.taskId) {
      return;
    }
    formFrame.contentWindow.postMessage({
      method: 'formInit',
      data: {
        taskId: currentRun.taskId,
        type: '0',
        disabled: false,
        allowSnapshot: true
      }
    }, '*');
  }

  function refreshFrames() {
    if (!currentRun) {
      return;
    }
    flowFrame.src = flowChartUrl(currentRun.instanceId);
    if (currentRun.taskId) {
      formFrame.src = formCreateUrl();
    }
  }

  function requestFormSnapshot() {
    return new Promise((resolve, reject) => {
      if (!currentRun || !currentRun.taskId || !formFrame.contentWindow) {
        reject(new Error('当前没有可采集的待办表单'));
        return;
      }
      pendingFormSnapshotResolve = resolve;
      formFrame.contentWindow.postMessage({ method: 'collectFormData' }, '*');
      setTimeout(() => {
        if (pendingFormSnapshotResolve) {
          pendingFormSnapshotResolve = null;
          reject(new Error('采集表单快照超时'));
        }
      }, 5000);
    });
  }

  async function submitRun() {
    if (!currentRun || !currentRun.taskId) {
      log('请先发起演练实例，再提交上帝模式流转');
      return;
    }
    const snapshot = await requestFormSnapshot();
    const resp = await fetch('/demo/god-workbench/submit', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        taskId: currentRun.taskId,
        message: messageEl.value.trim(),
        formData: snapshot.formData || {}
      })
    });
    const body = await resp.json();
    updateRuntimeState(body.data);
    refreshFrames();
    log(`正常流转：后端使用 FlowParams.ignore(true)，返回 taskId=<strong>${body.data.taskId || 'END'}</strong>`);
  }

  window.addEventListener('message', (event) => {
    if (!event.data || !event.data.method) {
      return;
    }
    if (event.data.method === 'formInit') {
      sendFormInit();
    }
    if (event.data.method === 'getOffsetHeight') {
      const height = Number(event.data.offsetHeight) || 0;
      formFrame.style.height = `${Math.max(height + 24, 360)}px`;
    }
    if (event.data.method === 'formDataSnapshot' && pendingFormSnapshotResolve) {
      const resolve = pendingFormSnapshotResolve;
      pendingFormSnapshotResolve = null;
      resolve(event.data.data || {});
    }
  });

  document.getElementById('startBtn').addEventListener('click', startRun);
  document.getElementById('previewBtn').addEventListener('click', previewDefinitionNotice);
  document.getElementById('viewBtn').addEventListener('click', () => {
    if (!currentRun) {
      log('请先发起演练实例，再刷新 iframe');
      return;
    }
    refreshFrames();
    log(`父页面刷新两个 iframe：FlowChart 使用 instanceId=<strong>${currentRun.instanceId}</strong>，formCreate 使用 taskId=<strong>${currentRun.taskId || 'END'}</strong>`);
  });
  document.getElementById('submitBtn').addEventListener('click', async () => {
    try {
      await submitRun();
    } catch (error) {
      log(`提交演练失败：${error.message}`);
    }
  });
  document.getElementById('previewCurrentBtn').addEventListener('click', () => {
    if (!currentRun || !currentRun.taskId) {
      log('请先发起演练实例，再预览当前表单');
      return;
    }
    sendFormInit();
    log('预览当前表单：只重新渲染 iframe 表单数据，不推进流程');
  });

  loadDefinitions().catch(error => log(`初始化失败：${error.message}`));
})();
```

- [ ] **Step 5: 创建 CSS，先从当前 mock 页面抽离，不改视觉方向**

做法：
- 复制 `demo-god-workbench-mock.html` 中当前已经确认的样式
- 移除只服务于 `srcdoc` 的局部样式
- 保留 `chart-log`、`help-tip`、三栏布局、移动端断点

Run: `git diff --check -- warm-flow-demo-app/src/main/resources/static/demo-god-workbench.css`
Expected:
- PASS

- [ ] **Step 6: 更新 README，补充真实页入口和验证动作**

```md
## 页面入口

- 表单设计器：`http://localhost:8080/warm-flow-ui/index.html?type=form`
- 流程设计器：`http://localhost:8080/warm-flow-ui/index.html`
- 旧宿主页：`http://localhost:8080/demo/workbench`
- 上帝模式工作台：`http://localhost:8080/demo/god-workbench`

## 上帝模式手工验证

1. 打开 `http://localhost:8080/demo/god-workbench`
2. 选择一个已发布流程并点击“发起演练”
3. 等待 `formCreate` iframe 发送 `formInit`
4. 在表单中修改业务字段
5. 点击“提交演练：正常流转”
6. 观察日志区出现 `formDataSnapshot` 和后端返回的新 `taskId`
7. 观察 `FlowChart` iframe 和 `formCreate` iframe 同步刷新
```

- [ ] **Step 7: 运行后端测试和脚本语法检查**

Run: `mvn -pl warm-flow-demo-app -Dtest=DemoFlowWorkbenchControllerTest,DemoGodWorkbenchSubmitControllerTest test`
Expected:
- PASS

Run: `node --check warm-flow-demo-app/src/main/resources/static/demo-god-workbench.js`
Expected:
- PASS

- [ ] **Step 8: 启动应用做手工联调**

Run: `mvn -pl warm-flow-demo-app -am spring-boot:run`
Expected:
- 启动成功
- `http://localhost:8080/demo/god-workbench` 可访问
- 发起实例后中间日志区能看到 `formInit`
- 点击提交后能看到流程推进，且日志显示新 `taskId`
- 流程结束时 `taskId` 变空或显示 `END`

- [ ] **Step 9: 提交这一块**

```bash
git add warm-flow-demo-app/src/main/resources/static/demo-god-workbench.html \
        warm-flow-demo-app/src/main/resources/static/demo-god-workbench.js \
        warm-flow-demo-app/src/main/resources/static/demo-god-workbench.css \
        warm-flow-demo-app/src/main/java/org/dromara/warm/demo/workbench/DemoFlowWorkbenchController.java \
        warm-flow-demo-app/src/test/java/org/dromara/warm/demo/workbench/DemoFlowWorkbenchControllerTest.java \
        warm-flow-demo-app/README.md
git commit -m "feat: wire real god workbench flow"
```

## Self-Review

- Spec coverage:
  - 已覆盖 demo 上帝提交接口
  - 已覆盖 `formCreate` 父页面采集契约
  - 已覆盖真实工作台页面、iframe 刷新和日志闭环
  - 未扩散到驳回、任意跳转、多实例管理，符合本轮范围
- Placeholder scan:
  - 无 `TODO` / `TBD`
  - 每个代码步骤给了实际代码和命令
- Type consistency:
  - 请求 DTO 使用 `taskId/message/formData`
  - 前端快照消息统一为 `formDataSnapshot`
  - 后端返回统一为 `instanceId/taskId/currentNodeCode/flowStatus/ended`
