# Warm-Flow Visual Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增一个基于源码联调的 `warm-flow-demo-app`，跑通“系统表单配置 -> 节点绑定 -> 条件分支 -> formCreate iframe 承载观察”的第一版可视化样板。

**Architecture:** 保持 `warm-flow-core`、`warm-flow-orm`、`warm-flow-plugin-*` 继续只承担框架职责，在仓库根目录新增独立业务联调模块 `warm-flow-demo-app` 作为 Spring Boot 壳。第一阶段优先复用 jar 内置暴露出来的 `warm-flow-ui` 资源，也就是复用 `warm-flow-plugin-ui-sb-web` 提供的表单配置界面、流程设计器和 `formCreate` 页面；第二阶段只新增宿主页静态文件，通过 `window.postMessage` 承载 `type=formCreate` 并打印通信日志。

**Tech Stack:** Maven multi-module, Spring Boot 3, MyBatis-Plus starter, Warm-Flow UI plugin, MySQL, Vue 3 + Vite（仅复用已有 `warm-flow-ui` 产物，不新增独立前端工程）

---

### Task 1: 新增 `warm-flow-demo-app` 模块骨架

**Files:**
- Modify: `pom.xml`
- Create: `warm-flow-demo-app/pom.xml`
- Create: `warm-flow-demo-app/src/main/java/org/dromara/warm/demo/WarmFlowDemoApplication.java`
- Create: `warm-flow-demo-app/src/main/resources/application.yml`
- Create: `warm-flow-demo-app/src/main/resources/schema-note.md`
- Verify: `warm-flow-plugin-ui-sb-web/pom.xml`
- Verify: `warm-flow-mybatis-plus-sb3-starter/pom.xml`

- [ ] **Step 1: 在根聚合 `pom.xml` 注册新模块**

在根 `pom.xml` 的 `<modules>` 节点中追加 `warm-flow-demo-app`：

```xml
<modules>
    <module>warm-flow-core</module>
    <module>warm-flow-orm</module>
    <module>warm-flow-plugin</module>
    <module>warm-flow-demo-app</module>
</modules>
```

- [ ] **Step 2: 创建 `warm-flow-demo-app/pom.xml`**

