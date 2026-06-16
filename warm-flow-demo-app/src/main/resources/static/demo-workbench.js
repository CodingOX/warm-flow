(function () {
  const iframe = document.getElementById('flowFrame');
  const openFrameButton = document.getElementById('openFrame');
  const logPanel = document.getElementById('log');
  const modeEl = document.getElementById('mode');
  const taskIdEl = document.getElementById('taskId');
  const formIdEl = document.getElementById('formId');
  const actorEl = document.getElementById('actor');

  function log(message) {
    const line = `[${new Date().toLocaleTimeString()}] ${message}`;
    logPanel.textContent = `${line}\n${logPanel.textContent}`.trim();
  }

  function formCreateUrl() {
    return '/warm-flow-ui/index.html?type=formCreate';
  }

  function resolveMode() {
    // 申请人视角属于业务发起/查看语义，不应复用审批人待办办理模式。
    if (actorEl.value === 'applicant' && modeEl.value === '0') {
      return '3';
    }
    return modeEl.value;
  }

  function buildInitPayload() {
    const resolvedMode = resolveMode();
    return {
      taskId: taskIdEl.value.trim(),
      formId: formIdEl.value.trim(),
      type: resolvedMode,
      disabled: resolvedMode !== '0'
    };
  }

  function sendFormInit() {
    const payload = buildInitPayload();
    iframe.contentWindow.postMessage({
      method: 'formInit',
      data: payload
    }, '*');
    log(`发送初始化: actor=${actorEl.value}, mode=${modeEl.value}, payload=${JSON.stringify(payload)}`);
  }

  openFrameButton.addEventListener('click', () => {
    iframe.src = formCreateUrl();
    log(`打开 iframe: ${iframe.src}`);
  });

  window.addEventListener('message', (event) => {
    log(`收到消息: ${JSON.stringify(event.data)}`);
    if (!event.data || !event.data.method) {
      return;
    }
    if (event.data.method === 'formInit') {
      sendFormInit();
    }
    if (event.data.method === 'getOffsetHeight') {
      const height = Number(event.data.offsetHeight) || 0;
      iframe.style.height = `${Math.max(height + 48, 720)}px`;
    }
  });

  // ========== 已发布流程列表与发起 ==========

  const defListEl = document.getElementById('defList');

  /** 渲染一条流程定义卡片 */
  function renderDefItem(def) {
    const li = document.createElement('li');
    li.className = 'def-item';
    li.innerHTML = `
      <div class="def-info">
        <span class="def-name">${escHtml(def.flowName)}</span>
        <span class="def-meta">${escHtml(def.flowCode)} v${escHtml(def.version || '1')}</span>
      </div>
      <button class="def-start-btn" data-def-id="${def.id}">发起</button>
    `;
    li.querySelector('.def-start-btn').addEventListener('click', async () => {
      await startFlow(def.id);
    });
    return li;
  }

  function escHtml(s) {
    if (s == null) return '';
    return String(s).replace(/[&<>"]/g, function (m) {
      if (m === '&') return '&amp;';
      if (m === '<') return '&lt;';
      if (m === '>') return '&gt;';
      if (m === '"') return '&quot;';
      return m;
    });
  }

  /** 拉取已发布流程定义列表 */
  async function loadPublishedDefs() {
    try {
      const resp = await fetch('/demo/definitions/published');
      const body = await resp.json();
      if (body.code !== 200) {
        log(`加载流程列表失败: ${body.msg}`);
        return;
      }
      const list = body.data.list || [];
      defListEl.innerHTML = '';
      for (const def of list) {
        defListEl.appendChild(renderDefItem(def));
      }
      if (list.length === 0) {
        defListEl.innerHTML = '<li class="def-empty">暂无已发布流程</li>';
      }
      log(`已加载 ${list.length} 个已发布流程`);
    } catch (e) {
      log(`加载流程列表出错: ${e.message}`);
    }
  }

  /** 发起一个流程定义 */
  async function startFlow(defId) {
    try {
      openFrameButton.disabled = true;
      const resp = await fetch(`/demo/definitions/start/${defId}`, { method: 'POST' });
      const body = await resp.json();
      if (body.code !== 200) {
        log(`发起流程失败: ${body.msg}`);
        return;
      }
      const { taskId, instanceId } = body.data;
      log(`流程已发起: instanceId=${instanceId}, taskId=${taskId}`);

      // 自动填充 taskId，申请人默认以“申请人查看”模式打开，避免看到审批区。
      taskIdEl.value = taskId;
      modeEl.value = actorEl.value === 'applicant' ? '3' : '0';
      iframe.src = formCreateUrl();
      log(`自动打开表单: taskId=${taskId}, resolvedMode=${resolveMode()}`);
    } catch (e) {
      log(`发起流程出错: ${e.message}`);
    } finally {
      openFrameButton.disabled = false;
    }
  }

  // 页面加载后自动拉取流程列表
  loadPublishedDefs();

  log(`工作台已就绪，视角=${actorEl.value}`);
}());
