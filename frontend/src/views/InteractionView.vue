<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const analyzing = ref(false) // 控制 AI 生成/重新生成按钮的 loading
const activeTab = ref('pending')
const pendingList = ref([])
const historyList = ref([])
const loading = ref(false)

// 弹窗相关
const showModal = ref(false)
const isReadOnly = ref(false)
const currentItem = ref(null)
const handleForm = ref({ result: '' })

// ⭐ 将 AI 的分析和话术分开存储
const aiAnalysisText = ref("") // 给管理员看的分析
const aiReplyDraft = ref("")   // 给客户发的话术
const agentResult = ref(null)  // Agent 工作流结构化结果
const intentOverride = ref('')
const selectedCampaignIds = ref([])
const intentOptions = [
  { value: '', label: '使用自动识别' },
  { value: 'product_consulting', label: '商品咨询' },
  { value: 'complaint', label: '投诉' },
  { value: 'after_sales', label: '售后' },
  { value: 'promotion_inquiry', label: '活动咨询' },
  { value: 'general_service', label: '其他服务' }
]

onMounted(() => { fetchData() })

function switchTab(tab) {
  activeTab.value = tab
  fetchData()
}

async function fetchData() {
  loading.value = true
  try {
    const url = activeTab.value === 'pending' ? '/interaction/pending' : '/interaction/history'
    const res = await axios.get('/api' + url)
    if (activeTab.value === 'pending') pendingList.value = res.data
    else historyList.value = res.data
  } catch (e) { console.error(e) } finally { loading.value = false }
}

// 首次手动触发 AI
async function generateAiReply() {
  analyzing.value = true
  try {
    const res = await axios.post(`/api/interaction/analyze/${currentItem.value.id}`, {
      selectedCampaignIds: selectedCampaignIds.value,
      intentOverride: intentOverride.value
    })
    if (res.data && res.data.code === 404) {
      alert(res.data.msg || "记录不存在")
      return
    }
    if (res.data) {
      parseAgentResult(res.data)
      currentItem.value.aiSuggestedReply = aiReplyDraft.value
    }
  } catch (e) {
    alert("AI 服务繁忙，请重试")
  } finally {
    analyzing.value = false
  }
}

// ⭐ 新增：重新生成 AI 话术的函数
async function regenerateReply() {
  if (!currentItem.value || !currentItem.value.id) return;

  analyzing.value = true;
  try {
    const res = await axios.post(`/api/interaction/analyze/${currentItem.value.id}`, {
      selectedCampaignIds: selectedCampaignIds.value,
      intentOverride: intentOverride.value
    });
    if (!res.data.code || res.data.code === 200) {
      parseAgentResult(res.data.data || res.data);
      currentItem.value.aiSuggestedReply = aiReplyDraft.value;
    } else {
      alert(res.data.msg || "生成失败");
    }
  } catch (error) {
    console.error("重新生成报错:", error);
    alert("网络异常，重新生成失败");
  } finally {
    analyzing.value = false;
  }
}

function openHandleModal(item) {
  currentItem.value = item
  isReadOnly.value = false
  handleForm.value.result = ""

  aiAnalysisText.value = ""
  aiReplyDraft.value = ""
  agentResult.value = null
  intentOverride.value = item.intentOverride || ''
  selectedCampaignIds.value = []

  if (item.agentResult) {
    parseAgentResult(item.agentResult)
  }

  showModal.value = true
}

// 已报废：旧版 Java 直连 Qwen 返回“分析 ||| 话术”，当前 FastAPI Agent 返回结构化 JSON。
// function parseLegacyAiContent(text) {
//   const parts = text.split('|||')
//   if (parts.length >= 2) {
//     aiAnalysisText.value = parts[0].replace('【情绪与意图分析】', '').trim()
//     aiReplyDraft.value = parts[1].replace('【建议回复话术】', '').trim()
//   } else {
//     aiReplyDraft.value = text
//   }
// }

