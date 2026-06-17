<template>
  <el-form ref="form" :model="form" :rules="rules" :disabled="disabled">
    <form-create
      class="formCreate"
      v-model="formData"
      v-model:api="fApi"
      :rule="rule"
      :option="option"
      :disabled="disabled"
    ></form-create>
    <el-divider v-if="showApprovalFields"></el-divider>
    <el-form-item label="审批意见" prop="message" v-if="showApprovalFields">
      <el-input v-model="message" type="textarea" placeholder="请输入审批意见" :autosize="{ minRows: 4, maxRows: 4 }" />
    </el-form-item>
    <div style="text-align: right;" v-if="showApprovalFields">
      <el-button type="primary" @click="handleBtn('PASS')">审批通过</el-button>
      <el-button @click="handleBtn('REJECT')">退回</el-button>
    </div>
    <div style="text-align: right;" v-else-if="showApplicantSubmit">
      <el-button type="primary" @click="handleBtn('PASS')">提交表单</el-button>
    </div>
  </el-form>
</template>

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
  // 表单设计内容
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
  window.removeEventListener('message', handleMessage);
});

function handleMessage(event) {
  if (!event.data || !event.data.method) {
    return;
  }
  switch (event.data.method) {
    case "formInit":
      formInit(event.data.data);
      break;
    case "reset":
      reset(); // 表单重置
      break;
    case "collectFormData":
      if (allowSnapshot.value) {
        collectFormDataSnapshot();
      }
      break;
  }
}
// 表单重置
function reset() {
  formData.value = {};
  message.value = "";
};

function emitFormDataSnapshot() {
  const snapshot = JSON.parse(JSON.stringify(formData.value || {}));
  // 只有父页面显式开启 allowSnapshot，才允许导出当前表单数据。
  window.parent.postMessage({
    method: "formDataSnapshot",
    data: {
      taskId: taskId.value,
      formData: snapshot
    }
  }, "*");
}

function emitFormDataSnapshotError(messageText) {
  window.parent.postMessage({
    method: "formDataSnapshotError",
    data: {
      taskId: taskId.value,
      message: messageText
    }
  }, "*");
}

function collectFormDataSnapshot() {
  if (!fApi.value || !proxy.$refs["form"]) {
    emitFormDataSnapshotError("表单尚未初始化完成");
    return;
  }
  // 与原生提交保持同一套校验口径：先触发表单组件校验，再导出快照。
  fApi.value.submit(() => {
    proxy.$refs["form"].validate(valid => {
      if (!valid) {
        emitFormDataSnapshotError("请先完成表单必填项");
        return;
      }
      emitFormDataSnapshot();
    });
  });
}

/** 审核通过按钮 */
function handleBtn(skipType) {
  fApi.value.submit(() => {
    proxy.$refs["form"].validate(valid => {
      if (valid) {
        executeHandle(formData.value, taskId.value, skipType, message.value).then(response => {
          window.parent.postMessage({ method: "submitSuccess" }, "*");
        });
      }
    });
  });
}
// 设计表单反显
async function formInit(data) {
  let response;
  let formContent;
  taskId.value = data.taskId;
  allowSnapshot.value = !!data.allowSnapshot;
  // type 来源：
  // 0 = 待办办理（审批人）
  // 1 = 已办历史
  // 2 = 已发布表单预览
  // 3 = 申请人办理（沿用任务数据加载，可编辑业务表单，但不显示审批意见和审批动作）
  if (data.type === "0") {
    showApprovalFields.value = true;
    showApplicantSubmit.value = false;
    reset();
    response = await executeLoad(data.taskId);
    if (!response.data) proxy.$modal.alertWarning("待办任务不存在");
    formContent = JSON.parse(response.data.form?.formContent);
  } else if (data.type === "3") {
    showApprovalFields.value = false;
    showApplicantSubmit.value = true;
    reset();
    response = await executeLoad(data.taskId);
    if (!response.data) proxy.$modal.alertWarning("待办任务不存在");
    formContent = JSON.parse(response.data.form?.formContent);
  } else if (data.type === "1") {
    showApprovalFields.value = false;
    showApplicantSubmit.value = false;
    response = await hisLoad(data.taskId);
    if (!response.data) proxy.$modal.alertWarning("历史记录不存在");
    formContent = JSON.parse(response.data.form?.formContent);
  } else {
    showApprovalFields.value = false;
    showApplicantSubmit.value = false;
    response = await getFormContent(data.formId);
    formContent = JSON.parse(response.data);
  }

  disabled.value = data.disabled;
  rule.value = formContent.rule;
  option.value = formContent.option;
  if (option.value) option.value.submitBtn = false;
  formData.value = response.data.data; // 表单内容
  proxy.$nextTick(() => {
    window.parent.postMessage({
      method: "formReady",
      data: {
        taskId: taskId.value,
        type: data.type
      }
    }, "*");
    window.parent.postMessage({ method: "getOffsetHeight", offsetHeight: proxy.$refs.form.$el.offsetHeight }, "*");
  });
};
</script>
