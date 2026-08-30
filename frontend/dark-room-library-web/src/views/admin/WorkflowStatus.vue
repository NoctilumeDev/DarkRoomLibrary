<template>
  <section class="workflow-page">
    <header class="page-head">
      <div>
        <p>流程状态</p>
        <h1>审核状态与后台流程</h1>
      </div>
      <el-button size="large" class="refresh-button" :loading="loading" @click="loadAll">
        刷新
      </el-button>
    </header>

    <div class="status-grid">
      <article
        v-for="card in cards"
        :key="card.label"
        class="status-card"
        :class="card.type"
      >
        <span>{{ card.module }}</span>
        <strong>{{ card.value }}</strong>
        <p>{{ card.label }}</p>
      </article>
    </div>

    <el-tabs v-model="activeTab" class="paper-tabs">
      <el-tab-pane label="审核状态" name="audit">
        <div class="group-grid">
          <section v-for="group in groups" :key="group.title" class="paper-panel">
            <div class="panel-head">
              <h2>{{ group.title }}</h2>
              <p>{{ group.description }}</p>
            </div>
            <div class="item-list">
              <div v-for="item in group.items" :key="item.label" class="audit-item">
                <span>{{ item.label }}</span>
                <el-tag :type="tagType(item.type)" effect="light">
                  {{ item.value }}
                </el-tag>
              </div>
            </div>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="后台流程" name="flow">
        <div class="flow-list">
          <article v-for="(stage, index) in stages" :key="stage.title" class="flow-stage">
            <div class="stage-index">{{ index + 1 }}</div>
            <div class="stage-body">
              <div class="stage-title">
                <h2>{{ stage.title }}</h2>
                <span>{{ stage.layer }}</span>
              </div>
              <p>{{ stage.description }}</p>
              <div class="point-list">
                <el-tag v-for="point in stage.points" :key="point" effect="plain">
                  {{ point }}
                </el-tag>
              </div>
            </div>
          </article>
        </div>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script>
export default {
  name: "WorkflowStatus",
  data() {
    return {
      activeTab: "audit",
      loading: false,
      cards: [],
      groups: [],
      stages: [],
    };
  },
  created() {
    this.loadAll();
  },
  methods: {
    async loadAll() {
      this.loading = true;
      try {
        const [auditResponse, flowResponse] = await Promise.all([
          this.$axios.get("/adminWorkflow/auditStatus"),
          this.$axios.get("/adminWorkflow/backendFlow"),
        ]);

        if (auditResponse.data.code === 200) {
          const audit = auditResponse.data.data || {};
          this.cards = audit.cards || [];
          this.groups = audit.groups || [];
        } else {
          this.$message.error(auditResponse.data.msg || "审核状态加载失败。");
        }

        if (flowResponse.data.code === 200) {
          const flow = flowResponse.data.data || {};
          this.stages = flow.stages || [];
        } else {
          this.$message.error(flowResponse.data.msg || "后台流程加载失败。");
        }
      } catch (error) {
        console.error("流程状态加载失败:", error);
        this.$message.error("流程状态加载失败，请稍后重试。");
      } finally {
        this.loading = false;
      }
    },
    tagType(type) {
      const map = {
        primary: "",
        success: "success",
        warning: "warning",
        danger: "danger",
        info: "info",
      };
      return map[type] || "info";
    },
  },
};
</script>

<style scoped lang="scss">
.workflow-page {
  display: grid;
  gap: 18px;
}

.page-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: end;

  p {
    margin: 0;
    color: var(--admin-gold);
    font-size: 12px;
    font-weight: 700;
  }

  h1 {
    margin: 5px 0 0;
    color: var(--admin-text);
    font-size: 32px;
  }
}

.refresh-button {
  border-color: var(--admin-border-strong);
  color: var(--admin-text-secondary);
  background: var(--admin-surface);
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(150px, 1fr));
  gap: 14px;
}

.status-card,
.paper-panel,
.flow-stage {
  border: 1px solid var(--admin-border);
  border-radius: 6px;
  background: var(--admin-surface);
  box-shadow: none;
}

.status-card {
  padding: 18px;
  border-left: 5px solid var(--admin-muted);

  span {
    color: var(--admin-muted);
    font-size: 13px;
  }

  strong {
    display: block;
    margin-top: 8px;
    color: var(--admin-text);
    font-size: 32px;
  }

  p {
    margin: 6px 0 0;
    color: var(--admin-text-secondary);
  }

  &.warning {
    border-left-color: var(--admin-gold);
  }

  &.danger {
    border-left-color: var(--admin-danger);
  }

  &.success {
    border-left-color: var(--admin-jade);
  }

  &.primary {
    border-left-color: var(--admin-blue);
  }
}

.paper-tabs {
  padding: 16px;
  border: 1px solid var(--admin-border);
  border-radius: 6px;
  background: var(--admin-surface);
}

.group-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  gap: 14px;
}

.paper-panel {
  padding: 16px;
}

.panel-head {
  margin-bottom: 12px;

  h2 {
    margin: 0;
    color: var(--admin-text);
    font-size: 18px;
  }

  p {
    margin: 6px 0 0;
    color: var(--admin-muted);
    line-height: 1.6;
  }
}

.item-list {
  display: grid;
  gap: 8px;
}

.audit-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: 5px;
  color: var(--admin-text-secondary);
  background: var(--admin-surface-muted);
}

.flow-list {
  display: grid;
  gap: 12px;
}

.flow-stage {
  display: grid;
  grid-template-columns: 44px 1fr;
  gap: 12px;
  padding: 16px;
}

.stage-index {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #fffaf1;
  background: var(--admin-accent-solid);
  font-weight: 700;
}

.stage-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;

  h2 {
    margin: 0;
    color: var(--admin-text);
    font-size: 18px;
  }

  span {
    color: var(--admin-muted);
    font-size: 13px;
  }
}

.stage-body p {
  margin: 8px 0 12px;
  color: var(--admin-text-secondary);
  line-height: 1.7;
}

.point-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 1100px) {
  .status-grid,
  .group-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .page-head {
    align-items: start;
    flex-direction: column;
  }

  .status-grid,
  .group-grid {
    grid-template-columns: 1fr;
  }

  .flow-stage {
    grid-template-columns: 1fr;
  }
}
</style>
