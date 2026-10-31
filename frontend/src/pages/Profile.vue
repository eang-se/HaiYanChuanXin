<script setup lang="ts">
import {useRouter} from 'vue-router'
import {api,user,refreshAuth,notify,message} from '../api'
import ProfileForm from '../components/ProfileForm.vue'
import PenNamesPanel from '../components/PenNamesPanel.vue'
const router=useRouter()
async function logout(){try{await api.post('/auth/logout');await refreshAuth();router.push('/');notify('已退出登录')}catch(e){notify(message(e))}}
</script>
<template><div class="container page-space personal-center"><div class="section-heading"><div><p class="eyebrow">账号 / 个人资料</p><h1 class="page-title">个人中心</h1></div><RouterLink to="/account" class="text-button">我的书桌 →</RouterLink></div><div class="panel"><p class="quiet-note">账号：{{user?.username}} · {{({'SUPER_ADMIN':'超级管理员','ADMIN':'管理员','MEMBER':'社员','RESTRICTED':'受限社员'} as Record<string,string>)[user?.role||'']||'社员'}}</p><ProfileForm :key="user?.id"/></div><PenNamesPanel/><div class="profile-footer"><RouterLink v-if="['ADMIN','SUPER_ADMIN'].includes(user?.actorRole||user?.role||'')" to="/admin/settings" class="text-button">后台配置</RouterLink><button class="text-button" @click="logout">退出登录</button></div></div></template>
