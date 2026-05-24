<script setup>
// 👇 1. 确保这里引入了所有需要的工具，一个都不能少！
import { ref, onMounted, watch, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router' // ⭐ 报错就是因为缺了这个 useRoute

const router = useRouter()
const route = useRoute() // ⭐ 报错就是指向了这一行
const nickname = ref('访客')
const role = ref('')

// 更新信息的独立函数
function updateUserInfo() {
  nickname.value = localStorage.getItem('userNickname') || '管理员'
  role.value = localStorage.getItem('userRole') || 'guest'
}

onMounted(() => {
  updateUserInfo()
  // 监听自定义事件，实现“登录后秒更新”
  window.addEventListener('storage-updated', updateUserInfo)
})

onUnmounted(() => {
  window.removeEventListener('storage-updated', updateUserInfo)
})

// 监听路由变化 (双重保险)
watch(route, () => {
  updateUserInfo()
})

function logout() {
  if (confirm("确定要退出当前账号吗？")) {
    localStorage.removeItem('userRole')
    localStorage.removeItem('userNickname')

    // 退出时也喊一声，把右上角清空
    window.dispatchEvent(new Event('storage-updated'))

    router.push('/login')
  }
}
</script>

<template>
  <div class="app-container">
    <<header v-if="!['login', 'register'].includes($route.name)" class="main-header">
      <div class="logo-area">
        <span class="rocket">🚀</span>
        <span class="app-name">Customer AI Biz</span>
      </div>

      <nav class="nav-links">
        <router-link to="/">驾驶舱</router-link>
        <router-link to="/customer">客户管理</router-link>
        <router-link to="/orders">订单中心</router-link>
        <router-link to="/interactions">交互工单</router-link>
        <router-link to="/products">商品管理 (AI库)</router-link>
      </nav>

      <div class="user-area">
        <span class="welcome-text">
          {{ nickname }}
          <span class="role-badge" :class="role">{{ role === 'admin' ? '管理员' : '客服' }}</span>
        </span>
        <div class="avatar-circle">{{ nickname[0] }}</div>
        <button class="logout-btn" @click="logout" title="退出登录">⏻</button>
      </div>
    </header>

    <main>
      <router-view />
    </main>
  </div>
</template>

<style>
/* 保持你之前的样式不变，这里为了篇幅省略了 style 内容 */
/* 如果你需要完整的 style，我可以再发一次，但只要不删下面的 style 就没事 */
body { margin: 0; font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', sans-serif; background-color: #f0f2f5; }

.main-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 0 30px; height: 60px;
  background-color: #001529; color: white;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}

.logo-area { font-size: 20px; font-weight: bold; display: flex; align-items: center; gap: 10px; }

/* 刚才调好的导航栏样式 */
.nav-links { display: flex; gap: 10px; height: 100%; }
.nav-links a {
  text-decoration: none; color: rgba(255,255,255,0.7); font-size: 14px;
  display: flex; align-items: center; padding: 0 15px;
  border-bottom: 3px solid transparent; transition: all 0.3s ease; position: relative;
}
.nav-links a:hover { color: #fff; transform: translateY(-3px); }
.nav-links a.router-link-active { color: #fff; font-weight: bold; border-bottom-color: #1890ff; transform: translateY(0); }

.user-area { display: flex; align-items: center; gap: 12px; }
.welcome-text { font-size: 14px; color: white; display: flex; flex-direction: column; align-items: flex-end; line-height: 1.2;}
.role-badge { font-size: 10px; background: #52c41a; padding: 1px 4px; border-radius: 2px; margin-top: 2px;}
.role-badge.admin { background: #1890ff; }

.avatar-circle {
  width: 32px; height: 32px; background: rgba(255,255,255,0.2); border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-weight: bold; font-size: 16px; color: white;
}

.logout-btn {
  background: transparent; border: 1px solid rgba(255,255,255,0.3);
  color: rgba(255,255,255,0.8);
  width: 32px; height: 32px; border-radius: 50%;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  font-size: 14px; transition: all 0.3s;
}
.logout-btn:hover { background: #ff4d4f; border-color: #ff4d4f; color: white; }
</style>
