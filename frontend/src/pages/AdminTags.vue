<script setup lang="ts">
import {computed,reactive,ref,onMounted} from 'vue'
import {api,message,notify,type Tag} from '../api'
import Icon from '../components/Icon.vue'
import AdminDialog from '../components/AdminDialog.vue'
import {isPersonalAdmin} from '../composables/admin'
const deleting=ref<Tag|null>(null),deleteBusy=ref(false),deleteError=ref('')
const tags=ref<Tag[]>([]),view=ref('activities'),search=ref(''),error=ref(''),formError=ref(''),loading=ref(true),saving=ref(false),updating=ref<number[]>([])
const form=reactive({name:'',description:''})
const activities=computed(()=>tags.value.filter(t=>t.isActivity)),content=computed(()=>tags.value.filter(t=>!t.isActivity&&t.name!=='海燕精选'))
const filtered=computed(()=>(view.value==='activities'?activities.value:content.value).filter(t=>`${t.name} ${t.description}`.includes(search.value.trim())))
async function load(){loading.value=true;error.value='';try{tags.value=(await api.get('/public/tags')).data}catch(e){error.value=message(e)}finally{loading.value=false}}
async function create(){if(saving.value)return;saving.value=true;formError.value='';try{await api.post('/admin/tags',{...form,name:form.name.trim(),isActivity:true});form.name='';form.description='';notify('活动已创建并开放参与');await load()}catch(e){formError.value=message(e)}finally{saving.value=false}}
async function toggle(t:Tag){if(updating.value.includes(t.id))return;updating.value.push(t.id);error.value='';try{const active=t.activityActive===false;await api.put(`/admin/activities/${t.id}/availability`,{active});notify(active?'活动已重新开放':'活动已结束，历史作品保留');await load()}catch(e){error.value=message(e)}finally{updating.value=updating.value.filter(id=>id!==t.id)}}
onMounted(load)
function askDelete(tag:Tag){deleting.value=tag;deleteError.value=''}
async function deleteActivity(){if(!deleting.value||deleteBusy.value)return;deleteBusy.value=true;deleteError.value='';try{await api.delete(`/admin/activities/${deleting.value.id}`);deleting.value=null;notify('活动已删除，文章内容保留');await load()}catch(e){deleteError.value=message(e)}finally{deleteBusy.value=false}}
</script>
<template>
  <header class="admin-page-heading"><div><p class="admin-kicker">ACTIVITIES & CONTENT TAGS</p><h1>活动与标签</h1><p>活动由社务台维护，内容标签由作者自由填写。</p></div><button class="button secondary small" :disabled="loading" @click="load"><Icon name="RefreshCw" :size="15"/>刷新列表</button></header>
  <div class="admin-subnav"><button :class="{selected:view==='activities'}" @click="view='activities'">征稿活动<span>{{activities.length}}</span></button><button :class="{selected:view==='content'}" @click="view='content'">内容标签<span>{{content.length}}</span></button></div>
  <div class="admin-info"><Icon :name="view==='activities'?'Feather':'Hash'" :size="20"/><p>{{view==='activities'?'只有开放中的活动可供作者选择。结束活动后，已有作品仍保留活动标记，作者无法新增参与。':'内容标签由作者在投稿时输入和编辑，同名标签自动归并；不会因为与活动同名而自动参加活动。海燕精选由文章管理页单独维护。'}}</p></div>
  <div class="admin-tags-layout" :class="{'admin-tags-browse':view==='content'}"><section>
    <label class="admin-search-field"><Icon name="Search" :size="17"/><input v-model="search" :placeholder="view==='activities'?'搜索征稿活动':'搜索作者内容标签'" aria-label="搜索活动或标签"/></label>
    <p v-if="error" class="error-message" role="alert">{{error}}</p><p v-if="loading" class="admin-empty">正在加载列表…</p>
    <div v-else class="admin-tag-list"><article v-for="t in filtered" :key="t.id" class="admin-surface"><div><h3>{{t.isActivity?'':'# '}}{{t.name}}</h3><span class="admin-badge" :class="t.isActivity?(t.activityActive===false?'withdrawn':'published'):''">{{t.isActivity?(t.activityActive===false?'已结束':'开放中'):'作者标签'}}</span></div><p>{{t.description||(t.isActivity?'暂无活动说明':'作者填写的内容关键词')}}</p><footer><span>{{t.workCount}} 篇公开作品</span><button v-if="t.isActivity" class="admin-inline-link" :disabled="updating.includes(t.id)" @click="toggle(t)">{{t.activityActive===false?'重新开放':'结束活动'}}</button><button v-if="t.isActivity&&isPersonalAdmin()" class="admin-inline-link admin-danger-link" :disabled="updating.includes(t.id)||deleteBusy" @click="askDelete(t)">删除活动</button><RouterLink :to="`/explore?tag=${t.id}`" class="admin-inline-link">查看作品 <Icon name="ArrowUpRight" :size="14"/></RouterLink></footer></article><div v-if="!filtered.length" class="admin-empty"><Icon name="Hash" :size="28"/><h3>{{view==='activities'?'暂无匹配活动':'暂无匹配内容标签'}}</h3></div></div>
  </section><form v-if="view==='activities'" class="admin-surface admin-form admin-sticky-form" @submit.prevent="create"><div class="admin-section-title"><div><h2>创建征稿活动</h2><p>发布后进入作者的可选活动列表。</p></div><Icon name="Plus" :size="20"/></div><p v-if="formError" class="error-message" role="alert">{{formError}}</p><label>活动名称<input v-model="form.name" maxlength="30" required placeholder="如：秋日来信"/></label><label>征稿说明<textarea v-model="form.description" maxlength="300" rows="5" placeholder="写下活动主题与征稿说明。"></textarea></label><footer class="admin-form-actions"><button class="button" :disabled="saving||!form.name.trim()">{{saving?'创建中…':'创建并开放活动'}}</button></footer></form></div>
  <AdminDialog v-if="deleting" title="删除活动" :busy="deleteBusy" @close="deleting=null"><div class="admin-confirm-copy"><h3>{{deleting.name}}</h3><p>删除后，该活动将从列表中移除，稿件中的对应活动标记也会移除。文章内容保留，此操作无法恢复。</p></div><p v-if="deleteError" class="error-message" role="alert">{{deleteError}}</p><footer class="admin-form-actions"><button class="button secondary" :disabled="deleteBusy" @click="deleting=null">取消</button><button class="button admin-danger-button" :disabled="deleteBusy" @click="deleteActivity">{{deleteBusy?'删除中…':'确认删除活动'}}</button></footer></AdminDialog>
</template>
<style scoped>.admin-tags-browse{grid-template-columns:1fr}.admin-tag-list article>footer{gap:12px;flex-wrap:wrap}</style>
