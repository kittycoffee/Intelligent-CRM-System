<script setup>
import { ref, onMounted, computed } from 'vue'
import axios from 'axios'

// --- 🔒 权限控制核心 ---
const userRole = localStorage.getItem('userRole')
// 判断是否为管理员 (只有 role 是 'admin' 才是 true)
const isAdmin = userRole === 'admin'

// --- 数据定义区 ---
const list = ref([])
const loading = ref(false)
const showModal = ref(false)
const submitting = ref(false)

// 当前选中的分类
const currentCategory = ref('全部')

// 表单数据模型
const form = ref({
  productId: null,
  productName: '',
  category: '',
  price: '',
  stock: '',
  features: ''
})

onMounted(() => {
  fetchList()
})

// --- 核心方法区 ---

async function fetchList() {
  loading.value = true
  try {
    const res = await axios.get('/api/product/list')
    list.value = res.data
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

// 自动提取分类
const categoryOptions = computed(() => {
  const cats = list.value.map(item => item.category).filter(c => c)
  return ['全部', ...new Set(cats)]
})

// 动态过滤
const filteredList = computed(() => {
  if (currentCategory.value === '全部') {
    return list.value
  }
  return list.value.filter(item => item.category === currentCategory.value)
})

function openModal(item = null) {
  if (item) {
    form.value = { ...item }
  } else {
    form.value = { productId: null, productName: '', category: '', price: '', stock: 100, features: '' }
  }
  showModal.value = true
}

async function submitForm() {
  if (!form.value.productName || !form.value.features) return alert("请填写完整信息")

  submitting.value = true
  try {
    await axios.post('/api/product/save', form.value)
    alert("保存成功！AI 知识库已更新")
    showModal.value = false
    fetchList()
  } catch (e) {
    alert("保存失败")
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(item) {
  const newStatus = item.status === 1 ? 0 : 1
  try {
    await axios.post('/api/product/status', {
      productId: item.productId,
      status: newStatus
    })
    item.status = newStatus
  } catch (e) {
    alert("操作失败")
  }
}

async function deleteItem(id) {
  if(!confirm("确定删除吗？")) return
  try {
    await axios.post(`/api/product/delete/${id}`)
    fetchList()
  } catch(e) { alert("删除失败") }
}
</script>

<template>
  <div class="page-container">
    <div class="header-bar">
      <div class="left-panel">
        <h2>📦 商品管理中心 (AI 知识库)</h2>

        <div class="filter-box">
          <label>📂 分类筛选：</label>
          <select v-model="currentCategory">
            <option v-for="opt in categoryOptions" :key="opt" :value="opt">
              {{ opt }}
            </option>
          </select>
        </div>
      </div>

      <button v-if="isAdmin" class="add-btn" @click="openModal(null)">+ 新增商品</button>
    </div>

    <div class="table-card">
      <table class="data-table">
        <thead>
        <tr>
          <th>ID</th>
          <th>商品名称</th>
          <th>分类</th>
          <th>价格</th>
          <th>卖点 / AI 知识特征</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="item in filteredList" :key="item.productId" :class="{ 'offline': item.status === 0 }">
          <td>{{ item.productId }}</td>
          <td class="bold-text">{{ item.productName }}</td>
          <td><span class="tag-gray">{{ item.category }}</span></td>
          <td class="price-text">¥{{ item.price }}</td>
          <td class="feature-col">{{ item.features }}</td>
          <td>
              <span class="status-badge" :class="item.status===1?'green':'gray'">
                {{ item.status===1 ? '已上架' : '已下架' }}
              </span>
          </td>

          <td>
<!--            Vue 的条件渲染指令。它会去检查前端定义的一个变量或计算属性 isAdmin（也就是“是否为管理员”）。-->
            <div v-if="isAdmin" class="btn-group">
              <button class="action-btn edit" @click="openModal(item)">编辑</button>
              <button class="action-btn toggle" @click="toggleStatus(item)">
                {{ item.status===1 ? '下架' : '上架' }}
              </button>
              <button class="action-btn del" @click="deleteItem(item.productId)">删除</button>
            </div>

            <span v-else class="tag-gray">无权限</span>
          </td>

        </tr>
        </tbody>
      </table>

      <div v-if="filteredList.length === 0" class="empty-tip">
        该分类下暂无商品
      </div>
    </div>

    <div v-if="showModal" class="modal-overlay">
      <div class="modal-content">
        <h3>{{ form.productId ? '✏️ 编辑商品' : '✨ 新增商品' }}</h3>

        <div class="form-group">
          <label>商品名称：</label>
          <input v-model="form.productName" placeholder="如：华为 Mate 60">
        </div>
        <div class="form-row">
          <div class="form-group half">
            <label>分类：</label>
            <input v-model="form.category" placeholder="如：手机" list="cat-suggestions">
            <datalist id="cat-suggestions">
              <option v-for="opt in categoryOptions" :key="opt" :value="opt"></option>
            </datalist>
          </div>
          <div class="form-group half">
            <label>价格：</label>
            <input v-model="form.price" type="number" placeholder="0.00">
          </div>
        </div>
        <div class="form-group">
          <label>库存：</label>
          <input v-model="form.stock" type="number">
        </div>

        <div class="form-group feature-group">
          <label>🔥 核心卖点 (AI 将读取此内容进行推荐)：</label>
          <textarea v-model="form.features" rows="4" placeholder="例如：遥遥领先，支持卫星通话，超耐摔昆仑玻璃..."></textarea>
          <p class="tip">提示：写得越详细，AI 推荐时的话术就越专业！</p>
        </div>

        <div class="modal-actions">
          <button class="cancel-btn" @click="showModal = false">取消</button>
          <button class="confirm-btn" @click="submitForm" :disabled="submitting">
            {{ submitting ? '保存中...' : '确认保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-container { width: 98%; margin: 20px auto; }
.header-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.left-panel { display: flex; align-items: center; gap: 30px; }
.left-panel h2 { margin: 0; }

.filter-box { display: flex; align-items: center; gap: 10px; font-size: 14px; color: #666; }
.filter-box select { padding: 8px 12px; border: 1px solid #ddd; border-radius: 4px; outline: none; cursor: pointer; background: white; min-width: 120px; }

.add-btn { background: #409eff; color: white; border: none; padding: 10px 20px; border-radius: 6px; cursor: pointer; font-weight: bold; transition: all 0.2s; }
.add-btn:hover { background: #66b1ff; transform: translateY(-2px); }

.table-card { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 12px rgba(0,0,0,0.05); }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { padding: 12px; text-align: left; border-bottom: 1px solid #eee; }
.data-table th { background: #f9fafc; color: #666; font-weight: 600; }

.offline { opacity: 0.6; background-color: #fcfcfc; }
.bold-text { font-weight: bold; color: #333; }
.tag-gray { background: #f0f2f5; padding: 4px 8px; border-radius: 4px; font-size: 12px; color: #666; }
.price-text { color: #f56c6c; font-weight: bold; }
.feature-col { max-width: 300px; color: #666; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.empty-tip { text-align: center; color: #999; padding: 40px; }

.status-badge { padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }
.status-badge.green { background: #f0f9eb; color: #67c23a; }
.status-badge.gray { background: #f4f4f5; color: #909399; }

/* 按钮组样式调整 */
.btn-group { display: flex; }
.action-btn { margin-right: 8px; padding: 4px 10px; border-radius: 4px; border: 1px solid #eee; cursor: pointer; background: white; font-size: 12px; }
.action-btn.edit { color: #409eff; border-color: #d9ecff; }
.action-btn.toggle { color: #e6a23c; border-color: #fdf6ec; }
.action-btn.del { color: #f56c6c; border-color: #fde2e2; }
.action-btn:hover { opacity: 0.8; }

/* 弹窗样式 */
.modal-overlay { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); display: flex; justify-content: center; align-items: center; z-index: 999; }
.modal-content { background: white; width: 500px; padding: 30px; border-radius: 8px; animation: slideIn 0.3s; }
@keyframes slideIn { from { transform: translateY(-20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }

.form-group { margin-bottom: 15px; }
.form-row { display: flex; gap: 15px; }
.half { flex: 1; }
label { display: block; margin-bottom: 5px; font-weight: bold; color: #333; }
input, textarea { width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 4px; box-sizing: border-box; }
textarea { resize: vertical; }
.feature-group textarea { border: 2px solid #e1f3d8; background: #f0f9eb; }
.tip { font-size: 12px; color: #67c23a; margin-top: 5px; }

.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
.cancel-btn { padding: 8px 20px; border: 1px solid #ddd; background: white; cursor: pointer; border-radius: 4px; }
.confirm-btn { padding: 8px 20px; background: #409eff; color: white; border: none; cursor: pointer; border-radius: 4px; }
</style>
