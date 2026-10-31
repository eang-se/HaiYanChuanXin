<script setup lang="ts">
import {ref,computed,onMounted} from 'vue'
import {api,user,refreshAuth,message,notify,date,type Work} from '../api'
import WorkCard from '../components/WorkCard.vue'
import WorkRow from '../components/WorkRow.vue'
import WorkFeed from '../components/WorkFeed.vue'
import LayoutSwitch from '../components/LayoutSwitch.vue'
import {feedLayout} from '../composables/feedLayout'
import Icon from '../components/Icon.vue'
import ProfileForm from '../components/ProfileForm.vue'
import PenNamesPanel from '../components/PenNamesPanel.vue'
const works=ref<Work[]>([]),bookmarks=ref<Work[]>([]),tab=ref('全部稿件'),error=ref(''),pen=ref(user.value?.penName||''),bio=ref(user.value?.bio||'')
const deleting=ref<number|null>(null)
const filtered=computed(()=>works.value.filter(w=>tab.value==='草稿箱'?w.status!=='PUBLISHED':tab.value==='已发布'?w.status==='PUBLISHED':true))
async function load(){try{const [w,b]=await Promise.all([api.get('/member/works'),api.get('/member/bookmarks')]);works.value=w.data;bookmarks.value=b.data}catch(e){error.value=message(e)}}
async function withdraw(id:number){if(!confirm('撤回后作品将不再公开展示，可以再次编辑发布。确认撤回？'))return;try{await api.post(`/member/works/${id}/withdraw`);await load();notify('作品已撤回')}catch(e){notify(message(e))}}
async function deleteDraft(work:Work){
  if(deleting.value!==null)return
  const label=work.status==='DRAFT'?'草稿':'稿件'
  if(!confirm(`永久删除${label}《${work.title||'未命名稿件'}》？此操作无法恢复。`))return
  deleting.value=work.id;error.value=''
  try{await api.delete(`/member/works/${work.id}`);works.value=works.value.filter(w=>w.id!==work.id);notify(`${label}已删除`)}
  catch(e){error.value=`删除失败：${message(e)}`;notify(error.value)}finally{deleting.value=null}
}
async function profile(){try{await api.put('/member/profile',{penName:pen.value,bio:bio.value});await refreshAuth();notify('个人资料已更新')}catch(e){notify(message(e))}}
onMounted(load)
</script>
<style scoped>
.row-actions .draft-delete{color:#98624e;min-height:36px;padding:6px 0}
</style>
<template><div class="container page-space"><div class="desk-heading"><div><p class="eyebrow">MY WRITING DESK</p><h1 class="page-title">{{user?.penName}}的书桌</h1><p class="page-description">管理稿件、合集与收藏。</p></div><RouterLink to="/write" class="button"><Icon name="Plus"/>新建作品</RouterLink></div><div class="desk-collection-links"><RouterLink v-if="user?.accountType!=='PUBLIC'" to="/profile" class="text-button">个人中心 →</RouterLink><RouterLink to="/collection-edit" class="text-button">＋ 新建合集</RouterLink><RouterLink to="/collections?mine=1" class="text-button">我的合集 →</RouterLink></div><div class="desk-stats"><div><strong>{{works.length}}</strong><span>全部稿件</span></div><div><strong>{{works.filter(w=>w.status==='PUBLISHED').length}}</strong><span>已发布</span></div><div><strong>{{bookmarks.length}}</strong><span>收藏的作品</span></div></div><div class="genre-tabs desk-tabs"><button v-for="t in user?.accountType==='PUBLIC'?['全部稿件','草稿箱','已发布','我的收藏']:['全部稿件','草稿箱','已发布','我的收藏','个人资料']" :key="t" :class="{selected:tab===t}" @click="tab=t">{{t}}</button></div><LayoutSwitch v-if="tab!=='个人资料'"/><p v-if="error" class="error-message" role="alert">{{error}}</p><section v-if="tab==='个人资料'" class="panel profile-form"><h2>个人资料</h2><ProfileForm :key="user?.id"/><PenNamesPanel/></section><template v-else-if="tab==='我的收藏'"><WorkFeed :works="bookmarks"/><div v-if="!bookmarks.length" class="empty-state">暂无收藏</div></template><template v-else><div :class="feedLayout==='rows'?'works-rows':'works-grid'"><div v-for="(w,i) in filtered" :key="w.id" class="desk-work-item"><component :is="feedLayout==='rows'?WorkRow:WorkCard" :work="w" :index="i"/><div class="desk-work-controls"><span class="status-badge" :class="{published:w.status==='PUBLISHED'}">{{w.status==='PUBLISHED'?'已发布':w.status==='DRAFT'?'草稿':'已撤回'}}</span><div class="row-actions"><RouterLink :to="`/write/${w.id}`">编辑</RouterLink><button v-if="w.status==='PUBLISHED'" @click="withdraw(w.id)">撤回</button><button v-else-if="w.status==='DRAFT'||w.status==='WITHDRAWN'" class="draft-delete" :aria-label="`删除${w.status==='DRAFT'?'草稿':'稿件'}：${w.title||'未命名稿件'}`" :disabled="deleting!==null" @click="deleteDraft(w)">{{deleting===w.id?'删除中…':'删除'}}</button></div></div></div></div><div v-if="!filtered.length" class="empty-state"><Icon name="Feather" :size="30"/><h3>暂无稿件</h3><RouterLink to="/write" class="text-button">新建作品 →</RouterLink></div></template></div></template>
