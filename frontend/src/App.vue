<script setup lang="ts">
import {canManage} from './composables/admin'
import { computed,ref,watch,onMounted,onBeforeUnmount,provide } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { user,api,refreshAuth,notification,notify,message,siteName } from './api'
import Icon from './components/Icon.vue'
import IdentityPicker from './components/IdentityPicker.vue'
import ProfileForm from './components/ProfileForm.vue'
import NewDraftDialog from './components/NewDraftDialog.vue'
import {setNewDraftSetup,takeNewDraftSetup,openDraftKey,type NewDraftSetup} from './article/newDraft'
import DraftLink from './components/DraftLink.vue'
const route=useRoute(),router=useRouter(),menu=ref(false),profileReminder=ref(false),reminderBusy=ref(false),reminderError=ref('')
const brandParts=computed(()=>{const separator=siteName.value.indexOf(' - ');return separator<0?{name:siteName.value,subtitle:'HAIYAN · LITERARY SOCIETY'}:{name:siteName.value.slice(0,separator),subtitle:siteName.value.slice(separator+3)||'HAIYAN · LITERARY SOCIETY'}})
watch(siteName,name=>{document.title=name},{immediate:true})
const newDraftOpen=ref(false),draftTag=ref<number|undefined>()
function openDraft(tag?:number){draftTag.value=tag;newDraftOpen.value=true}
provide(openDraftKey,openDraft)
function startDraft(value:NewDraftSetup){setNewDraftSetup(value);newDraftOpen.value=false;void router.push({path:'/write',query:draftTag.value?{tag:draftTag.value}:undefined})}
const pageRefreshedAt=new Date()
const traffic=ref<{online:number;visitors:number}|null>(null),trafficUnavailable=ref(false)
let trafficTimer:ReturnType<typeof setInterval>|undefined,trafficBusy=false,disposed=false
async function heartbeat(){if(disposed||document.visibilityState==='hidden'||trafficBusy)return;trafficBusy=true
try{const {data}=await api.post('/public/traffic');if(!disposed){traffic.value=data;trafficUnavailable.value=false}}catch{if(!disposed)trafficUnavailable.value=true}finally{trafficBusy=false}}
function resumeTraffic(){if(document.visibilityState==='visible')void heartbeat()}
const pageRefreshedText=new Intl.DateTimeFormat('zh-CN',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(pageRefreshedAt)
watch(()=>route.fullPath,(_next,previous)=>{menu.value=false;if(previous?.startsWith('/login')&&route.path!=='/write')takeNewDraftSetup()})
watch(()=>[user.value?.id,user.value?.profileCompleted,user.value?.profilePromptSeen],()=>{profileReminder.value=!!user.value&&!user.value.profileCompleted&&!user.value.profilePromptSeen},{immediate:true})
onMounted(async()=>{try{siteName.value=(await api.get('/public/site')).data.name}catch{}})
onMounted(()=>{void heartbeat();trafficTimer=setInterval(()=>void heartbeat(),30000);document.addEventListener('visibilitychange',resumeTraffic)})
onBeforeUnmount(()=>{disposed=true;clearInterval(trafficTimer);document.removeEventListener('visibilitychange',resumeTraffic)})
async function skipReminder(){reminderBusy.value=true;reminderError.value='';try{await api.post('/member/profile/reminder-dismiss');await refreshAuth();profileReminder.value=false}catch(e){reminderError.value=message(e)}finally{reminderBusy.value=false}}
async function logout(){try{await api.post('/auth/logout');await refreshAuth();router.push('/');notify('已退出登录')}catch(e){notify(message(e))}}
</script>
<template><div class="site-shell"><header class="site-header"><div class="header-inner">
<RouterLink to="/" class="brand" :aria-label="siteName" @click="menu=false"><img src="/brand/haiyan-bird.svg" alt="" aria-hidden="true"/><span>{{brandParts.name}}<small>{{brandParts.subtitle}}</small></span></RouterLink>
<nav :class="{open:menu}" aria-label="主导航"><RouterLink to="/" exact-active-class="active" @click="menu=false">首页</RouterLink><RouterLink to="/explore" active-class="active" @click="menu=false">文库</RouterLink><RouterLink to="/activities" active-class="active" @click="menu=false">征稿与活动</RouterLink><RouterLink to="/about" active-class="active" @click="menu=false">关于</RouterLink><RouterLink v-if="user" to="/account" active-class="active" @click="menu=false">我的书桌</RouterLink><RouterLink v-if="canManage('overview')" to="/admin" active-class="active" @click="menu=false">社务台</RouterLink></nav>
<div class="header-actions"><DraftLink class="button small"><Icon name="Feather" :size="16"/><span>写一篇</span></DraftLink><RouterLink v-if="!user" to="/login" class="login-link">登录 / 注册</RouterLink><RouterLink v-if="!user" to="/login" class="guest-avatar" aria-label="登录或注册"><Icon name="UserRound" :size="19"/></RouterLink><IdentityPicker v-else/><button class="icon-button mobile-menu" :aria-expanded="menu" aria-label="打开导航" @click="menu=!menu"><Icon :name="menu?'X':'Menu'"/></button></div></div></header>
<main :key="route.path" class="main-content"><RouterView /></main><footer class="site-footer"><nav class="footer-links" aria-label="页脚导航"><RouterLink to="/collections" @click="menu=false">合集</RouterLink><RouterLink v-if="canManage('overview')" to="/admin" @click="menu=false">社务台</RouterLink><RouterLink to="/about">关于</RouterLink></nav><div class="footer-identity"><div class="footer-title"><img src="/brand/haiyan-bird.svg" alt="" aria-hidden="true"/><span>{{siteName}}</span></div><p class="footer-refresh">最后刷新时间：<time :datetime="pageRefreshedAt.toISOString()">{{pageRefreshedText}}</time></p><p class="footer-traffic" title="在线人数：最近 90 秒内活跃的浏览器；累计访问人数：自统计启用起按浏览器去重，刷新和多标签页不重复计数。"><span><i class="footer-online-dot" aria-hidden="true"></i>实时在线 <strong>{{traffic?.online.toLocaleString()??'—'}}</strong> 人</span><span>累计访问 <strong>{{traffic?.visitors.toLocaleString()??'—'}}</strong> 人次</span><small v-if="trafficUnavailable" class="footer-traffic-unavailable">统计暂不可用</small></p><div class="footer-maintainers"><span>运营维护：</span><img class="footer-seal" src="/brand/haiyan-seal.webp" width="192" height="192" loading="lazy" decoding="async" alt="海燕文学社标志"/><span class="footer-tech-window" role="img" aria-label="技术部标志"><img src="/brand/tech-department.webp" width="256" height="256" loading="lazy" decoding="async" alt="" aria-hidden="true"/></span><span>海燕文学社技术部</span></div></div></footer><nav v-if="!route.path.startsWith('/write')" class="mobile-tabbar" aria-label="快捷导航">
<RouterLink to="/" exact-active-class="selected"><Icon name="House" :size="21"/><span>首页</span></RouterLink>
<RouterLink to="/explore" active-class="selected"><Icon name="BookOpen" :size="21"/><span>文库</span></RouterLink>
<button type="button" class="mobile-compose" aria-label="写作" @click="openDraft()"><span><Icon name="Plus" :size="23"/></span></button>
<RouterLink to="/activities" active-class="selected"><Icon name="Sparkles" :size="21"/><span>活动</span></RouterLink>
<RouterLink :to="user?'/account':'/login'" active-class="selected"><Icon name="UserRound" :size="21"/><span>我的</span></RouterLink>
</nav><NewDraftDialog v-if="newDraftOpen" @close="newDraftOpen=false" @start="startDraft"/><Transition name="toast"><div v-if="notification" class="toast" role="status">{{notification}}</div></Transition><div v-if="profileReminder" class="modal-backdrop"><section class="profile-modal" role="dialog" aria-modal="true" aria-labelledby="profile-reminder-title"><h2 id="profile-reminder-title">完善个人资料</h2><p class="quiet-note">可设置账号昵称，头像使用注册时的 QQ 号。</p><ProfileForm @saved="profileReminder=false"/><p v-if="reminderError" class="error-message">{{reminderError}}</p><button class="text-button" :disabled="reminderBusy" @click="skipReminder">稍后设置</button></section></div></div></template>
<style scoped>
.brand small{font-size:10px;letter-spacing:.4px;line-height:1.45}
.footer-title span{overflow-wrap:anywhere}
@media(max-width:800px){.brand small{font-size:9px;letter-spacing:0}}
</style>
