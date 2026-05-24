<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import * as echarts from 'echarts'

const router = useRouter()
const stats = ref({
  totalCustomers: 0,
  totalRevenue: 0,
  totalOrders: 0,
  totalInteractions: 0,
  chartDates: [],
  chartValues: []
})
// ⭐ 新增：AI 简报内容
const aiInsightText = ref("🤖 AI 正在分析今日经营数据，请稍候...")
const loading = ref(true)
const chartDom = ref(null)

onMounted(() => {
  fetchDashboard()
  fetchAiInsight() // 单独请求 AI，避免阻塞统计数字的显示
})

async function fetchDashboard() {
  try {
    const res = await axios.get('/api/dashboard/summary')
    stats.value = res.data
    nextTick(() => { initChart() })
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

// ⭐ 新增：请求 AI 简报
async function fetchAiInsight() {
  try {
    const res = await axios.get('/api/dashboard/insight')
    aiInsightText.value = res.data
  } catch (e) {
    aiInsightText.value = "无法获取 AI 简报，请检查网络。"
  }
}

function initChart() {
  if (!chartDom.value) return;
  echarts.dispose(chartDom.value);
  const myChart = echarts.init(chartDom.value);
  const option = {
    title: { text: '近 7 天营收趋势', left: 'center', textStyle: { color: '#666' } },
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: stats.value.chartDates || [], axisLine: { lineStyle: { color: '#ccc' } } },
    yAxis: { type: 'value', splitLine: { lineStyle: { type: 'dashed' } } },
    series: [{
      name: '销售额', data: stats.value.chartValues || [], type: 'line', smooth: true, symbolSize: 8,
      itemStyle: { color: '#409eff' },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(64,158,255,0.5)' }, { offset: 1, color: 'rgba(64,158,255,0.0)' }]) }
    }]
  };
  myChart.setOption(option);
  window.addEventListener('resize', () => myChart.resize());
}

function go(path) { router.push(path) }
</script>

<template>
  <div class="home-container">
    <div class="header-section">
      <div class="welcome-banner">
        <h1>👋 欢迎回来，管理员</h1>
        <p>这是您的智能客户价值分析系统驾驶舱</p>
      </div>
    </div>

    <div class="ai-insight-card">
      <div class="ai-icon-box">
        <span class="pulse-ring"></span>
        🤖
      </div>
      <div class="ai-content">
        <h3>AI 每日经营参谋</h3>
        <p class="typing-effect">{{ aiInsightText }}</p>
      </div>
    </div>

    <div class="stats-grid">
      <div class="card blue" @click="go('/customer')">
        <div class="icon">👥</div>
        <div class="info"><h3>总客户数</h3><div class="number">{{ stats.totalCustomers }}</div></div>
      </div>
      <div class="card red" @click="go('/orders')">
        <div class="icon">💰</div>
        <div class="info"><h3>总营收</h3><div class="number">¥{{ stats.totalRevenue }}</div></div>
      </div>
      <div class="card orange" @click="go('/orders')">
        <div class="icon">📦</div>
        <div class="info"><h3>总订单量</h3><div class="number">{{ stats.totalOrders }}</div></div>
      </div>
      <div class="card green" @click="go('/interactions')">
        <div class="icon">💬</div>
        <div class="info"><h3>AI 交互记录</h3><div class="number">{{ stats.totalInteractions }}</div></div>
      </div>
    </div>

    <div class="chart-section">
      <div ref="chartDom" class="chart-container"></div>
    </div>

    <div class="quick-actions">
      <div class="action-btn" @click="go('/customer')"><span>🔍</span> 客户查询与 AI 分析</div>
      <div class="action-btn" @click="go('/orders')"><span>📊</span> 查看所有订单流水</div>
    </div>
  </div>
</template>

<style scoped>
.home-container { width: 98%; margin: 20px auto; padding: 20px; }
.welcome-banner h1 { font-size: 26px; color: #333; margin: 0 0 8px 0; }
.welcome-banner p { color: #666; font-size: 14px; margin-bottom: 20px; }

/* ⭐ AI 卡片样式 */
.ai-insight-card {
  background: linear-gradient(135deg, #eef1f5 0%, #ffffff 100%);
  border: 1px solid #dcdfe6;
  border-left: 5px solid #722ed1; /* 紫色代表 AI */
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 30px;
  display: flex;
  align-items: flex-start;
  gap: 20px;
  box-shadow: 0 4px 15px rgba(0,0,0,0.03);
}
.ai-icon-box {
  font-size: 36px; position: relative; width: 50px; height: 50px;
  display: flex; align-items: center; justify-content: center;
}
.ai-content h3 { margin: 0 0 8px 0; color: #722ed1; font-size: 16px; font-weight: bold; }
.ai-content p { margin: 0; color: #333; line-height: 1.6; font-size: 14px; }

/* 呼吸灯动画 */
.pulse-ring {
  position: absolute; width: 100%; height: 100%; border-radius: 50%;
  border: 2px solid #722ed1; animation: pulse 2s infinite; opacity: 0;
}
@keyframes pulse { 0% { transform: scale(1); opacity: 0.8; } 100% { transform: scale(1.5); opacity: 0; } }

/* 统计卡片 */
.stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 20px; margin-bottom: 30px; }
.card {
  background: white; padding: 25px; border-radius: 12px;
  box-shadow: 0 4px 15px rgba(0,0,0,0.05); cursor: pointer; transition: all 0.2s;
  display: flex; align-items: center; gap: 20px; height: 120px;
}
.card:hover { transform: translateY(-5px); box-shadow: 0 8px 25px rgba(0,0,0,0.1); }
.card.blue { border-left: 5px solid #409eff; }
.card.red { border-left: 5px solid #f56c6c; }
.card.orange { border-left: 5px solid #e6a23c; }
.card.green { border-left: 5px solid #67c23a; }
.icon { font-size: 32px; width: 60px; height: 60px; background: #f5f7fa; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.info h3 { margin: 0 0 5px 0; font-size: 14px; color: #888; font-weight: normal; }
.number { font-size: 28px; font-weight: bold; color: #333; }

/* 图表与按钮 */
.chart-section { background: white; padding: 20px; border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.05); margin-bottom: 30px; }
.chart-container { width: 100%; height: 350px; }
.quick-actions { display: flex; gap: 20px; }
.action-btn { background: white; padding: 15px 30px; border-radius: 8px; border: 1px solid #eee; cursor: pointer; font-weight: bold; color: #555; display: flex; gap: 10px; transition: all 0.2s; }
.action-btn:hover { border-color: #409eff; color: #409eff; background: #ecf5ff; }
</style>
