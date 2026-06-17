const test = require("node:test");
const assert = require("node:assert/strict");

const {
  resolveCurrentTaskFormView
} = require("../../main/resources/static/demo-god-workbench-state.js");

test("running task should open current todo form view", () => {
  assert.deepEqual(
    resolveCurrentTaskFormView({
      taskId: "188800020005",
      formType: "3",
      ended: false
    }),
    {
      taskId: "188800020005",
      type: "3",
      title: "表单 iframe"
    }
  );
});

test("ended task should clear current todo form view", () => {
  assert.deepEqual(
    resolveCurrentTaskFormView({
      taskId: "",
      formType: "",
      ended: true
    }, {
      taskId: "188800020004",
      type: "0",
      title: "表单 iframe"
    }),
    {
      taskId: "",
      type: "0",
      title: "表单 iframe"
    }
  );
});