使用 Spring Boot 3 + MyBatis-Plus Starter + UI Web Plugin 的最小依赖，并在 demo 模块内显式导入 Spring Boot 3 的 dependencyManagement，避免根 POM 的 Spring Boot 2.7 版本管理泄漏进来：

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.dromara.warm</groupId>
        <artifactId>warm-flow</artifactId>
        <version>1.8.7</version>
    </parent>

    <artifactId>warm-flow-demo-app</artifactId>
    <name>warm-flow-demo-app</name>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${springboot3.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.dromara.warm</groupId>
            <artifactId>warm-flow-mybatis-plus-sb3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.dromara.warm</groupId>
            <artifactId>warm-flow-plugin-ui-sb-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <version>${mysql.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.8.1</version>
                <configuration>
                    <source>17</source>
                    <target>17</target>
                    <encoding>${project.build.sourceEncoding}</encoding>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: 创建 Spring Boot 启动类**

创建 `WarmFlowDemoApplication.java`：

```java
package org.dromara.warm.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WarmFlowDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(WarmFlowDemoApplication.class, args);
    }
}
```

- [ ] **Step 4: 创建本地运行配置**

创建 `application.yml`，只保留本次样板必要项：

```yaml
server:
  port: 8080

spring:
  application:
    name: warm-flow-demo-app
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/warm_flow_demo?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver

warm-flow:
  enabled: true
  ui: true
  data-source-type: mysql
  token-name: Authorization
```
 
- [ ] **Step 5: 加一个本地数据库说明文件**

创建 `schema-note.md`，防止实施者漏掉初始化动作：

```md
# Local DB Bootstrap

1. 创建数据库 `warm_flow_demo`
2. 执行 `sql/mysql/warm-flow-all.sql`
3. 启动 `warm-flow-demo-app`
4. 访问 `/warm-flow-ui/index.html`
```

- [ ] **Step 6: 编译确认新模块加入 reactor 后可解析依赖**

Run: `mvn -pl warm-flow-demo-app -am -DskipTests compile`

Expected:
- `warm-flow-demo-app` 编译通过
- Maven 优先使用当前仓库模块，不拉取远端同名 jar 作为主实现

- [ ] **Step 7: Commit**

```bash
git add pom.xml \
        warm-flow-demo-app/pom.xml \
        warm-flow-demo-app/src/main/java/org/dromara/warm/demo/WarmFlowDemoApplication.java \
        warm-flow-demo-app/src/main/resources/application.yml \
        warm-flow-demo-app/src/main/resources/schema-note.md
git commit -m "feat: add warm flow demo app skeleton"
```

### Task 2: 补最小业务接入实现，打通动态表单下拉

**Files:**
- Create: `FormPathQueryServiceImpl.java`
- Create: `DemoFormCatalogController.java`
- Create: `DemoPublishedFormReader.java`
- Create: `WarmFlowPublishedFormReader.java`
- Test: `FormPathQueryServiceImplTest.java`
- Verify: `FormPathService.java`

- [ ] **Step 1: 写失败测试，约束 `FormPathService` 输出树节点 ID**

创建 `FormPathQueryServiceImplTest.java`：

```java
package org.dromara.warm.demo.form;

import org.dromara.warm.flow.core.dto.Tree;
import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.utils.page.Page;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class FormPathQueryServiceImplTest {

    @Test
    public void queryFormPath_shouldUseFormIdAsTreeId() {
        DemoPublishedFormReader reader = () -> {
            Form form = org.dromara.warm.flow.core.FlowEngine.newForm()
                .setId(12L)
                .setFormName("请假发起表单")
                .setIsPublish(1);
            return new Page<>(Collections.singletonList(form), 1);
        };

        FormPathQueryServiceImpl service = new FormPathQueryServiceImpl(reader);

        List<Tree> trees = service.queryFormPath();

        Assert.assertEquals(1, trees.size());
        Assert.assertEquals("12", trees.get(0).getId());
        Assert.assertEquals("请假发起表单", trees.get(0).getName());
        Assert.assertEquals("0", trees.get(0).getParentId());
    }
}
```

- [ ] **Step 2: 运行测试确认当前实现缺失**

Run: `mvn -pl warm-flow-demo-app -Dtest=FormPathQueryServiceImplTest test`

Expected:
- FAIL
- 报 `FormPathQueryServiceImpl` 或 `DemoPublishedFormReader` 尚不存在

- [ ] **Step 3: 创建已发布表单读取接口**

创建 `DemoPublishedFormReader.java`：

```java
package org.dromara.warm.demo.form;

import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.utils.page.Page;

public interface DemoPublishedFormReader {

    Page<Form> readPublishedForms();
}
```

- [ ] **Step 4: 实现 `FormPathService` 的 demo 侧接入**

创建 `FormPathQueryServiceImpl.java`：

```java
package org.dromara.warm.demo.form;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.Tree;
import org.dromara.warm.flow.ui.service.FormPathService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FormPathQueryServiceImpl implements FormPathService {

    private final DemoPublishedFormReader reader;

    public FormPathQueryServiceImpl(DemoPublishedFormReader reader) {
        this.reader = reader;
    }

    @Override
    public List<Tree> queryFormPath() {
        return reader.readPublishedForms()
            .getList()
            .stream()
            .map(form -> new Tree()
                .setId(String.valueOf(form.getId()))
                .setName(form.getFormName())
                .setParentId("0"))
            .collect(Collectors.toList());
    }
}
```

- [ ] **Step 5: 为读取接口提供 Spring 实现**

创建 `WarmFlowPublishedFormReader.java`：

```java
package org.dromara.warm.demo.form;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.utils.page.Page;
import org.springframework.stereotype.Component;

@Component
public class WarmFlowPublishedFormReader implements DemoPublishedFormReader {

    @Override
    public Page<Form> readPublishedForms() {
        return FlowEngine.formService().publishedPage(null, 1, 200);
    }
}
```

- [ ] **Step 6: 补一个只读目录接口，方便宿主页侧调试**

创建 `DemoFormCatalogController.java`：

```java
package org.dromara.warm.demo.form;

import org.dromara.warm.flow.core.dto.ApiResult;
import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.utils.page.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo/forms")
public class DemoFormCatalogController {

    private final DemoPublishedFormReader reader;

    public DemoFormCatalogController(DemoPublishedFormReader reader) {
        this.reader = reader;
    }

    @GetMapping("/published")
    public ApiResult<Page<Form>> published() {
        return ApiResult.ok(reader.readPublishedForms());
    }
}
```

- [ ] **Step 7: 再跑测试，确认树节点契约成立**

Run: `mvn -pl warm-flow-demo-app -Dtest=FormPathQueryServiceImplTest test`

Expected:
- PASS

- [ ] **Step 8: 启动应用并验证设计器可见动态表单下拉**

Run: `mvn -pl warm-flow-demo-app spring-boot:run`

Expected:
- 启动成功
- 打开 `/warm-flow-ui/index.html` 后，流程基础信息或节点配置里的动态表单下拉能显示已发布表单

- [ ] **Step 9: Commit**

```bash
git add warm-flow-demo-app/src/main/java/org/dromara/warm/demo/form \
        warm-flow-demo-app/src/test/java/org/dromara/warm/demo/form
git commit -m "feat: wire demo form path service"
```

### Task 3: 新增 demo 宿主页，承载 `formCreate` iframe

**Files:**
- Modify: `formCreate.vue`
- Create: `DemoFlowWorkbenchController.java`
- Create: `demo-workbench.html`
- Create: `demo-workbench.js`
- Create: `demo-workbench.css`
- Test: `DemoFlowWorkbenchControllerTest.java`
- Verify: `formCreate.vue`
- Verify: `warm-flow-ui/README.md`

- [ ] **Step 1: 先修复 `formCreate` 在同一 iframe 下的审批区状态残留**

当前 `formCreate.vue` 在 `type === "1"` 和预览分支会把 `showApprovalFields` 置为 `false`，但切回 `type === "0"` 时没有恢复。

先把 `formInit()` 的待办分支改成：

```js
if (data.type === "0") {
  showApprovalFields.value = true
  reset()
  response = await executeLoad(data.taskId)
  if (!response.data) proxy.$modal.alertWarning("待办任务不存在")
  formContent = JSON.parse(response.data.form?.formContent)
} else if (data.type === "1") {
  showApprovalFields.value = false
  response = await hisLoad(data.taskId)
  if (!response.data) proxy.$modal.alertWarning("历史记录不存在")
  formContent = JSON.parse(response.data.form?.formContent)
} else {
  showApprovalFields.value = false
  response = await getFormContent(data.formId)
  formContent = JSON.parse(response.data)
}
```

目标：宿主页复用同一个 iframe 连续切换模式时，不残留审批区隐藏状态。

- [ ] **Step 2: 写失败测试，约束宿主页静态页面可访问**

创建 `DemoFlowWorkbenchControllerTest.java`：

```java
package org.dromara.warm.demo.workbench;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoFlowWorkbenchController.class)
class DemoFlowWorkbenchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void workbenchPage_shouldBeServed() throws Exception {
        mockMvc.perform(get("/demo/workbench"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Warm-Flow Demo Workbench")));
    }
}
```

- [ ] **Step 3: 运行测试确认页面不存在**

Run: `mvn -pl warm-flow-demo-app -Dtest=DemoFlowWorkbenchControllerTest test`

Expected:
- FAIL
- `/demo/workbench` 返回 404

- [ ] **Step 4: 创建宿主页 controller**

创建 `DemoFlowWorkbenchController.java`：

```java
package org.dromara.warm.demo.workbench;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DemoFlowWorkbenchController {

    @GetMapping("/demo/workbench")
    public String workbench() {
        return "forward:/demo-workbench.html";
    }
}
```

- [ ] **Step 5: 创建宿主页 HTML**

创建 `demo-workbench.html`：

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <title>Warm-Flow Demo Workbench</title>
  <link rel="stylesheet" href="/demo-workbench.css" />
</head>
<body>
  <div class="layout">
    <aside class="panel">
      <h1>Warm-Flow Demo Workbench</h1>
      <div class="section">
        <label>视角</label>
        <select id="actor">
          <option value="applicant">申请人</option>
          <option value="leader">主管</option>
          <option value="manager">经理</option>
        </select>
      </div>
      <div class="section">
        <label>任务 ID</label>
        <input id="taskId" type="text" placeholder="待办或历史任务 ID" />
      </div>
      <div class="section">
        <label>模式</label>
        <select id="mode">
          <option value="0">待办办理</option>
          <option value="1">已办历史</option>
          <option value="2">已发布表单预览</option>
        </select>
      </div>
      <div class="section">
        <label>表单 ID</label>
        <input id="formId" type="text" placeholder="表单预览时填写" />
      </div>
      <div class="actions">
        <button id="openFrame">打开表单</button>
        <button id="resetFrame">重置</button>
      </div>
      <pre id="log"></pre>
    </aside>
    <main class="main">
      <iframe id="formFrame" src="/warm-flow-ui/index.html?type=formCreate"></iframe>
    </main>
  </div>
  <script src="/demo-workbench.js"></script>
</body>
</html>
```

- [ ] **Step 6: 创建宿主页样式**

创建 `demo-workbench.css`：

```css
body {
  margin: 0;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  background: #f4f6f8;
}

.layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  min-height: 100vh;
}

.panel {
  padding: 24px;
  background: #132238;
  color: #f5f7fa;
}

.section {
  margin-bottom: 16px;
}

.section label {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
}

.section input,
.section select,
.actions button {
  width: 100%;
  box-sizing: border-box;
}

#log {
  min-height: 260px;
  background: #0d1727;
  color: #c6d2e1;
  padding: 12px;
  white-space: pre-wrap;
}

