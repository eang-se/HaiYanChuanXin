<script setup lang="ts">
import {ref,reactive,onMounted,onBeforeUnmount,watch,computed,nextTick} from 'vue'
import {useRoute,useRouter,onBeforeRouteLeave} from 'vue-router'
import {api,message,notify,genres,type Tag,type Work,type PenName,type CollectionSummary} from '../api'
import {createSaveQueue} from '../article/saveQueue'
import ArticleEditor from '../components/ArticleEditor.vue'
import ArticleLayoutMenu from '../components/ArticleLayoutMenu.vue'
import {defaultLayout,resolveLayout} from '../article/layout'
import Icon from '../components/Icon.vue'
const route=useRoute(),router=useRouter(),id=ref<number|undefined>(route.params.id?Number(route.params.id):undefined),tags=ref<Tag[]>([]),ready=ref(false),published=ref(false),busy=ref(false),importing=ref(false),uploading=ref(false),composing=ref(false),error=ref(''),saved=ref('尚未保存'),dirty=ref(false),count=ref(0),collections=ref<CollectionSummary[]>([]),penNames=ref<PenName[]>([])
const form=reactive({title:'',byline:'',sourceNote:'',notes:'',genre:'散文',customGenre:'',layout:defaultLayout('散文'),excerpt:'',cover:'',body:'',tagIds:[] as number[],collectionId:0,penNameId:0})
const fileInput=ref<HTMLInputElement|null>(null),articleEditor=ref<InstanceType<typeof ArticleEditor>|null>(null)
const preview=computed(()=>articleEditor.value?.preview||false)
const enqueue=createSaveQueue()
let timer:ReturnType<typeof setTimeout>|undefined,disposed=false,importTask:Promise<void>|null=null
function schedule(){clearTimeout(timer);if(!published.value&&!composing.value&&!disposed)timer=setTimeout(()=>void save(false,true),1800)}
function changed(){if(!ready.value)return;dirty.value=true;saved.value=published.value?'有未发布的修改':'等待保存…';schedule()}
watch(form,changed,{deep:true})
function compositionChanged(value:boolean){composing.value=value;if(value)clearTimeout(timer);else if(dirty.value)schedule()}
function genreChanged(){form.layout=defaultLayout(form.genre);if(form.genre!=='其他')form.customGenre=''}
function save(publish=false,automatic=false){
  clearTimeout(timer)
  if(busy.value)return Promise.resolve()
  busy.value=true
  return enqueue(async()=>{
    if(disposed)return
    if(importTask)await importTask
    const imagesReady=await articleEditor.value?.settle()
    await nextTick()
    if(automatic&&(published.value||!dirty.value||(!form.title&&!count.value)))return
    if(publish&&!imagesReady){error.value='图片尚未成功插入，请重试图片或取消本次图片后发布。正文可继续保存为草稿。';return}
    error.value=''
    const snapshot=JSON.stringify(form),payload=JSON.parse(snapshot)
    saved.value='正在保存…'
    try{
      const result=id.value?await api.put(`/member/works/${id.value}`,{...payload,publish}):await api.post('/member/works',{...payload,publish})
      id.value=result.data.id
      dirty.value=JSON.stringify(form)!==snapshot
      saved.value=dirty.value?'有新的修改待保存':`已保存 · ${new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})}`
      if(publish){
        published.value=true
        if(dirty.value){saved.value='已发布保存时的版本；新修改尚未发布';notify('已发布保存时的版本，请继续保存刚才的新修改')}
        else{notify('作品已发布');void router.push(`/work/${id.value}`)}
      }else if(!automatic)notify('草稿已保存')
      if(dirty.value&&!published.value)schedule()
    }catch(e){error.value=message(e);saved.value='保存失败，正文仍保留，请重试'}
  }).finally(()=>{busy.value=false})
}
function toggleTag(tag:number){form.tagIds=form.tagIds.includes(tag)?form.tagIds.filter(t=>t!==tag):[...form.tagIds,tag].slice(0,5)}
async function importFile(event:Event){
  const input=event.target as HTMLInputElement,file=input.files?.[0];input.value='';if(!file||importing.value||uploading.value)return
  if(count.value&&!confirm('导入文字将替换当前正文，是否继续？'))return
  importing.value=true;clearTimeout(timer)
  importTask=(async()=>{try{
    const data=new FormData();data.append('file',file);const result=await api.post('/member/import',data)
    const escape=(s:string)=>s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
    articleEditor.value?.replaceContent(result.data.text.split(/\r?\n/).map((p:string)=>`<p>${escape(p)||'<br>'}</p>`).join(''))
    if(!form.title)form.title=file.name.replace(/\.[^.]+$/,'').slice(0,100)
    changed();notify('文字已导入，请检查换行与排版')
  }catch(e){error.value=message(e)}finally{importing.value=false}})()
  await importTask;importTask=null
}
onMounted(async()=>{try{
  const [t,c,p]=await Promise.all([api.get('/public/tags'),api.get('/member/collections'),api.get('/member/pen-names')]);tags.value=t.data;collections.value=c.data;penNames.value=p.data
  if(id.value){
    const w:Work=(await api.get(`/public/works/${id.value}`)).data
    const genre=genres.includes(w.genre)?w.genre:'其他'
    Object.assign(form,{title:w.title,byline:w.byline||'',sourceNote:'',notes:[w.notes,w.sourceNote].filter(Boolean).join('\n\n'),genre,customGenre:w.customGenre||(genre==='其他'&&w.genre!=='其他'?w.genre:''),layout:resolveLayout(w.layout,genre),excerpt:w.excerpt,cover:w.cover,body:w.body,tagIds:w.tags.map(t=>t.id),collectionId:w.collectionId||0,penNameId:w.penNameId||0})
    published.value=w.status==='PUBLISHED';saved.value='稿件已载入'
  }else if(route.query.tag)form.tagIds=[Number(route.query.tag)]
  await nextTick();ready.value=true
}catch(e){error.value=message(e)}})
function unload(e:BeforeUnloadEvent){if(dirty.value||uploading.value||importing.value||composing.value){e.preventDefault();e.returnValue=''}}
window.addEventListener('beforeunload',unload)
onBeforeRouteLeave(async()=>{
  clearTimeout(timer)
  await enqueue(async()=>{})
  if(uploading.value||composing.value)await articleEditor.value?.settle()
  if(importTask)await importTask
  await nextTick()
  if(dirty.value&&!published.value)await save(false,true)
  if(dirty.value)return confirm('仍有未保存的修改，确定离开吗？')
})
onBeforeUnmount(()=>{disposed=true;clearTimeout(timer);window.removeEventListener('beforeunload',unload)})
</script>
<template><div class="container page-space editor-page"><div class="editor-top"><div><p class="eyebrow">投稿 / 编辑</p><h1 class="page-title">编辑作品</h1></div><span class="save-status"><Icon name="Check" :size="14"/>{{saved}}</span></div><p v-if="error" class="error-message" role="alert">{{error}}</p><div class="editor-layout"><section class="editor-paper"><input v-model="form.title" class="title-input" placeholder="作品标题" aria-label="作品标题" maxlength="100" :disabled="!ready"/><ArticleEditor ref="articleEditor" v-model="form.body" :disabled="!ready||importing" :genre="form.genre" :layout="form.layout" @count="count=$event" @pending="uploading=$event" @composition="compositionChanged"/><div class="editor-bottom"><span>{{count}} 字</span><span>支持自动保存草稿</span></div><section v-if="!preview||form.notes" class="editor-notes"><label for="work-notes">附注</label><textarea v-if="!preview" id="work-notes" v-model="form.notes" maxlength="4000" rows="5" placeholder="选填：来源、创作背景、署名或编辑说明"></textarea><p v-else class="notes-text">{{form.notes}}</p><p v-if="!preview" class="quiet-note">显示在文章末尾，最多 4000 字。</p></section></section><aside class="editor-sidebar"><section class="panel"><h3>作品信息</h3><label>作者笔名<select v-model="form.penNameId" :disabled="!ready"><option :value="0">不使用绑定笔名</option><option v-for="pen in penNames" :key="pen.id" :value="pen.id">{{pen.name}}</option></select></label><label v-if="!form.penNameId">作者署名<input v-model="form.byline" maxlength="120" placeholder="留空时使用发布账号昵称"/></label><p class="quiet-note">绑定笔名可汇总作品，文章仍由上传账号管理。<RouterLink to="/profile">管理绑定笔名 →</RouterLink></p><label>体裁<select aria-label="体裁" v-model="form.genre" :disabled="!ready" @change="genreChanged"><option v-for="g in genres.slice(1)" :key="g">{{g}}</option></select></label><label v-if="form.genre==='其他'">具体体裁<input v-model="form.customGenre" maxlength="30" placeholder="选填，例如：童话、剧本、书信"/></label><ArticleLayoutMenu v-model="form.layout" :genre="form.genre" :disabled="!ready"/><label>一小段引言<textarea v-model="form.excerpt" rows="3" maxlength="240" placeholder="留空时将从正文中提取"></textarea></label><label>标签 / 征稿活动</label><div class="tag-choices"><button v-for="t in tags" :key="t.id" :class="{selected:form.tagIds.includes(t.id)}" @click="toggleTag(t.id)"># {{t.name}}</button></div><p class="quiet-note">最多选择 5 个普通标签</p><label>所属合集<select v-model="form.collectionId" :disabled="!ready"><option :value="0">不加入合集</option><option v-for="collection in collections" :key="collection.id" :value="collection.id">{{collection.title}}{{collection.published?'':'（私密）'}}</option></select></label><p class="quiet-note">每篇文章最多绑定一部合集，可在合集页调整篇目顺序。<RouterLink to="/collection-edit">新建合集 →</RouterLink></p></section><section class="panel import-panel"><Icon name="Upload"/><h3>导入文档</h3><p>导入 DOCX、文字型 PDF 或 TXT。首版提取正文，图片请另行插入。</p><button class="button secondary full-width" :disabled="busy||importing||uploading||!ready" @click="fileInput?.click()">选择文档</button></section><section class="publish-panel"><p>发布后访客可阅读。当前仅进行基础内容检查。</p><button class="button full-width" :disabled="busy||importing||!ready" @click="save(true)"><Icon name="Send" :size="16"/>{{published?'检查并更新发布':'检查并发布'}}</button><button v-if="!published" class="text-button full-width" :disabled="busy||importing||!ready" @click="save(false)">保存为草稿</button></section></aside></div><input ref="fileInput" class="visually-hidden" type="file" accept=".docx,.pdf,.txt" @change="importFile"/></div></template>
