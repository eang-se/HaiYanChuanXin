<script setup lang="ts">
import {ref,reactive,onMounted,computed} from 'vue'
import {api,message,notify,date,user,siteName} from '../api'
const roles:Record<string,string>={SUPER_ADMIN:'超级管理员',ADMIN:'管理员',MEMBER:'社员',RESTRICTED:'受限社员'}
const isAdmin=computed(()=>['ADMIN','SUPER_ADMIN'].includes(user.value?.actorRole||user.value?.role||''))
const isEditor=computed(()=>user.value?.accountType==='PUBLIC'&&user.value.username==='haiyan_editor')
const isActivity=computed(()=>user.value?.accountType==='PUBLIC'&&user.value.username==='haiyan_activity')
const isTech=computed(()=>user.value?.accountType==='PUBLIC'&&user.value.username==='haiyan_tech')
const tabs=computed(()=>[...(isAdmin.value?['社员名册']:[]),...(isAdmin.value||isEditor.value?['稿件管理']:[]),...(isEditor.value?['批量上传']:[]),...(isAdmin.value||isActivity.value?['活动标签']:[]),...(isTech.value?['网站维护']:[])])
const data=ref<any>({members:[],works:[]}),tab=ref(isAdmin.value?'社员名册':isEditor.value?'稿件管理':isTech.value?'网站维护':'活动标签'),error=ref(''),tag=reactive({name:'',description:'',isActivity:true}),busy=ref(false)
const site=reactive({siteName:'',siteDescription:'',registrationEnabled:true,registrationMode:'live'})
const batchFiles=ref<File[]>([]),batchResults=ref<{name:string;message:string;ok:boolean}[]>([]),batchBusy=ref(false)
async function load(){error.value='';try{data.value=(await api.get('/admin/overview')).data;if(isTech.value)Object.assign(site,(await api.get('/admin/settings')).data)}catch(e){error.value=message(e)}}
async function saveSite(){busy.value=true;error.value='';try{await api.put('/admin/settings',site);siteName.value=site.siteName;notify('网站资料已更新')}catch(e){error.value=message(e)}finally{busy.value=false}}
async function withdraw(id:number){if(!confirm('确认下架这篇作品？'))return;try{await api.post(`/admin/works/${id}/withdraw`);await load();notify('作品已下架')}catch(e){notify(message(e))}}
async function createTag(){busy.value=true;try{await api.post('/admin/tags',tag);tag.name='';tag.description='';notify('标签已创建')}catch(e){notify(message(e))}finally{busy.value=false}}
function selectFiles(event:Event){batchFiles.value=Array.from((event.target as HTMLInputElement).files||[]);batchResults.value=[]}
function escapeText(text:string){return text.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]||c))}
async function importBatch(){
 if(!batchFiles.value.length||batchBusy.value)return
 if(batchFiles.value.length>20){notify('一次最多上传 20 个文件');return}
 batchBusy.value=true;batchResults.value=[]
 try{for(const file of batchFiles.value){try{
  const data=new FormData();data.append('file',file)
  const imported=(await api.post('/member/import',data)).data
  const body=String(imported.text||'').split(/\n\s*\n/).map(p=>`<p>${escapeText(p).replace(/\n/g,'<br>')}</p>`).join('')
  const title=file.name.replace(/\.(docx|pdf|txt)$/i,'').slice(0,100)||'未命名稿件'
  await api.post('/member/works',{title,genre:'其他',excerpt:'',body,cover:'',tagIds:[],publish:false})
  batchResults.value.push({name:file.name,message:'已保存为草稿',ok:true})
 }catch(e){batchResults.value.push({name:file.name,message:message(e),ok:false})}}}finally{batchBusy.value=false;batchFiles.value=[]}
}
onMounted(load)
</script>
<template><div class="container page-space"><div class="section-heading"><div><p class="eyebrow">管理</p><h1 class="page-title">社务台</h1></div><RouterLink v-if="isAdmin" to="/admin/settings" class="button secondary">后台配置</RouterLink></div><div v-if="isAdmin" class="desk-stats"><div><strong>{{data.members.filter((m:any)=>m.accountType!=='PUBLIC').length}}</strong><span>社员</span></div><div><strong>{{data.works.filter((w:any)=>w.status==='PUBLISHED').length}}</strong><span>近期公开作品</span></div></div><div class="genre-tabs desk-tabs"><button v-for="t in tabs" :key="t" :class="{selected:tab===t}" @click="tab=t">{{t}}</button></div><p v-if="error" class="error-message">{{error}}</p><div v-if="tab==='社员名册'" class="table-wrap"><table><thead><tr><th>笔名</th><th>账号</th><th>身份</th><th>加入时间</th><th></th></tr></thead><tbody><tr v-for="m in data.members" :key="m.id"><td>{{m.penName}}</td><td>{{m.username}}</td><td>{{m.accountType==='PUBLIC'?'公用账号':(roles[m.role]||m.role)}}</td><td>{{date(m.createdAt)}}</td><td><RouterLink to="/admin/settings">配置权限</RouterLink></td></tr></tbody></table></div><div v-else-if="tab==='稿件管理'" class="table-wrap"><p class="quiet-note">展示最近 100 篇稿件；作品经自动审核后公开。</p><table><thead><tr><th>作品</th><th>署名 / 发布账号</th><th>状态</th><th></th></tr></thead><tbody><tr v-for="w in data.works" :key="w.id"><td><RouterLink v-if="w.status==='PUBLISHED'" :to="`/work/${w.id}`">{{w.title}}</RouterLink><span v-else>{{w.title||'未命名稿件'}}</span></td><td>{{w.displayByline}}<br/><small class="quiet-note">{{w.authorName}}</small></td><td>{{w.status==='PUBLISHED'?'公开':w.status==='DRAFT'?'草稿':'已撤回'}}</td><td><button v-if="w.status==='PUBLISHED'" @click="withdraw(w.id)">下架</button></td></tr></tbody></table></div><form v-else-if="tab==='批量上传'" class="panel profile-form" @submit.prevent="importBatch"><h2>批量上传稿件</h2><p class="quiet-note">支持 TXT、DOCX 和文字型 PDF，一次最多 20 个文件。文件解析后保存为编辑部草稿，可在“我的书桌”中修改和发布。</p><label>选择文件<input type="file" multiple accept=".txt,.docx,.pdf" :disabled="batchBusy" @change="selectFiles"/></label><button class="button" :disabled="batchBusy||!batchFiles.length">{{batchBusy?'正在处理…':`上传并保存草稿（${batchFiles.length}）`}}</button><p v-for="result in batchResults" :key="result.name" :class="result.ok?'quiet-note':'error-message'">{{result.name}}：{{result.message}}</p></form><form v-else-if="tab==='网站维护'" class="panel profile-form" @submit.prevent="saveSite"><h2>网站资料</h2><label>站点名称<input v-model="site.siteName" maxlength="80" required/></label><label>站点介绍<textarea v-model="site.siteDescription" maxlength="300" rows="3"></textarea></label><p class="quiet-note">注册与账号权限由管理员配置。</p><button class="button" :disabled="busy">保存</button></form><form v-else class="panel profile-form" @submit.prevent="createTag"><h2>新增标签或征稿主题</h2><label>名称<input v-model="tag.name" maxlength="30" required/></label><label>说明<textarea v-model="tag.description" maxlength="300" rows="3"></textarea></label><label class="checkbox-label"><input type="checkbox" v-model="tag.isActivity"/>同时展示在“征稿与活动”</label><button class="button" :disabled="busy">创建标签</button></form></div></template>