.main {
  padding: 16px;
}

#formFrame {
  width: 100%;
  min-height: calc(100vh - 32px);
  border: 1px solid #d8dee9;
  background: #fff;
}
```

- [ ] **Step 7: 创建宿主页脚本，固化 `postMessage` 契约**

创建 `demo-workbench.js`：

```js
const frame = document.getElementById("formFrame");
const actor = document.getElementById("actor");
const taskId = document.getElementById("taskId");
const formId = document.getElementById("formId");
const mode = document.getElementById("mode");
const log = document.getElementById("log");

function appendLog(payload) {
  log.textContent = `[${new Date().toLocaleTimeString()}] ${payload}\n` + log.textContent;
}

function buildInitPayload() {
  return {
    taskId: taskId.value.trim(),
    formId: formId.value.trim(),
    type: mode.value,
    disabled: mode.value !== "0"
  };
}

window.addEventListener("message", (event) => {
  if (!event.data || !event.data.method) return;

  appendLog(`receive -> ${JSON.stringify(event.data)}`);

  if (event.data.method === "formInit") {
    frame.contentWindow.postMessage({
      method: "formInit",
      data: buildInitPayload()
    }, "*");
  }

  if (event.data.method === "getOffsetHeight") {
    frame.style.minHeight = `${Math.max(event.data.offsetHeight + 48, 720)}px`;
  }
});

