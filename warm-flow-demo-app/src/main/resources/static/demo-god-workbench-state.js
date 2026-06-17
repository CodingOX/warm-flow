"use strict";

/**
 * 根据当前运行态推导“当前待办表单视图”。
 * 流程结束后必须清空旧 taskId，避免 iframe 继续按“当前待办”模式加载已转历史的任务。
 */
function resolveCurrentTaskFormView(currentRun, previousView) {
  if (!currentRun) {
    return {
      type: "0",
      taskId: "",
      title: "表单 iframe"
    };
  }
  if (currentRun.ended || !currentRun.taskId) {
    return {
      type: "0",
      taskId: "",
      title: "表单 iframe"
    };
  }
  return {
    type: currentRun.formType || "0",
    taskId: currentRun.taskId,
    title: "表单 iframe"
  };
}

if (typeof window !== "undefined") {
  window.DemoGodWorkbenchState = {
    resolveCurrentTaskFormView
  };
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    resolveCurrentTaskFormView
  };
}