function parseAgentResult(result) {
  if (!result || typeof result === 'string') return

  agentResult.value = result
  selectedCampaignIds.value = (result.selected_campaigns || []).map(item => item.campaign_id)
  aiReplyDraft.value = result.reply_draft || result.replyDraft || ''
  const strategy = result.customer_strategy || {}
  aiAnalysisText.value = `识别意图：${result.intent || 'unknown'}；价值等级：${valueTierLabel(strategy.value_tier)}；生命周期风险：${lifecycleRiskLabel(strategy.lifecycle_risk)}`

  if (!aiReplyDraft.value && result.data) {
    parseAgentResult(result.data)
  }
}

function valueTierLabel(value) {
  return { high: '高价值客户', normal: '普通价值客户' }[value] || '待分析'
}

function lifecycleRiskLabel(value) {
  return { active: '活跃', silent: '沉睡', churn_risk: '流失风险' }[value] || '待分析'
}

function sufficiencyLabel(value) {
  return { sufficient: '证据充分', partial: '证据部分充分', insufficient: '证据不足，建议人工确认' }[value] || '待分析'
}

function evidenceLabel(item) {
  const sourceMap = { product: '商品', policy: '政策', faq: 'FAQ' }
  return sourceMap[item.source] || item.source || '证据'
}

function generationLabel(value) {
  return { qwen: 'Qwen3.6-Plus', qwen_rewrite: 'Qwen 重写后通过', template_fallback: '模板降级' }[value] || '待生成'
}

async function applyIntentOverride() {
  if (!currentItem.value?.id) return
  analyzing.value = true
  try {
    const res = await axios.post(`/api/interaction/override-intent/${currentItem.value.id}`, {
      intentOverride: intentOverride.value
    })
    parseAgentResult(res.data)
    currentItem.value.intentOverride = intentOverride.value
    currentItem.value.aiSuggestedReply = aiReplyDraft.value
  } catch (error) {
    console.error(error)
    alert('意图修正失败，请重试')
  } finally {
    analyzing.value = false
  }
}

function viewHistoryDetail(item) {
  currentItem.value = item
  isReadOnly.value = true
  handleForm.value.result = item.handleResult

  aiAnalysisText.value = ""
  aiReplyDraft.value = ""
  agentResult.value = null
  intentOverride.value = item.intentOverride || ''

  if (item.agentResult) parseAgentResult(item.agentResult)
  showModal.value = true
}