document.getElementById("openFrame").addEventListener("click", () => {
  appendLog(`open -> actor=${actor.value}, payload=${JSON.stringify(buildInitPayload())}`);
  frame.src = "/warm-flow-ui/index.html?type=formCreate";
  frame.contentWindow.postMessage({
    method: "formInit",
    data: buildInitPayload()
  }, "*");
});

document.getElementById("resetFrame").addEventListener("click", () => {
  appendLog("reset -> {}");
  frame.contentWindow.postMessage({ method: "reset" }, "*");
});
```

- [ ] **Step 8: 再跑页面测试**

Run: `mvn -pl warm-flow-demo-app -Dtest=DemoFlowWorkbenchControllerTest test`

Expected:
- PASS

- [ ] **Step 9: 手工验证宿主页和 iframe 通信**

Run: `mvn -pl warm-flow-demo-app spring-boot:run`

Expected:
- `/demo/workbench` 可访问
- iframe 首次加载后，日志区能看到 `formInit`
- 点击“打开表单”能回传 payload
- 提交成功后日志区能看到 `submitSuccess`

- [ ] **Step 10: Commit**

```bash
git add warm-flow-ui/src/components/form/formCreate.vue \
        warm-flow-demo-app/src/main/java/org/dromara/warm/demo/workbench \
        warm-flow-demo-app/src/main/resources/static/demo-workbench.* \
        warm-flow-demo-app/src/test/java/org/dromara/warm/demo/workbench
