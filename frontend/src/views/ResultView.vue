<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'

// 定义一个变量，用来装后端传来的列表数据
const adviceList = ref([])
const loading = ref(true)
const router = useRouter()

// 页面一加载（onMounted），就立马去后端“取货”
onMounted(async () => {
  fetchData()
})

// 专门用来取数据的函数
async function fetchData() {
  try {
    // 对应你 Controller 里的 @GetMapping("/history")
    const response = await axios.get('/api/ai/history')
    console.log("拿到历史记录了:", response.data)
    adviceList.value = response.data
  } catch (error) {
    console.error("取货失败:", error)
    alert("无法获取数据，请检查后端是否报错")
  } finally {
    loading.value = false
  }
}

// 清空历史记录的功能（对应你的 DeleteMapping）
async function clearHistory() {
  if(!confirm("确定要清空所有分析记录吗？")) return

  try {
    await axios.delete('/api/ai/history')
    adviceList.value = [] // 前端也清空一下，视觉上同步
    alert("已清空！")
  } catch (e) {
    alert("删除失败")
  }
}

function goBack() {
  router.push('/') // 返回首页
}

// 辅助函数：给不同等级加上不同的样式类名
function getLevelClass(level) {
  if (!level) return ''
  if (level.includes('重要')) return 'tag-important'
  if (level.includes('挽留')) return 'tag-warning'
  if (level.includes('一般')) return 'tag-normal'
  return 'tag-default'
}

// 辅助函数：简单的处理时间显示 (比如去掉 'T')
function formatTime(timeStr) {
  if(!timeStr) return ''
  return timeStr.replace('T', ' ')
}
</script>

<template>
  <div class="result-container">
    <div class="header">
      <h2>📊 AI 营销建议大盘</h2>
      <button @click="goBack" class="back-btn">返回首页</button>
    </div>

    <div v-if="loading" class="loading-box">
      <div class="spinner"></div>
      <p>正在读取数据库...</p>
    </div>

    <div v-else class="table-wrapper">
      <div v-if="adviceList.length === 0" class="empty-tip">
        暂无数据，请返回首页点击“开始分析”
      </div>

      <table v-else>
        <thead>
        <tr>
          <th width="100">客户ID</th>
          <th width="150">客户等级</th>
          <th>AI 建议策略</th>
          <th width="180">生成时间</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="item in adviceList" :key="item.id">
          <td>{{ item.custId }}</td>

          <td>
              <span class="tag" :class="getLevelClass(item.customerLevel)">
                {{ item.customerLevel }}
              </span>
          </td>

          <td class="advice-text">{{ item.adviceContent }}</td>

          <td class="time">{{ formatTime(item.createTime) }}</td>
        </tr>
        </tbody>
      </table>

      <button v-if="adviceList.length > 0" @click="clearHistory" class="clear-btn">
        🗑️ 清空历史记录
      </button>
    </div>
  </div>
</template>

<style scoped>
.result-container {
  max-width: 900px;
  margin: 40px auto;
  padding: 20px;
  color: #333333; /* ⭐ 关键：让所有默认文字变黑 */
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 10px;
  background-color: white; /* 确保表格背景是纯白 */
  box-shadow: 0 2px 12px rgba(0,0,0,0.1);
}

th, td {
  border: 1px solid #eee;
  padding: 12px;
  text-align: left;
  color: #333333;
}

th {
  background-color: #f8f9fa;
  font-weight: bold;
  color: #000000; /* 表头字用纯黑 */
}

.advice-text {
  max-width: 400px; /* 防止文字太长撑爆屏幕 */
  line-height: 1.5;
}

.tag {
  background-color: #e1f3d8;
  color: #67c23a;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.back-btn {
  background-color: #909399;
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 4px;
  cursor: pointer;
}

.clear-btn {
  margin-top: 20px;
  background-color: #f56c6c;
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 4px;
  cursor: pointer;
  float: right;
}

.clear-btn:hover {
  background-color: #ff4949;
}

.tag {
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: bold;
}

.tag-important { background-color: #f56c6c; color: white; } /* 红色 */
.tag-warning { background-color: #e6a23c; color: white; }   /* 橙色 */
.tag-normal { background-color: #409eff; color: white; }    /* 蓝色 */
.tag-default { background-color: #909399; color: white; }   /* 灰色 */
</style>
