<script setup lang="ts">
import {ref,onMounted} from 'vue'
import {api,message,user,type Work} from '../api'
import Icon from '../components/Icon.vue'
import PermissionSummary from '../components/PermissionSummary.vue'
import {useAdminAccess,workStatusLabels,adminDate} from '../composables/admin'
const {isAdmin,canReview,canTag,canImport,canConfigure}=useAdminAccess()
const loading=ref(true),error=ref(''),accountCount=ref(0),restrictedCount=ref(0),tagCount=ref(0)
const summary=ref({total:0,published:0,draft:0,withdrawn:0,featured:0}),recent=ref<Work[]>([])
async function load(){
  loading.value=true;error.value=''
  try{await Promise.all([
    ...(isAdmin.value?[api.get('/admin/accounts').then(({data})=>{const personal=data.filter((m:{accountType:string})=>m.accountType!=='PUBLIC');accountCount.value=personal.length;restrictedCount.value=personal.filter((m:{role:string})=>m.role==='RESTRICTED').length})]:[]),
    ...(canReview.value?[api.get('/admin/works',{params:{pageSize:5}}).then(({data})=>{summary.value=data.summary;recent.value=data.items})]:[]),
    ...(canTag.value?[api.get('/public/tags').then(({data})=>tagCount.value=data.length)]:[]),
  ])}catch(e){error.value=message(e)}finally{loading.value=false}
}
onMounted(load)
</script>
<template>
  <header class="admin-page-heading"><div><p class="admin-kicker">WORKSPACE OVERVIEW</p><h1>工作概览</h1><p>{{user?.penName}}，欢迎回来。社务工作，从这里有序展开。</p></div><button class="button secondary small" :disabled="loading" @click="load"><Icon name="RefreshCw" :size="15"/>{{loading?'加载中…':'刷新数据'}}</button></header>
  <div v-if="error" class="error-message" role="alert">{{error}} <button @click="load">重新加载</button></div>
  <div class="admin-metrics" :aria-busy="loading">
    <RouterLink v-if="isAdmin" to="/admin/accounts" class="admin-metric"><span>个人账户 <Icon name="Users"/></span><strong>{{loading||error?'—':accountCount}}</strong><small>{{loading?'正在获取账户数据':`${restrictedCount} 个受限账户`}} <Icon name="ArrowRight" :size="14"/></small></RouterLink>
    <RouterLink v-if="canReview" to="/admin/works?status=PUBLISHED" class="admin-metric"><span>已公开文章 <Icon name="BookOpen"/></span><strong>{{loading||error?'—':summary.published}}</strong><small>查看公开内容 <Icon name="ArrowRight" :size="14"/></small></RouterLink>
    <RouterLink v-if="canReview" to="/admin/works?featured=true" class="admin-metric"><span>海燕精选 <Icon name="Sparkles"/></span><strong>{{loading||error?'—':summary.featured}}</strong><small>管理首页推荐 <Icon name="ArrowRight" :size="14"/></small></RouterLink>
    <RouterLink v-if="canTag" to="/admin/tags" class="admin-metric"><span>活动与标签 <Icon name="Hash"/></span><strong>{{loading||error?'—':tagCount}}</strong><small>整理创作主题 <Icon name="ArrowRight" :size="14"/></small></RouterLink>
  </div>
  <section class="admin-section"><div class="admin-section-title"><div><h2>管理入口</h2><p>按工作内容分区，各有归属。</p></div></div><div class="admin-entry-grid">
    <RouterLink v-if="isAdmin" to="/admin/accounts" class="admin-entry"><span class="admin-entry-icon"><Icon name="Users" :size="23"/></span><div><h3>账户管理</h3><p>个人账户、角色权限、公用身份授权。</p><small>进入账户管理 <Icon name="ArrowRight" :size="14"/></small></div></RouterLink>
    <RouterLink v-if="canReview" to="/admin/works" class="admin-entry"><span class="admin-entry-icon"><Icon name="FileText" :size="23"/></span><div><h3>文章管理 / 审核</h3><p>检索稿件、巡查正文、推荐精选与下架。</p><small>进入文章管理 <Icon name="ArrowRight" :size="14"/></small></div></RouterLink>
    <RouterLink v-if="canTag" to="/admin/tags" class="admin-entry"><span class="admin-entry-icon"><Icon name="Hash" :size="23"/></span><div><h3>活动与标签</h3><p>查看现有主题，创建标签与征稿活动。</p><small>管理活动标签 <Icon name="ArrowRight" :size="14"/></small></div></RouterLink>
    <RouterLink v-if="canImport" to="/admin/import" class="admin-entry"><span class="admin-entry-icon"><Icon name="Upload" :size="23"/></span><div><h3>批量导入</h3><p>批量解析文档，保存为编辑部草稿。</p><small>导入稿件 <Icon name="ArrowRight" :size="14"/></small></div></RouterLink>
    <RouterLink v-if="canConfigure" to="/admin/settings" class="admin-entry"><span class="admin-entry-icon"><Icon name="Settings" :size="23"/></span><div><h3>站点设置</h3><p>{{isAdmin?'站点资料、注册开关与 QQ 注册名单。':'维护网站名称与站点介绍。'}}</p><small>查看站点设置 <Icon name="ArrowRight" :size="14"/></small></div></RouterLink>
  </div></section>
  <section v-if="canReview" class="admin-section"><div class="admin-section-title"><div><h2>最近更新的文章</h2><p>自动审核后公开，编辑部可继续进行内容巡查。</p></div><RouterLink to="/admin/works" class="admin-inline-link">全部文章 <Icon name="ArrowRight" :size="15"/></RouterLink></div><div class="admin-surface"><p v-if="loading" class="admin-empty">正在加载文章…</p><p v-else-if="!recent.length" class="admin-empty">暂无文章，新的来信正在路上。</p><RouterLink v-for="w in recent" :key="w.id" :to="`/admin/works?work=${w.id}`" class="admin-recent"><Icon name="FileText" :size="19"/><div><strong>{{w.title||'未命名稿件'}}</strong><small>{{w.displayByline||w.authorName}} · {{adminDate(w.updatedAt)}}</small></div><span class="admin-badge" :class="w.status.toLowerCase()">{{workStatusLabels[w.status]||w.status}}</span><Icon name="ChevronRight" :size="16"/></RouterLink></div></section>
  <PermissionSummary/>
</template>