git commit -m "feat: add demo workbench for formcreate iframe"
```

### Task 4: 补 demo 运行说明与手工验证步骤

**Files:**
- Modify: `2026-06-16-warm-flow-dynamic-form-visual-test-design.md`
- Create: `warm-flow-demo-app/README.md`
- Create: `docs/superpowers/plans/2026-06-16-warm-flow-visual-demo-checklist.md`

- [ ] **Step 1: 创建 `warm-flow-demo-app/README.md`**

写清楚本地跑法、数据库准备和页面入口：

```md
# warm-flow-demo-app

## Run

1. 创建 MySQL 数据库 `warm_flow_demo`
2. 执行 `sql/mysql/warm-flow-all.sql`
3. 启动：`mvn -pl warm-flow-demo-app spring-boot:run`
4. 表单配置页：`/warm-flow-ui/index.html?type=form`
5. 流程设计器：`/warm-flow-ui/index.html`
6. 宿主页：`/demo/workbench`
```

- [ ] **Step 2: 创建手工验证 checklist**

创建 `2026-06-16-warm-flow-visual-demo-checklist.md`：

```md
# Warm-Flow Visual Demo Checklist

- [ ] 创建发起动态表单，包含 `leaveDays`
- [ ] 创建主管动态表单
- [ ] 创建经理动态表单
- [ ] 三份表单均发布
- [ ] 创建请假流程并绑定三份表单
- [ ] 配置 `default@@${formData.leaveDays > 3}` 分支
- [ ] `leaveDays = 2` 进入主管节点
- [ ] `leaveDays = 5` 进入经理节点
- [ ] `/demo/workbench` 能收到 `formInit`
- [ ] `submitSuccess` 日志可见
```

- [ ] **Step 3: 回写 spec 中的交付物位置**

在 spec 文档末尾追加实际实现产物位置：

```md
实施产物：
- `warm-flow-demo-app`
- `warm-flow-demo-app/README.md`
- `2026-06-16-warm-flow-visual-demo-checklist.md`
```

- [ ] **Step 4: 运行最小验证命令**

Run: `mvn -pl warm-flow-demo-app -am test`

Expected:
- demo 模块测试通过

- [ ] **Step 5: Commit**

```bash
git add warm-flow-demo-app/README.md \
        docs/superpowers/plans/2026-06-16-warm-flow-visual-demo-checklist.md \
        docs/superpowers/specs/2026-06-16-warm-flow-dynamic-form-visual-test-design.md
git commit -m "docs: add warm flow demo runbook"
```
