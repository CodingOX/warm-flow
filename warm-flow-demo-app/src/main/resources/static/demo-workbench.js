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

  function buildInitPayload() {
    return {
      taskId: taskIdEl.value.trim(),
      formId: formIdEl.value.trim(),
      type: modeEl.value,
      disabled: modeEl.value !== '0'
    };
  }

  function sendFormInit() {
    const payload = buildInitPayload();
    iframe.contentWindow.postMessage({
      method: 'formInit',
      data: payload
    }, '*');
    log(`发送初始化: actor=${actorEl.value}, payload=${JSON.stringify(payload)}`);
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

  log(`工作台已就绪，视角=${actorEl.value}`);
}());
