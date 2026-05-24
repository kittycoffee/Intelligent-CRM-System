<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'

const route = useRoute()
const router = useRouter()
const custId = route.query.custId

// 页面数据
const customer = ref({})
const rfm = ref({})
const orders = ref([])
const interactions = ref([])
const aiAdvice = ref({}) // AI 建议
const aiAdviceTime = ref('') // AI 生成时间 (如果后端返回的话)
const loading = ref(true) // 首次加载的全屏 Loading

// ⭐ 交互录入弹窗状态
const showAddModal = ref(false)
const addForm = ref({
  content: '',
  type: '咨询' // 默认选咨询
})

// ⭐ 按钮状态
const analyzing = ref(false) // "重新诊断"按钮的转圈
const submitting = ref(false) // "录入交互"时的遮罩层

onMounted(() => {
  if (custId) {
    fetchDetail()
  }
})

// ⭐ 功能 1：手动刷新 AI 诊断
async function refreshAdvice() {
  analyzing.value = true
  try {
    // 1. 发送诊断指令 (设置 60秒 超时)
    await axios.post(
      `/api/customer/diagnose/${custId}`,
      {},
      {timeout: 60000}
    )

    // 2. 诊断完成后，重新拉取页面数据
    // 🔴 修复点：这里原来写的是 fetchData()，改成了 fetchDetail()
    await fetchDetail()

    alert("✅ AI 诊断已更新！")
  } catch (e) {
    console.error(e)
    if (e.code === 'ECONNABORTED') {
      alert("AI 思考时间较长，请稍后刷新页面查看结果")
    } else {
      alert("分析失败，请检查后端日志")
    }
  } finally {
    analyzing.value = false
  }
}

