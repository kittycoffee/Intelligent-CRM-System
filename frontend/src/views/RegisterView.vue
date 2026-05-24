<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()

const form = ref({
  username: '',
  password: '',
  nickname: ''
})

const loading = ref(false)
const errorMsg = ref('')

async function doRegister() {
  if (!form.value.username || !form.value.password) {
    errorMsg.value = '账号和密码不能为空'
    return
  }

  loading.value = true
  errorMsg.value = ''

  try {
    const res = await axios.post('/api/auth/register', form.value)
    if (res.data.code === 200) {
      alert('注册成功！即将跳转到登录页')
      router.push('/login')
    } else {
      errorMsg.value = res.data.msg // 例如“账号已存在”
    }
  } catch (e) {
    errorMsg.value = '网络异常，请稍后重试'
  } finally {
    loading.value = false
  }
}

</script>

<template>
  <div class="register-container">
    <div class="register-card">
      <h2>📝 注册新账号</h2>
      <p class="sub-text">注册后自动获得普通用户权限</p>

      <div class="form-group">
        <label>账号</label>
        <input v-model="form.username" placeholder="请输入账号" />
      </div>

      <div class="form-group">
        <label>密码</label>
        <input v-model="form.password" type="password" placeholder="请输入密码" />
      </div>

      <div class="form-group">
        <label>昵称（可选）</label>
        <input v-model="form.nickname" placeholder="起一个好听的昵称" />
      </div>

      <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>

      <button class="register-btn" @click="doRegister" :disabled="loading">
        {{ loading ? '注册中...' : '立即注册' }}
      </button>

      <p class="login-link">
        已有账号？<router-link to="/login">去登录</router-link>
      </p>
    </div>
  </div>
</template>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, #1f4037, #99f2c8);  /* 只改了这行 */
}
.register-card {
  background: white;
  padding: 40px 30px;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.08);
  width: 400px;
}
h2 { margin: 0 0 5px 0; text-align: center; }
.sub-text { text-align: center; color: #999; margin-bottom: 25px; }
.form-group { margin-bottom: 18px; }
label { display: block; font-weight: bold; margin-bottom: 5px; }
input { width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 6px; box-sizing: border-box; }
.error-msg { color: #f56c6c; font-size: 14px; margin: 10px 0; text-align: center; }
.register-btn {
  width: 100%; padding: 12px; background: #409eff; color: white;
  border: none; border-radius: 6px; font-size: 16px; cursor: pointer;
  margin-top: 10px;
}
.register-btn:disabled { background: #a0cfff; cursor: not-allowed; }
.login-link { text-align: center; margin-top: 15px; font-size: 14px; }
.login-link a { color: #409eff; text-decoration: none; }
</style>
