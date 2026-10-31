<template>
  <div class="layout">
    <router-view v-slot="{ Component }">
      <keep-alive :include="['Home']">
        <component :is="Component" />
      </keep-alive>
    </router-view>

    <van-tabbar route fixed placeholder safe-area-inset-bottom active-color="#4f6ef7">
      <van-tabbar-item to="/" icon="wap-home-o">首页</van-tabbar-item>
      <van-tabbar-item to="/bills" icon="orders-o">账单</van-tabbar-item>
      <van-tabbar-item to="/ai" icon="chat-o">AI 助手</van-tabbar-item>
      <van-tabbar-item to="/stats" icon="bar-chart-o">统计</van-tabbar-item>
      <van-tabbar-item to="/mine" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
// 底部导航布局：五个一级页面共用
</script>

<style scoped>
.layout {
  min-height: 100vh;
}
</style>
