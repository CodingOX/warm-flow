<template>
  <div v-if="showEntryPage" class="entry-page">
    <div class="entry-shell">
      <div class="entry-copy">
        <span class="entry-kicker">Warm-Flow UI</span>
        <h1>选择你要进入的页面</h1>
        <p>
          默认地址不再直接跳进流程设计器，而是先给出入口说明。
          如果你要创建动态表单，请先进入“表单设计器”。
        </p>
      </div>

      <div class="entry-grid">
        <a class="entry-card" href="./index.html?type=form">
          <span class="entry-card-tag">推荐</span>
          <strong>表单设计器</strong>
          <p>创建和发布动态表单，用于发起、主管、经理等节点绑定。</p>
          <code>/warm-flow-ui/index.html?type=form</code>
        </a>

        <a class="entry-card" href="./index.html?type=FlowChart">
          <span class="entry-card-tag">查看</span>
          <strong>流程图</strong>
          <p>查看流程图展示页面，适合只读预览。</p>
          <code>/warm-flow-ui/index.html?type=FlowChart</code>
        </a>

        <a class="entry-card" href="./index.html?type=design">
          <span class="entry-card-tag">配置</span>
          <strong>流程设计器</strong>
          <p>创建流程定义、节点、分支条件，并绑定已发布动态表单。</p>
          <code>/warm-flow-ui/index.html?type=design</code>
        </a>
      </div>

      <div class="entry-tips">
        <div class="entry-tip">
          <strong>动态表单创建顺序</strong>
          <p>先建发起表单，再建主管表单、经理表单，最后回到流程设计器逐个绑定。</p>
        </div>
        <div class="entry-tip">
          <strong>分支条件写法</strong>
          <p>绑定动态表单后，条件表达式统一使用 <code>formData.字段Key</code>。</p>
        </div>
      </div>
    </div>
  </div>

  <component v-else-if="component" v-bind:is="component"></component>
</template>

<script setup>
import Design from './views/flow-design/index.vue';
import FlowChart from './views/flow-design/flowChart.vue';
import Form from './views/form-design/index.vue';
import FormCreate from './views/form-design/formCreate.vue';
import useAppStore from "@/store/app";

const appStore = useAppStore();
const appParams = computed(() => appStore.appParams);
const component = shallowRef(null);
const showEntryPage = ref(false);

onMounted(async () => {
  if (!appParams.value) await appStore.fetchTokenName();

  // 统一收口 UI 入口：无 type 时展示导航页，明确告诉用户去哪里创建动态表单。
  const type = appParams.value?.type;
  const pathObj = {
    form: Form,
    FlowChart: FlowChart,
    formCreate: FormCreate,
    design: Design
  };

  if (!type) {
    showEntryPage.value = true;
    return;
  }

  component.value = pathObj[type] || Design;
});
</script>

<style scoped lang="scss">
.entry-page {
  min-height: 100vh;
  padding: 48px 20px;
  background:
    radial-gradient(circle at top left, rgba(20, 184, 166, 0.14), transparent 30%),
    radial-gradient(circle at right center, rgba(245, 158, 11, 0.14), transparent 24%),
    linear-gradient(180deg, #f7f9fc 0%, #eef3f8 100%);
  color: #1f2937;
}

.entry-shell {
  max-width: 1120px;
  margin: 0 auto;
}

.entry-copy {
  max-width: 720px;
  margin-bottom: 28px;
}

.entry-kicker {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.entry-copy h1 {
  margin: 18px 0 12px;
  font-size: clamp(32px, 5vw, 52px);
  line-height: 1.05;
}

.entry-copy p {
  margin: 0;
  font-size: 17px;
  line-height: 1.7;
  color: #475569;
}

.entry-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 18px;
}

.entry-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 24px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 24px;
  text-decoration: none;
  color: inherit;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(12px);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.entry-card:hover {
  transform: translateY(-4px);
  border-color: rgba(13, 148, 136, 0.4);
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.12);
}

.entry-card-tag {
  display: inline-flex;
  align-self: flex-start;
  padding: 4px 10px;
  border-radius: 999px;
  background: #ecfeff;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.entry-card strong {
  font-size: 24px;
  line-height: 1.2;
}

.entry-card p {
  margin: 0;
  color: #475569;
  line-height: 1.7;
}

.entry-card code,
.entry-tip code {
  padding: 4px 8px;
  border-radius: 8px;
  background: #e2e8f0;
  color: #0f172a;
  font-size: 13px;
  word-break: break-all;
}

.entry-tips {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
  margin-top: 18px;
}

.entry-tip {
  padding: 20px 22px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.entry-tip strong {
  display: block;
  margin-bottom: 8px;
  font-size: 16px;
}

.entry-tip p {
  margin: 0;
  color: #475569;
  line-height: 1.7;
}

@media (max-width: 640px) {
  .entry-page {
    padding: 28px 14px;
  }

  .entry-card,
  .entry-tip {
    border-radius: 18px;
  }
}
</style>
