import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import ResultView from "@/views/ResultView.vue";

// 1. 引入新页面
import RegisterView from '../views/RegisterView.vue'
import CustomerListView from '../views/CustomerListView.vue'
import CustomerDetailView from '../views/CustomerDetailView.vue'
import OrderListView from "@/views/OrderListView.vue";
import InteractionView from '../views/InteractionView.vue'
import ProductView from '../views/ProductView.vue' // 1. 引入组件
import LoginView from '../views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: LoginView }, // 1. 加登录页
    { path: '/register', name: 'register', component: RegisterView }, // 新增注册页面
    {
      path: '/',          // 意思：当网址是根目录时
      name: 'home',
      component: HomeView // 显示 HomeView 组件
    },
    {
      path: '/result',      // 访问这个网址时
      name: 'result',
      component: ResultView // 显示这个页面
    },
    // 2. 新增路由：访问 /customers 时显示列表页
    {
      path: '/customer',
      name: 'customer',
      component: CustomerListView
    },
    {
      path: '/customer/detail',
      name: 'customer-detail',
      component: CustomerDetailView
    },
    {
      path: '/orders',
      name: 'orders',
      component: OrderListView
    },
    {
      path: '/interactions',  // 浏览器地址栏会变成这个
      name: 'interactions',
      component: InteractionView // 对应上面引入的文件
    },
    // 👇👇👇 2. 新增这一段 👇👇👇
    {
      path: '/products',
      name: 'products',
      component: ProductView
    }
  ]
})

// 2. ⭐ 全局守卫：没登录就踢回登录页，让 /register 也不需要登录就能访问
router.beforeEach((to, from, next) => {
  const role = localStorage.getItem('userRole')
  // if (to.name !== 'login' && !role) {
  //   next({ name: 'login' }) // 没登录？去登录页！
  // } else {
  //   next() // 已登录，放行
  // }

  // 白名单：login 和 register
  if (to.name === 'login' || to.name === 'register' || role) {
    next()
  } else {
    next({ name: 'login' })
  }
})

export default router