//粘贴部分
// ⭐ 改进点：精准提取话术内容
function quoteAi() {
  let rawText = aiReplyDraft.value || ""

  // 1. 尝试找到实际话术的开头标记（比如遇到冒号或特定前缀）
  // 很多时候 AI 会输出 "建议回复话术：亲爱的..."，我们需要截取冒号之后的内容
  let cleanText = rawText;
  const splitKeywords = ["：", ":", "\n"]; // 尝试以这些符号分割

  for (const keyword of splitKeywords) {
    if (cleanText.includes(keyword)) {
      // 找到第一个分割符，截取它后面的所有内容
      const parts = cleanText.split(keyword);
      // 考虑到可能只有换行没有冒号的情况，这里做个判断
      if (parts.length > 1 && parts[1].trim() !== "") {
        // 把分割符后面的部分重新拼起来（防止后面也有同样的符号被截断）
        cleanText = parts.slice(1).join(keyword).trim();
        break; // 成功分割，跳出循环
      }
    }
  }

  // 2. 🧹 正则数据清洗：移除残留的 Markdown 符号
  cleanText = cleanText
    .replace(/\*\*/g, '')
    .replace(/##/g, '')
    .replace(/^\s*[\-\*]\s+/gm, '')
    .replace(/`/g, '')
    .trim(); // 再次去两边空格

  const prefix = handleForm.value.result ? handleForm.value.result + "\n" : ""
  handleForm.value.result = prefix + cleanText
}

async function submitHandle() {
  if(!handleForm.value.result) return alert("请填写处理结果")
  try {
    await axios.post('http://localhost:8080/interaction/handle', {
      id: currentItem.value.id,
      handleResult: handleForm.value.result
    })
    alert("✅ 处理完成！")
    showModal.value = false
    fetchData()
  } catch (e) { alert("提交失败") }
}

function formatTime(t) { return t ? t.replace('T', ' ') : '' }
</script>

<template>
  <div class="page-container">
    <div class="tabs">
      <div class="tab-item" :class="{ active: activeTab === 'pending' }" @click="switchTab('pending')">
        🔔 待办工单 ({{ pendingList.length }})
      </div>
      <div class="tab-item" :class="{ active: activeTab === 'history' }" @click="switchTab('history')">
        📂 历史归档
      </div>
    </div>

    <div v-if="loading" class="loading-state">
      <div class="spinner-small"></div> 数据加载中...
    </div>

    <div v-else-if="activeTab === 'pending'" class="task-grid">
      <div v-if="pendingList.length === 0" class="empty-state">
        <div class="empty-icon">🎉</div>
        <div>目前没有待处理的工单</div>
      </div>
      <div v-for="item in pendingList" :key="item.id" class="task-card pending-card">
        <div class="card-header">
          <span class="tag red">待处理</span>
          <span class="date">{{ formatTime(item.createTime) }}</span>
        </div>
        <div class="user-info"><strong>{{ item.custName }}</strong> <small>(ID: {{ item.custId }})</small></div>
        <div class="content">"{{ item.content }}"</div>
        <button class="handle-btn" @click="openHandleModal(item)">⚡ 去处理</button>
      </div>
    </div>

    <div v-else class="history-list">
      <table class="history-table">
        <thead>
        <tr><th>时间</th><th>客户</th><th>类型</th><th>反馈内容</th><th>状态</th></tr>
        </thead>
        <tbody>
        <tr v-for="item in historyList" :key="item.id" @click="viewHistoryDetail(item)" class="clickable-row">
          <td>{{ formatTime(item.createTime) }}</td>
          <td>{{ item.custName }}</td>
          <td>
            <span class="type-badge" :class="item.type==='投诉'?'red':'blue'">{{ item.type }}</span>
          </td>
          <td class="col-content">{{ item.content }}</td>
          <td><span class="tag green">已完成</span></td>
        </tr>
        </tbody>
      </table>
      <div class="tip-text">💡 点击列表行可查看详细处理结果</div>
    </div>

    <div v-if="showModal" class="modal-overlay">
      <div class="modal-content">
        <div class="modal-header">
          <h3>{{ isReadOnly ? '📜 历史工单详情' : '🛠️ 工单处理' }}</h3>
          <button class="close-icon" @click="showModal = false">×</button>
        </div>

        <div class="detail-group">
          <label>客户反馈：</label>
          <div class="customer-say">{{ currentItem.content }}</div>
        </div>

        <div v-if="!isReadOnly" class="ai-suggestion-box">
          <div class="ai-title">
            <span>🤖 AI 军师建议</span>
            <button
              v-if="aiReplyDraft || aiAnalysisText"
              class="regen-btn"
              @click="regenerateReply"
              :disabled="analyzing"
            >
              {{ analyzing ? '生成中...' : '🔄 重新生成' }}
            </button>
          </div>

          <div v-if="!aiReplyDraft && !aiAnalysisText" class="ai-empty-state">
            <p>该工单暂未进行 AI 深度分析。</p>
            <button class="generate-btn" @click="generateAiReply" :disabled="analyzing">
              <span v-if="analyzing" class="spinner-mini"></span>
              {{ analyzing ? 'AI 正在思考中 (约10秒)...' : '✨ 立即生成分析与话术' }}
            </button>
          </div>

          <div v-else>
            <div class="ai-analysis">
              <strong>【深度分析】</strong> {{ aiAnalysisText }}
            </div>
            <div v-if="agentResult" class="agent-panel">
              <div class="agent-metrics">
                <span>意图：{{ agentResult.intent }}</span>
                <span class="strategy-chip">{{ valueTierLabel(agentResult.customer_strategy?.value_tier) }}</span>
                <span class="strategy-chip">{{ lifecycleRiskLabel(agentResult.customer_strategy?.lifecycle_risk) }}</span>
                <span :class="['sufficiency-chip', agentResult.evidence_sufficiency]">
                  {{ sufficiencyLabel(agentResult.evidence_sufficiency) }}
                </span>
                <span v-if="agentResult.fallback" class="warning-chip">已降级</span>
                <span>生成来源：{{ generationLabel(agentResult.generation_backend) }}</span>
              </div>

              <div class="agent-section intent-editor">
                <strong>人工修正意图</strong>
                <select v-model="intentOverride">
                  <option v-for="option in intentOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
                </select>
                <button @click="applyIntentOverride" :disabled="analyzing">应用并重新生成</button>
              </div>

              <div v-if="agentResult.trace?.length" class="agent-section">
                <strong>Agent 执行轨迹</strong>
                <div class="trace-list">
                  <span v-for="step in agentResult.trace" :key="step.node" class="trace-item">
                    {{ step.node }}
                  </span>
                </div>
              </div>

              <div v-if="agentResult.available_campaigns?.length" class="agent-section">
                <strong>可用活动</strong>
                <div v-for="item in agentResult.available_campaigns" :key="item.evidence_id" class="evidence-item campaign-item">
                  <input v-model="selectedCampaignIds" type="checkbox" :value="item.campaign_id" />
                  <span class="evidence-tag">MySQL</span>
                  <span>{{ item.campaign_name }}</span>
                  <small>{{ item.allowedReplyText }}</small>
                </div>
                <button class="regen-btn" @click="regenerateReply" :disabled="analyzing">使用选中活动重新生成</button>
              </div>

              <div v-if="agentResult.matched_products?.length" class="agent-section">
                <strong>命中商品</strong>
                <div v-for="item in agentResult.matched_products" :key="item.id" class="evidence-item">
                  <span class="evidence-tag">MySQL</span>
                  <span>{{ item.title }}</span>
                  <small>库存 {{ item.metadata?.stock }} 件 · ¥{{ item.metadata?.price }}</small>
                </div>
              </div>

              <div v-if="agentResult.service_entitlements?.length" class="agent-section">
                <strong>服务权益</strong>
                <div v-for="item in agentResult.service_entitlements" :key="item.evidence_id" class="evidence-item">
                  <span class="evidence-tag">MySQL</span>
                  <span>{{ item.name }}</span>
                  <small>{{ item.responseSla || '无固定 SLA' }} · {{ item.requiresManualApproval ? '需人工确认' : '可按边界承诺' }}</small>
                </div>
              </div>

              <div v-if="agentResult.retrieved_docs?.length" class="agent-section">
                <strong>手册证据</strong>
                <div v-for="item in agentResult.retrieved_docs" :key="item.doc_id" class="evidence-item">
                  <span class="evidence-tag">{{ item.evidence_type }}</span>
                  <span>{{ item.title }}</span>
                  <small>{{ item.text }}</small>
                </div>
              </div>

              <div v-if="agentResult.policy_guidance?.length" class="agent-section">
                <strong>政策结论与待核实项</strong>
                <div v-for="item in agentResult.policy_guidance" :key="item.evidence_id" class="evidence-item">
                  <span>{{ item.known_rule }}</span>
                  <small v-if="item.missing_checks?.length">仍需核实：{{ item.missing_checks.join('、') }}</small>
                </div>
              </div>

              <div v-if="agentResult.internal_actions?.length" class="agent-section">
                <strong>客服内部动作</strong>
                <div v-for="action in agentResult.internal_actions" :key="action" class="action-item">
                  {{ action }}
                </div>
              </div>

              <div v-if="agentResult.risk_flags?.length" class="agent-section risk-section">
                <strong>风险提示</strong>
                <div v-for="flag in agentResult.risk_flags" :key="flag.message">- {{ flag.message }}</div>
              </div>
            </div>
            <div class="ai-reply">
              <strong>【推荐话术】</strong> {{ aiReplyDraft }}
            </div>
            <button class="quote-btn" @click="quoteAi">📋 引用推荐话术</button>
            <div style="clear: both;"></div> </div>
        </div>

        <div class="form-group">
          <label>{{ isReadOnly ? '最终处理结果：' : '回复内容 / 处理结果：' }}</label>
          <textarea
            v-model="handleForm.result"
            rows="5"
            :disabled="isReadOnly"
            :class="{ 'readonly-textarea': isReadOnly }"
            placeholder="在此输入回复..."
          ></textarea>
        </div>

        <div class="modal-actions">
          <button class="cancel-btn" @click="showModal = false">{{ isReadOnly ? '关闭' : '取消' }}</button>
          <button v-if="!isReadOnly" class="confirm-btn" @click="submitHandle">✅ 标记完成</button>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
.page-container { width: 98%; margin: 20px auto; }
.tabs { display: flex; border-bottom: 2px solid #eee; margin-bottom: 20px; }
.tab-item { padding: 10px 25px; cursor: pointer; font-weight: bold; color: #666; border-bottom: 3px solid transparent; transition: all 0.2s; }
.tab-item:hover { color: #409eff; }
.tab-item.active { color: #409eff; border-bottom-color: #409eff; }

/* 列表样式 */
.task-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 20px; }
.task-card { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.05); border-left: 4px solid #f56c6c; transition: transform 0.2s; }
.task-card:hover { transform: translateY(-3px); box-shadow: 0 5px 15px rgba(0,0,0,0.1); }
.card-header { display: flex; justify-content: space-between; margin-bottom: 10px; font-size: 12px; color: #999; }
.content { background: #f9f9f9; padding: 10px; margin: 10px 0; border-radius: 4px; color: #555; font-style: italic; }
.handle-btn { width: 100%; background: #f56c6c; color: white; border: none; padding: 8px; border-radius: 4px; cursor: pointer; font-weight: bold; margin-top: 10px; }
.handle-btn:hover { background: #f78989; }

.empty-state { text-align: center; color: #999; padding: 50px; width: 100%; grid-column: 1 / -1; }
.empty-icon { font-size: 40px; margin-bottom: 10px; }

/* 历史表格 */
.history-table { width: 100%; border-collapse: collapse; background: white; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.05); }
.history-table th, .history-table td { padding: 12px; text-align: left; border-bottom: 1px solid #eee; }
.clickable-row { cursor: pointer; transition: background 0.2s; }
.clickable-row:hover { background-color: #f5f7fa; }
.col-content { max-width: 400px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #666; }
.type-badge { padding: 2px 6px; border-radius: 4px; font-size: 12px; }
.type-badge.red { background: #fef0f0; color: #f56c6c; }
.type-badge.blue { background: #ecf5ff; color: #409eff; }
.tag.green { background: #f0f9eb; color: #67c23a; padding: 2px 6px; border-radius: 4px; font-size: 12px; }
.tip-text { margin-top: 10px; font-size: 12px; color: #999; text-align: right; }

/* 弹窗样式 */
.modal-overlay { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); display: flex; justify-content: center; align-items: center; z-index: 2000; animation: fadeIn 0.2s; }
.modal-content { background: white; padding: 25px; border-radius: 8px; width: 760px; max-width: 90%; max-height: 90vh; overflow-y: auto; position: relative; animation: slideUp 0.2s; }
@keyframes slideUp { from { transform: translateY(20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }

.modal-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.modal-header h3 { margin: 0; }
.close-icon { background: none; border: none; font-size: 24px; cursor: pointer; color: #999; }
.close-icon:hover { color: #333; }

.customer-say { background: #f4f4f5; padding: 12px; border-radius: 4px; color: #333; margin-bottom: 20px; border-left: 3px solid #909399; }

/* AI 建议区 (优化版) */
.ai-suggestion-box { border: 1px solid #c6e2ff; background: #ecf5ff; padding: 15px; border-radius: 6px; margin-bottom: 20px; }
.ai-title { color: #409eff; font-weight: bold; font-size: 13px; margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center; }
.ai-analysis { font-size: 13px; color: #666; margin-bottom: 8px; padding-bottom: 8px; border-bottom: 1px dashed #c6e2ff; }
.ai-reply { font-size: 14px; color: #333; margin-bottom: 10px; line-height: 1.5; background: #fff; padding: 8px; border-radius: 4px; border: 1px solid #dcdfe6; }
.agent-panel { background: #fff; border: 1px solid #dcdfe6; border-radius: 6px; padding: 10px; margin: 10px 0; font-size: 12px; color: #555; }
.agent-metrics { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
.agent-metrics span { background: #f4f4f5; padding: 3px 7px; border-radius: 4px; }
.agent-metrics .warning-chip { background: #fdf6ec; color: #e6a23c; }
.agent-metrics .strategy-chip { background: #f0f9eb; color: #529b2e; }
.agent-metrics .sufficiency-chip.partial { background: #fdf6ec; color: #b88230; }
.agent-metrics .sufficiency-chip.insufficient { background: #fef0f0; color: #c45656; }
.agent-metrics .sufficiency-chip.sufficient { background: #f0f9eb; color: #529b2e; }
.agent-section { border-top: 1px dashed #ddd; padding-top: 8px; margin-top: 8px; }
.trace-list { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 6px; }
.trace-item { background: #ecf5ff; color: #409eff; padding: 3px 6px; border-radius: 4px; }
.evidence-item { display: grid; grid-template-columns: auto minmax(120px, auto) 1fr; align-items: start; gap: 6px; margin-top: 6px; }
.evidence-item small { color: #999; }
.campaign-item { grid-template-columns: auto auto minmax(120px, auto) 1fr; }
.evidence-tag { background: #f0f9eb; color: #67c23a; padding: 2px 5px; border-radius: 3px; }
.risk-section { color: #e6a23c; line-height: 1.6; }
.action-item { margin-top: 6px; padding-left: 12px; position: relative; }
.action-item::before { content: '•'; position: absolute; left: 0; color: #409eff; }
.intent-editor { display: flex; gap: 8px; align-items: center; }
.intent-editor select { padding: 4px 6px; border: 1px solid #dcdfe6; border-radius: 4px; }
.intent-editor button { padding: 4px 8px; border: 1px solid #409eff; background: white; color: #409eff; border-radius: 4px; cursor: pointer; }

.ai-empty-state { text-align: center; padding: 15px; color: #666; }

/* ⭐ 重新生成按钮样式 */
.regen-btn {
  background: #fdf6ec;
  color: #e6a23c;
  border: 1px solid #f5dab1;
  padding: 4px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.3s;
}
.regen-btn:hover:not(:disabled) {
  background: #e6a23c;
  color: white;
}
.regen-btn:disabled {
  background: #f5f7fa;
  color: #c0c4cc;
  border-color: #e4e7ed;
  cursor: not-allowed;
}

.generate-btn {
  background: linear-gradient(90deg, #409eff, #36cfc9);
  color: white; border: none; padding: 10px 20px; border-radius: 20px;
  cursor: pointer; font-weight: bold; margin-top: 10px; box-shadow: 0 4px 10px rgba(64,158,255,0.3);
  transition: all 0.3s;
}
.generate-btn:hover { transform: translateY(-2px); box-shadow: 0 6px 15px rgba(64,158,255,0.4); }
.generate-btn:disabled { opacity: 0.7; cursor: wait; transform: none; }

.spinner-mini {
  display: inline-block; width: 12px; height: 12px;
  border: 2px solid white; border-top-color: transparent; border-radius: 50%;
  animation: spin 1s infinite; margin-right: 5px;
}
@keyframes spin { 100% { transform: rotate(360deg); } }


.quote-btn { background: #e6a23c; color: white; border: none; padding: 5px 12px; border-radius: 4px; cursor: pointer; font-size: 12px; font-weight: bold; transition: background 0.2s; float: right; margin-top: 5px; }
.quote-btn:hover { background: #ebb563; }

.form-group label { display: block; margin-bottom: 8px; font-weight: bold; }
textarea { width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 4px; font-family: inherit; resize: vertical; }
.readonly-textarea { background-color: #f9fafc; color: #555; border-color: #eee; }

.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
.cancel-btn { padding: 8px 20px; border: 1px solid #ddd; background: white; border-radius: 4px; cursor: pointer; }
.confirm-btn { background: #409eff; color: white; border: none; padding: 8px 20px; border-radius: 4px; cursor: pointer; }

/* Spinner */
.loading-state { text-align: center; padding: 40px; color: #999; }
.spinner-small { width: 20px; height: 20px; border: 2px solid #eee; border-top-color: #409eff; border-radius: 50%; animation: spin 1s infinite; display: inline-block; vertical-align: middle; margin-right: 5px; }
@keyframes spin { 100% { transform: rotate(360deg); } }

</style>