// ⭐ 功能 2：获取页面所有数据
async function fetchDetail() {
  try {
    const res = await axios.get('/api/customer/detail', {
      params: {custId: custId}
    })

    // 赋值数据 (加了空值保护)
    const data = res.data || {}
    customer.value = data.info || {}
    rfm.value = data.rfm || {rscore: 0, fscore: 0, mscore: 0, customerLevel: '普通客户'}
    orders.value = data.orders || []
    interactions.value = data.interactions || []

    // AI 数据处理
    aiAdvice.value = data.aiAdvice || {}
    // 如果后端传了 createTime，可以在这里保存，比如:
    // aiAdviceTime.value = data.aiAdvice?.createTime

  } catch (e) {
    console.error(e)
    alert("数据加载失败")
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.back()
}

// --- ⭐ 功能 3：新的录入逻辑 START ---

// 打开弹窗
function openAddModal() {
  addForm.value = {content: '', type: '咨询'}
  showAddModal.value = true
}

// 提交录入
async function submitAddRecord() {
  if (!addForm.value.content) return alert("请输入交互内容")

  submitting.value = true // 开启遮罩层 Loading

  try {
    await axios.post('/api/interaction/add', {
      custId: custId,
      content: addForm.value.content,
      type: addForm.value.type
    })

    // 录入成功后，刷新页面数据 (AI 也会在后台更新)
    await fetchDetail()

    showAddModal.value = false // 关闭弹窗
  } catch (e) {
    console.error(e)
    alert("录入失败，请检查后端")
  } finally {
    submitting.value = false // 关闭遮罩层
  }
}

// --- ⭐ 新的录入逻辑 END ---

function formatTime(t) {
  return t ? t.replace('T', ' ') : ''
}

// 辅助函数：根据等级返回颜色 class
function getLevelClass(level) {
  if (!level) return '';
  if (level.includes('重要价值')) return 'tag-red';
  if (level.includes('一般保持')) return 'tag-blue';
  if (level.includes('流失风险')) return 'tag-grey';
  return 'tag-green'; // 兜底颜色
}
</script>

<template>
  <div class="page-wrap">

    <div v-if="loading" class="global-loading">
      <div class="spinner"></div> 正在加载客户档案...
    </div>

    <div class="page-container" :class="{ blur: loading }">

      <div class="header-bar">
        <div class="left">
          <button class="back-btn" @click="goBack">← 返回列表</button>
          <h2 class="title">👤 客户全景档案</h2>
        </div>
        <button class="add-record-btn" @click="openAddModal">📝 录入新交互</button>
      </div>

      <div class="dashboard-layout">

        <div class="left-column">
          <div class="info-card">
            <div class="avatar">{{ customer.custName ? customer.custName[0] : 'U' }}</div>
            <h3>{{ customer.custName }}</h3>
            <span class="level-tag" :class="getLevelClass(rfm.customerLevel)">
              {{ rfm.customerLevel || '普通客户' }}
            </span>
            <div class="detail-list">
              <p><strong>ID：</strong> {{ customer.custId }}</p>
              <p><strong>性别：</strong> {{ customer.gender }}</p>
              <p><strong>电话：</strong> {{ customer.phone }}</p>
              <p><strong>生日：</strong> {{ customer.birthday }}</p>
            </div>
          </div>

          <div class="rfm-card">
            <h4>📊 RFM 评分</h4>
            <div class="rfm-item">
              <span>R (近度)</span>
              <div class="progress"><div class="bar" :style="{width: (rfm.rscore*20)+'%'}"></div></div>
              <span class="score">{{ rfm.rscore }}分</span>
            </div>
            <div class="rfm-item">
              <span>F (频度)</span>
              <div class="progress"><div class="bar" :style="{width: (rfm.fscore*20)+'%'}"></div></div>
              <span class="score">{{ rfm.fscore }}分</span>
            </div>
            <div class="rfm-item">
              <span>M (额度)</span>
              <div class="progress"><div class="bar" :style="{width: (rfm.mscore*20)+'%'}"></div></div>
              <span class="score">{{ rfm.mscore }}分</span>
            </div>
          </div>
        </div>

        <div class="right-column">

          <div class="analysis-card">
            <div class="card-header-row">
              <h3>🛡️ 客户价值诊断 (AI 参谋)</h3>
              <button class="refresh-btn" @click="refreshAdvice" :disabled="analyzing">
                <span :class="{ 'spinning': analyzing }">⚡</span>
                {{ analyzing ? '深度分析中...' : '重新诊断' }}
              </button>
            </div>

            <div class="ai-content">
              <div v-if="!aiAdvice || !aiAdvice.adviceContent" class="loading-text">
                AI 正在等待指令，请点击右上角“重新诊断”...
              </div>
              <div v-else class="markdown-body">
                {{ aiAdvice.adviceContent }}
              </div>

              <div v-if="aiAdvice && aiAdvice.createTime" class="time-tag">
                生成时间: {{ formatTime(aiAdvice.createTime) }}
              </div>
            </div>
          </div>

          <div class="section-card">
            <div class="card-title">💬 历史交互记录</div>
            <div class="interaction-list">
              <div v-for="item in interactions" :key="item.id" class="interaction-item">
                <div class="item-head">
                  <span class="type-tag" :class="item.interactionType==='投诉'?'tag-red':'tag-blue'">
                    {{ item.interactionType }}
                  </span>
                  <span class="time">{{ formatTime(item.createTime) }}</span>
                  <span class="status-badge" :class="item.status === 1 ? 'done' : 'pending'">
                    {{ item.status === 1 ? '✅ 已完成' : '⏳ 待处理' }}
                  </span>
                </div>
                <div class="item-body">{{ item.content }}</div>
                <div v-if="item.status === 1 && item.handleResult" class="handle-result">
                  ↪️ <strong>处理结果：</strong> {{ item.handleResult }}
                </div>
              </div>
            </div>
          </div>

          <div class="section-card">
            <div class="card-title">💰 消费订单 (Top 10)</div>
            <table class="simple-table">
              <thead>
              <tr>
                <th>购买商品</th>
                <th>金额</th>
                <th>下单时间</th>
                <th>状态</th>
              </tr>
              </thead>
              <tbody>
              <tr v-for="order in orders" :key="order.orderId">
                <td style="font-weight: bold; color: #333;" :title="'订单号: ' + order.orderId">
                  {{ order.productName }}
                </td>

                <td class="money">¥{{ order.orderAmount }}</td>
                <td>{{ formatTime(order.orderDate) }}</td>
                <td><span class="status-ok">✅ 完成</span></td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showAddModal" class="modal-overlay">
      <div class="modal-content">
        <h3>📝 录入新交互</h3>
        <p class="modal-desc">系统将自动触发 AI 重新评估该客户的价值与流失风险。</p>

        <div class="form-item">
          <label>交互类型：</label>
          <div class="radio-group">
            <label class="radio-label">
              <input type="radio" v-model="addForm.type" value="咨询">
              <span class="radio-btn blue">📞 咨询/日常</span>
            </label>
            <label class="radio-label">
              <input type="radio" v-model="addForm.type" value="投诉">
              <span class="radio-btn red">😡 投诉/不满</span>
            </label>
          </div>
        </div>

        <div class="form-item">
          <label>详细内容：</label>
          <textarea v-model="addForm.content" rows="4" placeholder="例如：客户询问是否有双十一活动..."></textarea>
        </div>

        <div class="modal-actions">
          <button class="cancel-btn" @click="showAddModal = false">取消</button>
          <button class="submit-btn" @click="submitAddRecord">确定录入</button>
        </div>
      </div>
    </div>

    <div v-if="submitting" class="loading-mask">
      <div class="loading-box">
        <div class="spinner"></div>
        <p>正在同步 AI 分析...</p>
      </div>
    </div>

  </div>
</template>

<style scoped>
/* 页面容器 */
.page-wrap { position: relative; min-height: 100vh; background-color: #f6f8fa; padding-bottom: 40px; }
.page-container { width: 98%; max-width: 1200px; margin: 20px auto; transition: filter 0.3s; }
.page-container.blur { filter: blur(5px); pointer-events: none; }

/* 头部 Header */
.header-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.left { display: flex; align-items: center; gap: 15px; }
.back-btn { padding: 8px 15px; background: white; border: 1px solid #ddd; cursor: pointer; border-radius: 4px; color: #666; transition: all 0.2s; }
.back-btn:hover { background: #f0f0f0; }
.title { margin: 0; font-size: 20px; color: #333; }

.add-record-btn { background: #409eff; color: white; border: none; padding: 10px 20px; border-radius: 4px; font-weight: bold; cursor: pointer; box-shadow: 0 4px 10px rgba(64,158,255,0.3); transition: all 0.2s;}
.add-record-btn:hover { transform: translateY(-2px); box-shadow: 0 6px 15px rgba(64,158,255,0.4); }

/* 布局 */
.dashboard-layout { display: flex; gap: 20px; align-items: flex-start; }
.left-column { width: 300px; flex-shrink: 0; display: flex; flex-direction: column; gap: 20px; }
.right-column { flex: 1; display: flex; flex-direction: column; gap: 20px; }

/* 标题栏布局 (通用) */
.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  border-bottom: 2px solid #f0f2f5;
  padding-bottom: 10px;
}
.card-header-row h3 { margin: 0; color: #409eff; }

/* 重新诊断按钮 */
.refresh-btn {
  background: white;
  border: 1px solid #409eff;
  color: #409eff;
  padding: 5px 12px;
  border-radius: 20px;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.3s;
  display: flex; align-items: center; gap: 4px;
}
.refresh-btn:hover { background: #ecf5ff; }
.refresh-btn:disabled { border-color: #ddd; color: #999; cursor: wait; background: #f5f5f5; }

/* 转圈动画 */
.spinning { display: inline-block; animation: rotate 1s linear infinite; }
@keyframes rotate { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

/* 卡片样式基础 */
.info-card, .rfm-card, .section-card, .analysis-card {
  background: white; padding: 25px; border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.05);
}

/* 左侧：头像与信息 */
.avatar { width: 80px; height: 80px; background: #42b883; color: white; font-size: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 15px; }
.info-card h3 { text-align: center; margin-bottom: 10px; color: #333; }
.detail-list { margin-top: 20px; text-align: left; font-size: 14px; line-height: 1.8; color: #555; }

/* 左侧：RFM */
.rfm-item { display: flex; align-items: center; margin-bottom: 15px; font-size: 13px; gap: 10px; }
.progress { flex: 1; height: 8px; background: #eee; border-radius: 4px; overflow: hidden; }
.bar { height: 100%; background: #409eff; border-radius: 4px; }
.level-tag {
  padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: bold;
  display: block; width: fit-content; margin: 0 auto 15px; /* 居中 */
}
/* 状态标签通用样式 */
.tag-red {
  background: #fef0f0;
  color: #f56c6c;
  border: 1px solid #fde2e2;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
}
.tag-blue {
  background: #ecf5ff;
  color: #409eff;
  border: 1px solid #d9ecff;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
}
.tag-grey {
  background: #f4f4f5;
  color: #909399;
  border: 1px solid #e9e9eb;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
}

/* 右侧：AI 诊断卡片 */
.analysis-card { border-left: 5px solid #409eff; }
.ai-content { color: #333; line-height: 1.8; font-size: 14px; text-align: justify; }
.loading-text { color: #999; font-style: italic; padding: 10px 0; }
.time-tag { margin-top: 15px; text-align: right; font-size: 12px; color: #999; }
.markdown-body { white-space: pre-wrap; } /* 保持 AI 的换行格式 */

/* 右侧：交互列表 */
.card-title { font-size: 16px; font-weight: bold; margin-bottom: 15px; border-bottom: 1px solid #eee; padding-bottom: 10px; color: #333; }
.interaction-item { padding: 15px; background: #f9fafc; border-radius: 6px; margin-bottom: 15px; border-left: 3px solid #ccc; transition: all 0.2s;}
.interaction-item:hover { background: #f0f2f5; }
.item-head { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; font-size: 12px; }
.type-tag { font-weight: bold; padding: 2px 6px; border-radius: 4px; font-size: 12px; }
.time { color: #999; margin-right: auto; }
.status-badge { padding: 2px 6px; border-radius: 4px; font-weight: bold; font-size: 12px; }
.status-badge.done { background: #f0f9eb; color: #67c23a; }
.status-badge.pending { background: #fdf6ec; color: #e6a23c; }
.handle-result { margin-top: 8px; padding-top: 8px; border-top: 1px dashed #ddd; color: #1890ff; font-size: 13px; }

/* 右侧：订单表 */
.simple-table { width: 100%; border-collapse: collapse; margin-top: 10px; }
.simple-table th, .simple-table td { padding: 12px 10px; text-align: left; border-bottom: 1px solid #eee; font-size: 13px; }
.simple-table th { background-color: #f9fafc; color: #666; font-weight: 600; }
.money { font-weight: bold; color: #f56c6c; }
.status-ok { color: #67c23a; font-weight: bold; }

/* ⭐ 弹窗样式 (Modal) */
.modal-overlay { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); display: flex; justify-content: center; align-items: center; z-index: 2000; animation: fadeIn 0.2s; }
.modal-content { background: white; padding: 30px; border-radius: 12px; width: 500px; box-shadow: 0 10px 30px rgba(0,0,0,0.2); animation: slideUp 0.3s ease; }
@keyframes slideUp { from { transform: translateY(20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

.modal-content h3 { margin-top: 0; color: #333; margin-bottom: 10px;}
.modal-desc { color: #666; font-size: 13px; margin-bottom: 25px; background: #f0f9eb; padding: 10px; border-radius: 4px; color: #67c23a; border: 1px solid #e1f3d8;}

.form-item { margin-bottom: 20px; }
.form-item label { display: block; font-weight: bold; margin-bottom: 8px; color: #333; font-size: 14px; }
.form-item textarea { width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 6px; font-family: inherit; font-size: 14px; resize: vertical; box-sizing: border-box; }
.form-item textarea:focus { border-color: #409eff; outline: none; box-shadow: 0 0 0 2px rgba(64,158,255,0.2); }

/* 单选按钮组 */
.radio-group { display: flex; gap: 15px; }
.radio-label input { display: none; } /* 隐藏原生 radio */
.radio-btn { padding: 8px 20px; border: 1px solid #ddd; border-radius: 20px; cursor: pointer; font-size: 14px; transition: all 0.2s; color: #666; display: inline-block;}
.radio-label input:checked + .radio-btn.blue { background: #ecf5ff; border-color: #409eff; color: #409eff; font-weight: bold; }
.radio-label input:checked + .radio-btn.red { background: #fef0f0; border-color: #f56c6c; color: #f56c6c; font-weight: bold; }

.modal-actions { display: flex; justify-content: flex-end; gap: 15px; margin-top: 30px; }
.cancel-btn { padding: 10px 20px; border: 1px solid #ddd; background: white; border-radius: 6px; cursor: pointer; color: #666; transition: all 0.2s; }
.cancel-btn:hover { background: #f5f5f5; border-color: #ccc; }
.submit-btn { padding: 10px 25px; background: #409eff; color: white; border: none; border-radius: 6px; cursor: pointer; font-weight: bold; transition: all 0.2s; }
.submit-btn:hover { background: #66b1ff; transform: translateY(-1px); }

/* ⭐ 提交 Loading 遮罩 */
.loading-mask { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(255,255,255,0.8); display: flex; justify-content: center; align-items: center; z-index: 3000; backdrop-filter: blur(2px); }
.loading-box { text-align: center; color: #409eff; font-weight: bold; }
.spinner { width: 40px; height: 40px; border: 4px solid #f3f3f3; border-top: 4px solid #409eff; border-radius: 50%; animation: spin 1s linear infinite; margin: 0 auto 15px; }
@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }

/* 全局首次 Loading */
.global-loading { position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); text-align: center; color: #999; z-index: 999; }
</style>
