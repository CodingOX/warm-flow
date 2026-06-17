(function () {
  const workbenchState = window.DemoGodWorkbenchState || {};
  const resolveCurrentTaskFormView = workbenchState.resolveCurrentTaskFormView || function (run) {
    if (!run || run.ended || !run.taskId) {
      return {
        type: "0",
        taskId: "",
        title: "表单 iframe"
      };
    }
    return {
      type: run.formType || "0",
      taskId: run.taskId,
      title: "表单 iframe"
    };
  };

  const definitionSelectEl = document.getElementById("definitionSelect");
  const defListEl = document.getElementById("defList");
  const logEl = document.getElementById("log");
  const flowFrame = document.getElementById("flowFrame");
  const formFrame = document.getElementById("formFrame");
  const messageEl = document.getElementById("message");
  const historyListEl = document.getElementById("historyList");
  const historyMetaEl = document.getElementById("historyMeta");
  const formFrameTitleEl = document.getElementById("formFrameTitle");

  const metricDefsEl = document.getElementById("metricDefs");
  const metricVersionEl = document.getElementById("metricVersion");
  const metricStatusEl = document.getElementById("metricStatus");
  const chartNameEl = document.getElementById("chartName");
  const chartMetaEl = document.getElementById("chartMeta");
  const instanceIdEl = document.getElementById("instanceId");
  const taskIdEl = document.getElementById("taskId");
  const flowFrameUrlEl = document.getElementById("flowFrameUrl");
  const formFrameUrlEl = document.getElementById("formFrameUrl");
  const submitBtnEl = document.getElementById("submitBtn");

  let definitions = [];
  let currentRun = null;
  let pendingSnapshot = null;
  let frameRefreshSeq = 0;
  let historyItems = [];
  let formFrameReadyState = {
    taskId: "",
    type: "",
    ready: false
  };
  let pendingFormReadyWaiters = [];
  let currentFormView = {
    type: "0",
    taskId: "",
    title: "表单 iframe"
  };

  function escHtml(value) {
    if (value == null) {
      return "";
    }
    return String(value).replace(/[&<>"]/g, function (m) {
      if (m === "&") return "&amp;";
      if (m === "<") return "&lt;";
      if (m === ">") return "&gt;";
      return "&quot;";
    });
  }

  function addLog(html) {
    const line = document.createElement("div");
    line.className = "log-line";
    line.innerHTML = `<strong>${new Date().toLocaleTimeString()}</strong> · ${html}`;
    logEl.prepend(line);
  }

  function formCreateUrl() {
    return "/warm-flow-ui/index.html?type=formCreate";
  }

  function flowChartUrl(instanceId) {
    return `/warm-flow-ui/index.html?type=FlowChart&id=${encodeURIComponent(instanceId)}`;
  }

  function withFrameRefresh(url) {
    frameRefreshSeq += 1;
    const separator = url.indexOf("?") >= 0 ? "&" : "?";
    return `${url}${separator}_refresh=${frameRefreshSeq}`;
  }

  function selectedDefinition() {
    return definitions.find(item => item.id === definitionSelectEl.value) || null;
  }

  function updateMetrics() {
    const currentDef = selectedDefinition();
    metricDefsEl.textContent = String(definitions.length);
    metricVersionEl.textContent = currentDef ? `v${currentDef.version || "1"}` : "--";
    metricStatusEl.textContent = currentRun ? (currentRun.ended ? "已结束" : (currentRun.flowStatus || "RUNNING")) : "未发起";
    chartNameEl.textContent = currentDef ? currentDef.flowName : "尚未选择流程";
  }

  function updateRuntimeState(run) {
    currentRun = run;
    instanceIdEl.textContent = run ? run.instanceId : "未发起";
    taskIdEl.textContent = run ? (run.taskId || "已结束") : "未发起";
    chartMetaEl.textContent = run
      ? `instanceId=${run.instanceId} · taskId=${run.taskId || "END"} · current=${run.currentNodeCode || "END"}`
      : "尚未发起演练实例";
    flowFrameUrlEl.textContent = run
      ? flowChartUrl(run.instanceId)
      : "/warm-flow-ui/index.html?type=FlowChart&id=...";
    if (!run) {
      currentFormView = {
        type: "0",
        taskId: "",
        title: "表单 iframe"
      };
      formFrameTitleEl.textContent = currentFormView.title;
      formFrameUrlEl.textContent = "/warm-flow-ui/index.html?type=formCreate";
    }
    updateMetrics();
  }

  function updateFormView(view) {
    currentFormView = view;
    formFrameTitleEl.textContent = view.title;
    formFrameUrlEl.textContent = `${formCreateUrl()} + postMessage(taskId=${view.taskId || ""}, type=${view.type})`;
  }

  function useCurrentTaskFormView() {
    // 结束态必须主动清空旧 taskId，避免 formCreate 再按待办模式加载已归档任务。
    updateFormView(resolveCurrentTaskFormView(currentRun, currentFormView));
  }

  function formatDateTime(value) {
    if (!value) {
      return "--";
    }
    return new Date(value).toLocaleString();
  }

  function renderHistory() {
    if (!currentRun || !currentRun.instanceId) {
      historyMetaEl.textContent = "发起实例后可查看已填写历史表单";
      historyListEl.innerHTML = '<div class="history-empty">当前还没有历史步骤。</div>';
      return;
    }
    historyMetaEl.textContent = historyItems.length > 0
      ? `共 ${historyItems.length} 条历史步骤，可点击回看表单`
      : "当前实例还没有产生历史步骤";
    if (historyItems.length === 0) {
      historyListEl.innerHTML = '<div class="history-empty">当前实例还没有历史步骤。</div>';
      return;
    }
    historyListEl.innerHTML = historyItems.map(item => {
      const isActive = currentFormView.type === "1" && currentFormView.taskId === item.hisTaskId;
      return `
        <button class="history-item${isActive ? " active" : ""}" type="button" data-his-task-id="${escHtml(item.hisTaskId)}">
          <strong>${escHtml(item.nodeName || item.nodeCode || "历史步骤")}</strong>
          <span>${escHtml(item.skipType || "PASS")} · ${escHtml(formatDateTime(item.createTime))}</span>
          <small>${escHtml(item.message || "无流转意见")}</small>
        </button>
      `;
    }).join("");
    historyListEl.querySelectorAll(".history-item").forEach(button => {
      button.addEventListener("click", () => {
        const historyItem = historyItems.find(item => item.hisTaskId === button.dataset.hisTaskId);
        if (!historyItem) {
          return;
        }
        updateFormView({
          type: "1",
          taskId: historyItem.hisTaskId,
          title: `历史表单 · ${historyItem.nodeName || historyItem.nodeCode || historyItem.hisTaskId}`
        });
        refreshFormFrameOnly();
        renderHistory();
        addLog(`打开历史表单：hisTaskId=<strong>${escHtml(historyItem.hisTaskId)}</strong>，节点=<strong>${escHtml(historyItem.nodeName || historyItem.nodeCode || "")}</strong>。`);
      });
    });
  }

  async function loadHistory() {
    if (!currentRun || !currentRun.instanceId) {
      historyItems = [];
      renderHistory();
      return;
    }
    const body = await parseApiResponse(await fetch(`/demo/god-workbench/history/${encodeURIComponent(currentRun.instanceId)}`));
    historyItems = Array.isArray(body.data) ? body.data : [];
    renderHistory();
  }

  function renderDefinitions() {
    definitionSelectEl.innerHTML = definitions.map(def => {
      return `<option value="${escHtml(def.id)}">${escHtml(def.flowName)} ${escHtml(def.flowCode)} v${escHtml(def.version || "1")}</option>`;
    }).join("");

    defListEl.innerHTML = definitions.map(def => {
      const active = definitionSelectEl.value === def.id ? " active" : "";
      return `
        <article class="def-card${active}" data-id="${escHtml(def.id)}">
          <strong>${escHtml(def.flowName)}<span class="tag green">已发布</span></strong>
          <small>${escHtml(def.flowCode)} v${escHtml(def.version || "1")}</small>
        </article>
      `;
    }).join("");

    defListEl.querySelectorAll(".def-card").forEach(card => {
      card.addEventListener("click", () => {
        definitionSelectEl.value = card.dataset.id;
        renderDefinitions();
        updateMetrics();
      });
    });

    updateMetrics();
  }

  async function parseApiResponse(resp) {
    const body = await resp.json();
    if (!resp.ok || body.code !== 200) {
      throw new Error(body.msg || "请求失败");
    }
    return body;
  }

  async function loadDefinitions() {
    const body = await parseApiResponse(await fetch("/demo/definitions/published"));
    definitions = body.data && body.data.list ? body.data.list : [];
    if (definitions.length > 0) {
      definitionSelectEl.value = definitions[0].id;
    }
    renderDefinitions();
    addLog(`已加载 <strong>${definitions.length}</strong> 个已发布流程。`);
  }

  async function startRun() {
    const currentDef = selectedDefinition();
    if (!currentDef) {
      addLog("没有可发起的流程定义。");
      return;
    }

    const body = await parseApiResponse(await fetch(`/demo/definitions/start/${encodeURIComponent(currentDef.id)}`, {
      method: "POST"
    }));

    updateRuntimeState({
      instanceId: body.data.instanceId,
      taskId: body.data.taskId,
      currentNodeCode: "待刷新",
      flowStatus: "RUNNING",
      ended: false,
      // 首个任务是申请人继续填写/提交业务表单，不应该展示审批意见。
      formType: "3"
    });
    useCurrentTaskFormView();
    refreshFrames();
    await loadHistory();
    addLog(`发起真实演练实例：instanceId=<strong>${escHtml(body.data.instanceId)}</strong>，taskId=<strong>${escHtml(body.data.taskId)}</strong>。`);
  }

  function previewDefinitionNotice() {
    const currentDef = selectedDefinition();
    if (!currentDef) {
      addLog("当前没有可说明的流程定义。");
      return;
    }
    addLog(`只看说明：当前选中 definition=<strong>${escHtml(currentDef.flowName)}</strong>（${escHtml(currentDef.flowCode)} v${escHtml(currentDef.version || "1")}），本页不使用 defId 直接打开运行态 FlowChart。`);
  }

  function refreshFrames() {
    if (!currentRun) {
      flowFrame.src = "about:blank";
      formFrame.src = "about:blank";
      return;
    }
    flowFrame.src = withFrameRefresh(flowChartUrl(currentRun.instanceId));
    refreshFormFrameOnly();
  }

  function refreshFormFrameOnly() {
    formFrameReadyState = {
      taskId: currentFormView.taskId || "",
      type: currentFormView.type || "",
      ready: false
    };
    formFrame.src = currentFormView.taskId ? withFrameRefresh(formCreateUrl()) : "about:blank";
  }

  function resolvePendingFormReadyWaiters(error) {
    const waiters = pendingFormReadyWaiters;
    pendingFormReadyWaiters = [];
    waiters.forEach(waiter => {
      window.clearTimeout(waiter.timer);
      if (error) {
        waiter.reject(error);
        return;
      }
      waiter.resolve();
    });
  }

  function markFormFrameReady(payload) {
    formFrameReadyState = {
      taskId: payload.taskId || "",
      type: payload.type || "",
      ready: true
    };
    resolvePendingFormReadyWaiters();
  }

  function waitForFormFrameReady() {
    if (
      formFrameReadyState.ready &&
      formFrameReadyState.taskId === (currentFormView.taskId || "") &&
      formFrameReadyState.type === (currentFormView.type || "")
    ) {
      return Promise.resolve();
    }
    return new Promise((resolve, reject) => {
      const timer = window.setTimeout(() => {
        pendingFormReadyWaiters = pendingFormReadyWaiters.filter(item => item.timer !== timer);
        reject(new Error("当前待办表单加载超时"));
      }, 5000);
      pendingFormReadyWaiters.push({ resolve, reject, timer });
    });
  }

  async function ensureCurrentTaskFormReady() {
    if (!currentRun || !currentRun.taskId) {
      throw new Error("当前没有可提交的待办任务");
    }
    const expectedType = currentRun.formType || "0";
    const switched =
      currentFormView.taskId !== currentRun.taskId ||
      currentFormView.type !== expectedType;
    if (switched) {
      useCurrentTaskFormView();
      refreshFormFrameOnly();
      renderHistory();
      addLog(`提交前切回当前待办表单：taskId=<strong>${escHtml(currentRun.taskId)}</strong>。`);
    }
    await waitForFormFrameReady();
  }

  function sendFormInit() {
    if (!currentRun || !currentFormView.taskId || !formFrame.contentWindow) {
      return;
    }
    // 父页面显式开启 allowSnapshot，避免普通业务页默认暴露表单快照。
    formFrame.contentWindow.postMessage({
      method: "formInit",
      data: {
        taskId: currentFormView.taskId,
        type: currentFormView.type,
        disabled: false,
        allowSnapshot: currentFormView.type !== "1"
      }
    }, "*");
  }

  function requestFormSnapshot() {
    return new Promise((resolve, reject) => {
      if (!currentRun || !currentRun.taskId || !formFrame.contentWindow) {
        reject(new Error("当前没有可采集的待办表单"));
        return;
      }

      if (pendingSnapshot) {
        pendingSnapshot.reject(new Error("上一次表单采集尚未结束"));
      }

      const timer = window.setTimeout(() => {
        if (pendingSnapshot) {
          pendingSnapshot = null;
          reject(new Error("采集表单快照超时"));
        }
      }, 5000);

      pendingSnapshot = {
        resolve,
        reject,
        timer
      };
      addLog(`开始采集表单快照：taskId=<strong>${escHtml(currentRun.taskId)}</strong>。`);
      formFrame.contentWindow.postMessage({ method: "collectFormData" }, "*");
    });
  }

  async function submitRun() {
    if (!currentRun || !currentRun.taskId) {
      addLog("请先发起演练实例，再提交上帝模式流转。");
      return;
    }

    submitBtnEl.disabled = true;
    try {
      await ensureCurrentTaskFormReady();
      const previousTaskId = currentRun.taskId;
      const snapshot = await requestFormSnapshot();
      addLog(`已收到表单快照：字段数=<strong>${Object.keys(snapshot.formData || {}).length}</strong>。`);

      const body = await parseApiResponse(await fetch("/demo/god-workbench/submit", {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          taskId: currentRun.taskId,
          message: messageEl.value.trim(),
          formData: snapshot.formData || {}
        })
      }));

      updateRuntimeState({
        instanceId: body.data.instanceId,
        taskId: body.data.taskId || "",
        currentNodeCode: body.data.currentNodeCode || "",
        flowStatus: body.data.flowStatus || "",
        ended: !!body.data.ended,
        // 一旦提交过申请表单，后续待办统一按审批态打开；结束态则不再打开表单。
        formType: body.data.ended ? "" : "0"
      });
      useCurrentTaskFormView();
      refreshFrames();
      await loadHistory();
      addLog(`正常流转完成：旧 taskId=<strong>${escHtml(previousTaskId)}</strong>，新 taskId=<strong>${escHtml(body.data.taskId || "END")}</strong>。`);
    } finally {
      submitBtnEl.disabled = false;
    }
  }

  window.addEventListener("message", event => {
    if (!event.data || !event.data.method) {
      return;
    }

    if (event.data.method === "formInit") {
      sendFormInit();
      return;
    }

    if (event.data.method === "getOffsetHeight") {
      const height = Number(event.data.offsetHeight) || 0;
      formFrame.style.height = `${Math.max(height + 24, 360)}px`;
      return;
    }

    if (event.data.method === "formReady") {
      markFormFrameReady(event.data.data || {});
      return;
    }

    if (event.data.method === "formDataSnapshot" && pendingSnapshot) {
      const snapshot = pendingSnapshot;
      pendingSnapshot = null;
      window.clearTimeout(snapshot.timer);
      addLog(`子页面返回 formDataSnapshot：taskId=<strong>${escHtml((event.data.data || {}).taskId || "")}</strong>。`);
      snapshot.resolve(event.data.data || {});
      return;
    }

    if (event.data.method === "formDataSnapshotError" && pendingSnapshot) {
      const snapshot = pendingSnapshot;
      pendingSnapshot = null;
      window.clearTimeout(snapshot.timer);
      const payload = event.data.data || {};
      addLog(`子页面拒绝导出快照：taskId=<strong>${escHtml(payload.taskId || "")}</strong>，原因：${escHtml(payload.message || "表单校验未通过")}。`);
      snapshot.reject(new Error(payload.message || "表单校验未通过"));
    }
  });

  definitionSelectEl.addEventListener("change", () => {
    renderDefinitions();
    updateMetrics();
  });

  document.getElementById("startBtn").addEventListener("click", () => {
    startRun().catch(error => addLog(`发起演练失败：${escHtml(error.message)}`));
  });
  document.getElementById("previewBtn").addEventListener("click", previewDefinitionNotice);
  document.getElementById("submitBtn").addEventListener("click", () => {
    submitRun().catch(error => addLog(`提交演练失败：${escHtml(error.message)}`));
  });
  document.getElementById("viewBtn").addEventListener("click", () => {
    if (!currentRun) {
      addLog("请先发起演练实例，再刷新两个 iframe。");
      return;
    }
    useCurrentTaskFormView();
    refreshFrames();
    renderHistory();
    addLog(`父页面刷新两个 iframe：FlowChart 使用 instanceId=<strong>${escHtml(currentRun.instanceId)}</strong>，formCreate 使用 taskId=<strong>${escHtml(currentRun.taskId || "END")}</strong>。`);
  });

  updateRuntimeState(null);
  refreshFrames();
  renderHistory();
  loadDefinitions().catch(error => addLog(`初始化失败：${escHtml(error.message)}`));
  addLog("上帝模式工作台已就绪。");
}());
