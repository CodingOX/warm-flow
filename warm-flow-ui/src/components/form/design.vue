<template>
  <div class="container" ref="container">
    <fc-designer class="fcDesigner" ref="designer" :config="config" @save="handleSave"/>
  </div>
</template>

<script setup name="formDesign">
import {getFormContent, saveFormContent} from '@/api/form/form';
const { proxy } = getCurrentInstance();
import useAppStore from "@/store/app";
const appStore = useAppStore();
const appParams = computed(() => appStore.appParams);

const definitionId = ref("");
const data = reactive({
  config: {
    //是否显示保存按钮
    showSaveBtn: true,
    //字段ID是否可编辑
    fieldReadonly: false
  }
});

const { config } = toRefs(data);

function handleSave() {
  //获取表单的生成规则
  const ruleJson = proxy.$refs.designer.getJson();
  //获取表单的配置
  const optionsJson =  JSON.stringify(proxy.$refs.designer.getOption());
  let data = {
    formContent: JSON.stringify({
      rule: formCreate.parseJson(ruleJson),
      option: formCreate.parseJson(optionsJson)
    }),
    id: definitionId.value
  };
  saveFormContent(data).then(response => {
    if (response.code === 200) {
      const savedId = response.data?.id;
      if (savedId) {
        definitionId.value = String(savedId);
        syncFormId(savedId);
      }
      proxy.$modal.msgSuccess("保存成功");
      // const obj = { path: "/form/formDefinition", query: { t: Date.now(), pageNum: proxy.$route.query.pageNum } };
      // proxy.$tab.closeOpenPage(obj);
      window.parent.postMessage({ method: "close" }, "*");
    }
  });
}

// 获取详情
function getInfo() {
  definitionId.value = appParams.value?.id;
  if (!definitionId.value) {
    return;
  }
  getFormContent(definitionId.value).then(res => {
    let formContent = res.data;
    if (formContent) {
      nextTick(() => {
        formContent = JSON.parse(formContent);
        if (formContent.rule) proxy.$refs.designer.setRule(formContent.rule);
        if (formContent.option) proxy.$refs.designer.setOption(formContent.option);
      });
    }
  });
}

function syncFormId(savedId) {
  const nextParams = new URLSearchParams(window.location.search);
  nextParams.set('id', savedId);
  window.history.replaceState({}, '', `${window.location.pathname}?${nextParams.toString()}`);
  appStore.appParams = {
    ...(appParams.value || {}),
    id: String(savedId)
  };
}
getInfo();

</script>

<style scoped lang="scss">
:deep(.container) {
  width: 100%;
  .fcDesigner {
    height: 100vh;
    .el-aside {
      padding: 0;
      background: #ffffff;
      height: 100%;
    }
  }
}
</style>
