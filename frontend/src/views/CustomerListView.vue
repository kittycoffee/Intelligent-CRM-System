<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const list = ref([])
const loading = ref(true)

// 查询条件
const searchForm = ref({
  name: '',
  level: '',
  gender: ''
})

onMounted(() => {
  fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    // 构造查询参数
    const params = {}
    if (searchForm.value.name) params.name = searchForm.value.name
    if (searchForm.value.level && searchForm.value.level !== '全部等级') params.level = searchForm.value.level
    if (searchForm.value.gender && searchForm.value.gender !== '全部性别') params.gender = searchForm.value.gender

    const res = await axios.get('/api/customer/list', { params })
    list.value = res.data
  } catch (e) {
    console.error(e)
    alert("加载客户列表失败")
  } finally {
    loading.value = false
  }
}

// 跳转详情
function toDetail(id) {
  router.push({ path: '/customer/detail', query: { custId: id } })
}

// // 辅助函数：截取 AI 建议的前 20 个字用于预览
// function shortAdvice(text) {
//   if (!text) return '暂无分析'
//   return text.length > 20 ? text.substring(0, 20) + '...' : text
// }

// --- 替换原来的 shortAdvice 函数 ---

// 1. 修改：专门清理 Markdown 符号的“净化函数”（保留了 \n）
function cleanMarkdown(text) {
  if (!text) return '暂无分析'
  // 删掉 *、#、`，但把换行符留着
  return text.replace(/[*#`]/g, '').trim()
}

// 2. 修改：截取预览文本 (基于净化后的文本截取)
function shortAdvice(text) {
  const cleanText = cleanMarkdown(text)
  if (cleanText === '暂无分析') return cleanText
  return cleanText.length > 20 ? cleanText.substring(0, 20) + '...' : cleanText
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
  <div class="page-container">

    <div class="header-bar">
      <h2>👥 客户档案管理</h2>
    </div>

    <div class="search-bar">
      <input v-model="searchForm.name" placeholder="请输入客户姓名..." class="search-input">

      <select v-model="searchForm.level">
        <option value="">全部等级</option>
        <option value="重要价值客户">重要价值客户</option>
        <option value="一般保持客户">一般保持客户</option>
        <option value="流失风险客户">流失风险客户</option>
      </select>

      <select v-model="searchForm.gender" class="search-select">
        <option value="">全部性别</option>
        <option value="男">男</option>
        <option value="女">女</option>
      </select>

      <button class="search-btn" @click="fetchList">🔍 查询</button>
    </div>

    <div class="table-card">
      <div v-if="loading" class="loading-box">加载中...</div>

      <table v-else class="data-table">
        <thead>
        <tr>
          <th>ID</th>
          <th>姓名</th>
          <th>性别</th>
          <th>电话</th>
          <th>RFM 等级</th>
          <th>AI 最新建议预览</th>
          <th>操作</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="item in list" :key="item.info.custId">
          <td>{{ item.info.custId }}</td>
          <td class="name-col">{{ item.info.custName }}</td>
          <td>{{ item.info.gender }}</td>
          <td>{{ item.info.phone }}</td>
          <td>
              <span class="level-tag" :class="getLevelClass(item.rfm.customerLevel)">
                {{ item.rfm.customerLevel || '普通客户' }}
              </span>
          </td>
          <td class="ai-col">
              <span v-if="item.aiAdvice" class="ai-preview" :title="cleanMarkdown(item.aiAdvice)">
    💡             {{ shortAdvice(item.aiAdvice) }}
              </span>
            <span v-else class="no-data">暂无分析</span>
          </td>
          <td>
            <button class="action-btn" @click="toDetail(item.info.custId)">详情</button>
          </td>
        </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.page-container { width: 98%; margin: 20px auto; }
.header-bar { margin-bottom: 20px; border-left: 5px solid #409eff; padding-left: 15px; }
.header-bar h2 { margin: 0; color: #333; }

/* 搜索栏 */
.search-bar { background: white; padding: 20px; border-radius: 8px; display: flex; gap: 15px; margin-bottom: 20px; box-shadow: 0 2px 12px rgba(0,0,0,0.05); }
.search-input, .search-select { padding: 10px; border: 1px solid #ddd; border-radius: 4px; outline: none; }
.search-input { width: 200px; }
.search-btn { background: #42b883; color: white; border: none; padding: 0 25px; border-radius: 4px; cursor: pointer; font-weight: bold; transition: 0.2s; }
.search-btn:hover { background: #3aa876; }

/* 表格 */
.table-card { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 12px rgba(0,0,0,0.05); min-height: 400px; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { padding: 15px; text-align: left; border-bottom: 1px solid #eee; }
.data-table th { background: #f9fafc; color: #666; font-weight: 600; }
.name-col { font-weight: bold; color: #333; }

/* 标签样式 */
.level-tag { padding: 4px 10px; border-radius: 4px; font-size: 12px; font-weight: bold; color: white; }
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

/* AI 预览列 */
.ai-col { max-width: 300px; }
.ai-preview { background: #f0f9eb; color: #67c23a; padding: 4px 8px; border-radius: 4px; font-size: 13px; cursor: help; }
.no-data { color: #ccc; font-size: 12px; }

.action-btn { background: white; border: 1px solid #dcdfe6; color: #606266; padding: 6px 15px; border-radius: 4px; cursor: pointer; transition: 0.2s; }
.action-btn:hover { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }

.loading-box { text-align: center; color: #999; padding: 50px; }
</style>
