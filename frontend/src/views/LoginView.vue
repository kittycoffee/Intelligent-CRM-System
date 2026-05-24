<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const form = ref({ username: '', password: '' })
const loading = ref(false)

async function handleLogin() {
  if (!form.value.username || !form.value.password) return alert("请输入账号密码")

  loading.value = true
  try {
    const res = await axios.post('/api/auth/login', form.value)

    if (res.data.code === 200) {
      // 1. 存数据
      localStorage.setItem('userRole', res.data.role)
      localStorage.setItem('userNickname', res.data.nickname)

      // ⭐ 核心修改：派发一个自定义事件，通知 App.vue 更新
      window.dispatchEvent(new Event('storage-updated'))

      alert(`欢迎回来，${res.data.nickname}！`)
      router.push('/')
    } else {
      alert(res.data.msg)
    }
  } catch (e) {
    alert("登录服务连接失败")
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container">
    <div class="login-box">
      <div class="title-area">
        <h1>🚀 Customer AI Biz</h1>
        <p>智能客户价值分析系统</p>
      </div>

      <div class="form-area">
        <input v-model="form.username" placeholder="请输入账号 (admin/kefu)" @keyup.enter="handleLogin">
        <input v-model="form.password" type="password" placeholder="请输入密码 (123456)" @keyup.enter="handleLogin">
        <button @click="handleLogin" :disabled="loading">
          {{ loading ? '登录中...' : '立即登录' }}
        </button>
      </div>

      <div class="tips">
        <p>测试账号：admin / 123456 (管理员)</p>
        <p>测试账号：kefu / 123456 (普通客服)</p>
      </div>
<!--      新增注册入口      -->
      <p class="register-link">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </p>
    </div>
  </div>
</template>

<style scoped>
.login-container { height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #1f4037, #99f2c8); }
.login-box { width: 400px; padding: 40px; background: white; border-radius: 12px; box-shadow: 0 10px 25px rgba(0,0,0,0.1); text-align: center; }
.title-area h1 { margin: 0 0 10px; color: #333; }
.title-area p { color: #666; margin-bottom: 30px; }
.form-area input { width: 100%; padding: 12px; margin-bottom: 15px; border: 1px solid #ddd; border-radius: 6px; box-sizing: border-box; }
.form-area button { width: 100%; padding: 12px; background: #409eff; color: white; border: none; border-radius: 6px; font-weight: bold; cursor: pointer; transition: 0.3s; }
.form-area button:hover { background: #66b1ff; }
.tips { margin-top: 20px; font-size: 12px; color: #999; text-align: left; background: #f9f9f9; padding: 10px; border-radius: 4px; }
.register-link {
  margin-top: 15px;
  font-size: 14px;
  color: #666;
}
.register-link a {
  color: #409eff;
  text-decoration: none;
  font-weight: bold;
}
.register-link a:hover {
  text-decoration: underline;
}
</style>
