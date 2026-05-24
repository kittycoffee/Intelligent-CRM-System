<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const orderList = ref([])
const stats = ref({ totalCount: 0, totalRevenue: 0 })
const loading = ref(true)
const filterStatus = ref('all') // 筛选状态：all, completed, pending

onMounted(() => {
  fetchOrders()
})

async function fetchOrders() {
  try {
    const res = await axios.get('/api/order/list')

    // 兼容处理：如果你用的是我刚才给的 OrderController，返回的是直接的 Array
    if (Array.isArray(res.data)) {
      orderList.value = res.data
      // 前端计算统计数据
      const totalRev = res.data.reduce((sum, item) => sum + (Number(item.orderAmount) || 0), 0)
      stats.value = {
        totalCount: res.data.length,
        totalRevenue: totalRev.toFixed(2) // 保留两位小数
      }
    } else {
      // 兼容之前的对象包裹格式
      orderList.value = res.data.items || []
      stats.value = {
        totalCount: res.data.totalCount || 0,
        totalRevenue: res.data.totalRevenue || 0
      }
    }
  } catch (e) {
    console.error(e)
    alert("加载订单失败，请检查后端是否启动")
  } finally {
    loading.value = false
  }
}

// ⭐ 新增：标记订单为已完成
async function markAsFinished(item) {
  if (!confirm(`确认将订单 ${item.orderId} 标记为已完成吗？`)) return

  try {
    await axios.post(`/api/order/finish/${item.orderId}`)
    // 成功后，直接更新前端数据状态，不用刷新页面
    item.orderStatus = 1
    alert("操作成功！")
  } catch (e) {
    console.error(e)
    alert("操作失败，请检查网络或后端接口")
  }
}

function formatTime(t) { return t ? t.replace('T', ' ') : '' }

// 跳转到客户详情页
function goToCustomer(custId) {
  router.push({ path: '/customer/detail', query: { custId } })
}

// 前端筛选逻辑
const filteredList = computed(() => {
  if (filterStatus.value === 'all') return orderList.value
  if (filterStatus.value === 'completed') return orderList.value.filter(o => o.orderStatus === 1)
  if (filterStatus.value === 'pending') return orderList.value.filter(o => o.orderStatus !== 1)
  return orderList.value
})
</script>

<template>
  <div class="page-container">

    <div class="header-section">
      <h2>📦 订单管理中心</h2>
      <div class="stats-bar">
        <div class="stat-item">
          <span>总订单数</span>
          <strong>{{ stats.totalCount }} 单</strong>
        </div>
        <div class="stat-item highlight">
          <span>总营收</span>
          <strong>¥ {{ stats.totalRevenue }}</strong>
        </div>
      </div>
    </div>

    <div class="toolbar">
      <div class="filters">
        <button :class="{ active: filterStatus === 'all' }" @click="filterStatus = 'all'">全部</button>
        <button :class="{ active: filterStatus === 'completed' }" @click="filterStatus = 'completed'">✅ 已完成</button>
        <button :class="{ active: filterStatus === 'pending' }" @click="filterStatus = 'pending'">⏳ 进行中</button>
      </div>
    </div>

    <div v-if="loading" class="loading">正在加载数据...</div>

    <table v-else class="order-table">
      <thead>
      <tr>
        <th>订单号</th>
        <th>客户姓名</th>
        <th>购买商品</th>
        <th>单价</th>
        <th>数量</th>
        <th>总金额</th>
        <th>下单时间</th>
        <th>状态</th>
        <th style="width: 180px;">操作</th> </tr>
      </thead>
      <tbody>
      <tr v-for="order in filteredList" :key="order.orderId">
        <td class="order-id">{{ order.orderId }}</td>
        <td>
            <span class="cust-link" @click="goToCustomer(order.custId)">
              {{ order.custName }} ↗
            </span>
        </td>

        <td style="font-weight: bold; color: #333;">{{ order.productName }}</td>
        <td>¥{{ order.unitPrice }}</td>
        <td style="text-align: center;">x {{ order.quantity }}</td>
        <td class="price">¥{{ order.orderAmount }}</td>

        <td style="font-size: 13px; color: #666;">{{ formatTime(order.orderDate) }}</td>
        <td>
            <span :class="['status-tag', order.orderStatus === 1 ? 'done' : 'doing']">
              {{ order.orderStatus === 1 ? '交易成功' : '处理中' }}
            </span>
        </td>
        <td>
          <button
            v-if="order.orderStatus === 0"
            class="action-btn finish"
            @click="markAsFinished(order)"
          >
            ✅ 确认完成
          </button>

          <span v-else class="status-text-gray">
            已归档
          </span>

          <button class="action-btn" style="margin-left: 8px;" @click="goToCustomer(order.custId)">查看客户</button>
        </td>
      </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.page-container {
  width: 98%;
  max-width: 100%;
  margin: 20px auto;
  padding: 20px;
  color: #333;
}
/* 头部统计 */
.header-section { display: flex; justify-content: space-between; align-items: center; margin-bottom: 25px; }
.stats-bar { display: flex; gap: 20px; }
.stat-item { background: white; padding: 10px 25px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.05); text-align: center; }
.stat-item span { font-size: 12px; color: #666; display: block; margin-bottom: 4px; }
.stat-item strong { font-size: 20px; color: #333; }
.stat-item.highlight strong { color: #f56c6c; }

/* 筛选栏 */
.toolbar { margin-bottom: 20px; }
.filters button { border: none; background: #f0f2f5; padding: 6px 16px; margin-right: 10px; border-radius: 4px; cursor: pointer; color: #666; transition: all 0.2s;}
.filters button.active { background: #42b883; color: white; font-weight: bold; }

/* 表格样式 */
.order-table { width: 100%; border-collapse: collapse; background: white; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.05); }
th, td { padding: 12px 15px; text-align: left; border-bottom: 1px solid #eee; }
th { background: #fafafa; font-weight: bold; color: #000; }
.price { color: #d9001b; font-weight: bold; }
.order-id { font-family: monospace; color: #999; font-size: 12px; }

/* 链接与标签 */
.cust-link { color: #409eff; cursor: pointer; font-weight: bold; }
.cust-link:hover { text-decoration: underline; }
.status-tag { padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }
.status-tag.done { background: #f0f9eb; color: #67c23a; }
.status-tag.doing { background: #fdf6ec; color: #e6a23c; }
.status-text-gray { color: #999; font-size: 12px; cursor: default; }

/* 按钮样式 */
.action-btn { padding: 5px 12px; border: 1px solid #dcdfe6; background: white; border-radius: 4px; cursor: pointer; font-size: 12px; transition: all 0.2s; }
.action-btn:hover { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }

/* ⭐ 新增：完成按钮样式 */
.action-btn.finish {
  background-color: #67c23a; /* 绿色 */
  color: white;
  border: 1px solid #67c23a;
}
.action-btn.finish:hover {
  background-color: #85ce61;
  border-color: #85ce61;
  color: white;
  transform: translateY(-1px);
}
</style>
