<script setup lang="ts">
import { ref,watch,onMounted } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { user,api,refreshAuth,notification,notify,message,siteName } from './api'
import Icon from './components/Icon.vue'
import Avatar from './components/Avatar.vue'
import ProfileForm from './components/ProfileForm.vue'
const route=useRoute(),router=useRouter(),menu=ref(false),profileReminder=ref(false),reminderBusy=ref(false),reminderError=ref('')
watch(()=>route.fullPath,()=>{menu.value=false})
watch(()=>[user.value?.id,user.value?.profileCompleted,user.value?.profilePromptSeen],()=>{profileReminder.value=!!user.value&&!user.value.profileCompleted&&!user.value.profilePromptSeen},{immediate:true})
onMounted(async()=>{try{siteName.value=(await api.get('/public/site')).data.name}catch{}})
async function skipReminder(){reminderBusy.value=true;reminderError.value='';try{await api.post('/member/profile/reminder-dismiss');await refreshAuth();profileReminder.value=false}catch(e){reminderError.value=message(e)}finally{reminderBusy.value=false}}
async function logout(){try{await api.post('/auth/logout');await refreshAuth();router.push('/');notify('已退出登录')}catch(e){notify(message(e))}}
</script>
<template><div class="site-shell"><header class="site-header"><div class="header-inner">
<RouterLink to="/" class="brand" @click="menu=false"><svg viewBox="0 0 48 48" aria-hidden="true"><path d="M5 13 25 22 43 7 32 27 39 39 23 32 11 42 16 25Z" fill="currentColor"/></svg><span>{{siteName}}<small>HAIYAN · LITERARY SOCIETY</small></span></RouterLink>
<nav :class="{open:menu}" aria-label="主导航"><RouterLink to="/" exact-active-class="active" @click="menu=false">首页</RouterLink><RouterLink to="/explore" active-class="active" @click="menu=false">作品书架</RouterLink><RouterLink to="/collections" active-class="active" @click="menu=false">合集</RouterLink><RouterLink to="/activities" active-class="active" @click="menu=false">征稿与活动</RouterLink><RouterLink v-if="user" to="/account" active-class="active" @click="menu=false">我的书桌</RouterLink><RouterLink v-if="user?.role==='ADMIN'" to="/admin" active-class="active" @click="menu=false">社务台</RouterLink></nav>
<div class="header-actions"><RouterLink to="/write" class="button small"><Icon name="Feather" :size="16"/><span>写一篇</span></RouterLink><RouterLink v-if="!user" to="/login" class="login-link">登录 / 注册</RouterLink><RouterLink v-else to="/profile" class="profile-entry" title="个人中心" aria-label="个人中心"><Avatar :src="user.avatarUrl" :name="user.penName" :size="35"/></RouterLink><button class="icon-button mobile-menu" :aria-expanded="menu" aria-label="打开导航" @click="menu=!menu"><Icon :name="menu?'X':'Menu'"/></button></div></div></header>
<main :key="route.path" class="main-content"><RouterView /></main><footer class="site-footer"><div><span class="footer-brand">{{siteName}}</span><span>校内文学社投稿与阅读平台</span></div><div><RouterLink to="/collections" active-class="active" @click="menu=false">合集</RouterLink><RouterLink to="/activities">征稿活动</RouterLink><span>校园文学 · 自由表达</span></div></footer><Transition name="toast"><div v-if="notification" class="toast" role="status">{{notification}}</div></Transition><div v-if="profileReminder" class="modal-backdrop"><section class="profile-modal" role="dialog" aria-modal="true" aria-labelledby="profile-reminder-title"><h2 id="profile-reminder-title">完善个人资料</h2><p class="quiet-note">可设置笔名和头像，暂不设置将以账号名展示。</p><ProfileForm @saved="profileReminder=false"/><p v-if="reminderError" class="error-message">{{reminderError}}</p><button class="text-button" :disabled="reminderBusy" @click="skipReminder">稍后设置</button></section></div></div></template>
