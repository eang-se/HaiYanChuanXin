<script setup lang="ts">
import {ref,reactive,onMounted,onBeforeUnmount,watch,computed,nextTick} from 'vue'
import {useRoute,useRouter,onBeforeRouteLeave} from 'vue-router'
import {api,user,message,notify,genres,type Tag,type Work,type PenName,type CollectionSummary,type BylineMode} from '../api'
import {createSaveQueue} from '../article/saveQueue'
import ArticleEditor from '../components/ArticleEditor.vue'
import ArticleLayoutMenu from '../components/ArticleLayoutMenu.vue'
import {defaultLayout,resolveLayout} from '../article/layout'
import Icon from '../components/Icon.vue'
import type {JSONContent} from '@tiptap/core'
import ArticleVersions from '../components/ArticleVersions.vue'
import BylinePicker from '../components/BylinePicker.vue'
import {canAnnounce} from '../composables/admin'
import PenNamesDialog from '../components/PenNamesDialog.vue'
import CollectionsDialog from '../components/CollectionsDialog.vue'
import {takeNewDraftSetup} from '../article/newDraft'
const route=useRoute(),router=useRouter(),id=ref<number|undefined>(route.params.id?Number(route.params.id):undefined),tags=ref<Tag[]>([]),ready=ref(false),published=ref(false),busy=ref(false),importing=ref(false),uploading=ref(false),composing=ref(false),error=ref(''),saved=ref('尚未保存'),dirty=ref(false),count=ref(0),collections=ref<CollectionSummary[]>([]),penNames=ref<PenName[]>([])
const form=reactive({title:'',byline:'佚名',bylineMode:'ANONYMOUS' as BylineMode,sourceNote:'',notes:'',genre:'散文',customGenre:'',layout:defaultLayout('散文'),excerpt:'',cover:'',body:'',bodyJson:null as JSONContent|null,activityIds:[] as number[],contentTags:[] as string[],collectionId:0,penNameId:0})
function includeBoundPen(pen:PenName){if(!penNames.value.some(p=>p.id===pen.id))penNames.value.push(pen)}
const pensOpen=ref(false),collectionsOpen=ref(false),createCollection=ref(false)
function updatePenNames(names:PenName[]){penNames.value=names;if(form.bylineMode==='BOUND'&&!names.some(p=>p.id===form.penNameId)){form.bylineMode='TEMPORARY';form.penNameId=0}}
async function openCollections(create:boolean){await save(false,true);if(dirty.value||conflicted.value){notify('请先保存当前稿件后再管理合集');return}createCollection.value=create;collectionsOpen.value=true}
async function collectionSaved(collectionId:number){try{collections.value=(await api.get('/member/collections')).data;if(id.value){const current=(await api.get(`/member/works/${id.value}/draft`)).data;draftRevision.value=current.draftRevision;if(!createCollection.value)form.collectionId=current.collectionId||0}if(createCollection.value)form.collectionId=collectionId}catch(e){error.value=message(e)}}
async function collectionDeleted(deletedId:number){try{collections.value=(await api.get('/member/collections')).data;if(id.value){const current=(await api.get(`/member/works/${id.value}/draft`)).data;draftRevision.value=current.draftRevision}if(form.collectionId===deletedId)form.collectionId=0}catch(e){error.value=message(e)}}
function addEditorCredit(){const name=user.value?.accountType==='PUBLIC'?(user.value.actorPenName||user.value.actorUsername):(user.value?.penName||user.value?.username);if(!name){notify('无法确认个人账号，请刷新后重试');return}const line=`本文编辑为：${name}`;if(form.notes.split('\n').some(value=>value.trim()===line)){notify('附注已包含当前编辑者');return}const value=form.notes?`${form.notes.replace(/\s+$/,'')}\n${line}`:line;if(value.length>4000){notify('附注字数不足，请先整理后再添加');return}form.notes=value;notify('已在附注添加编辑者')}
const draftRevision=ref<number|undefined>(undefined),conflicted=ref(false)
interface ModerationLocation {field:string;block:number|null;tag:string|null}
const moderationWarning=ref(''),reviewSnapshot=ref(''),reviewStatus=ref(''),moderationLocations=ref<ModerationLocation[]>([]),moderationStructure=ref<string[]>([])
const fieldLabels:Record<string,string>={title:'标题',byline:'署名',excerpt:'引言摘要',notes:'附注',customGenre:'具体体裁',body:'正文'}
const blockLabels:Record<string,string>={p:'段落',h2:'小标题',h3:'小标题',blockquote:'引用',ul:'无序列表',ol:'有序列表',figure:'图片说明',pre:'代码块'}
function locationLabel(item:ModerationLocation){return item.field==='body'&&item.block?`正文第 ${item.block} 个块（${blockLabels[item.tag||'']||'正文块'}）`:fieldLabels[item.field]||'文章内容'}
async function revealLocation(item:ModerationLocation){if(item.field!=='body'||!item.block||!item.tag)return;const ok=await articleEditor.value?.revealBlock(item.block,item.tag,moderationStructure.value);if(!ok)notify('正文结构已变化，请重新检查并发布以定位位置')}
const tagInput=ref(''),tagError=ref(''),tagComposing=ref(false),retainedActivities=ref<Tag[]>([])
const editorGenres=computed(()=>genres.slice(1).filter(g=>g!=='公告'||canAnnounce()))
const activityOptions=computed(()=>[...tags.value,...retainedActivities.value.filter(t=>form.activityIds.includes(t.id))])
function addContentTags(){
  const names=tagInput.value.split(/[,，;；\n]+/).map(name=>name.trim().replace(/^[#＃]+/,'').trim()).filter(Boolean)
  if(!names.length){tagInput.value='';tagError.value='';return true}
  if(names.some(name=>name.length>30)){tagError.value='每个内容标签最多 30 字';return false}
  if(names.includes('海燕精选')){tagError.value='海燕精选由编辑部设置，请填写其他内容标签';return false}
  const next=[...form.contentTags]
  for(const name of names)if(!next.some(t=>t.toLocaleLowerCase()===name.toLocaleLowerCase()))next.push(name)
  if(next.length>5){tagError.value='最多添加 5 个内容标签';return false}
  form.contentTags=next;tagInput.value='';tagError.value='';return true
}
function tagKeydown(event:KeyboardEvent){if(event.isComposing||tagComposing.value||event.keyCode===229)return;if(['Enter',',','，',';','；'].includes(event.key)){event.preventDefault();addContentTags()}}
const fileInput=ref<HTMLInputElement|null>(null),articleEditor=ref<InstanceType<typeof ArticleEditor>|null>(null)
const focused=ref(false)
const actionBar=ref<HTMLElement|null>(null),actionBarHeight=ref(74)
let actionBarObserver:ResizeObserver|undefined
onMounted(()=>{
  if(!actionBar.value)return
  const measure=()=>{actionBarHeight.value=actionBar.value?.getBoundingClientRect().height||74}
  measure();actionBarObserver=new ResizeObserver(measure);actionBarObserver.observe(actionBar.value)
})
const preview=computed(()=>articleEditor.value?.preview||false)
const enqueue=createSaveQueue()
let timer:ReturnType<typeof setTimeout>|undefined,disposed=false,importTask:Promise<void>|null=null
function schedule(){clearTimeout(timer);if(!conflicted.value&&!composing.value&&!disposed)timer=setTimeout(()=>void save(false,true),1800)}
function changed(){if(!ready.value)return;if(moderationWarning.value){moderationWarning.value='';reviewSnapshot.value='';moderationLocations.value=[]} if(reviewStatus.value.includes('待人工复核'))reviewStatus.value='稿件已修改，保存后原复核申请将失效。';dirty.value=true;saved.value=published.value?'有未发布的修改':'等待保存…';schedule()}
watch(form,changed,{deep:true})
function compositionChanged(value:boolean){composing.value=value;if(value)clearTimeout(timer);else if(dirty.value)schedule()}
function genreChanged(){form.layout=defaultLayout(form.genre);if(form.genre!=='其他')form.customGenre=''}
function save(publish=false,automatic=false,requestReview=false){
  if(!automatic&&!addContentTags())return Promise.resolve()
  clearTimeout(timer)
  if(busy.value||conflicted.value)return Promise.resolve()
  busy.value=true
  return enqueue(async()=>{
    if(disposed)return
    if(importTask)await importTask
    const imagesReady=await articleEditor.value?.settle()
    await nextTick()
    if(automatic&&(!dirty.value||(!form.title&&!count.value)))return
    if(publish&&!imagesReady){error.value='图片尚未成功插入，请重试图片或取消本次图片后发布。正文可继续保存为草稿。';return}
    error.value=''
    const snapshot=JSON.stringify(form),payload=JSON.parse(snapshot)
    saved.value='正在保存…'
    try{
      const body={...payload,publish,draftRevision:draftRevision.value}
      const result=requestReview?await api.post(id.value?`/member/works/${id.value}/article-reviews`:'/member/article-reviews',body):id.value?await api.put(`/member/works/${id.value}`,body):await api.post('/member/works',body)
      id.value=result.data.id
      draftRevision.value=result.data.draftRevision??draftRevision.value
      dirty.value=JSON.stringify(form)!==snapshot
      saved.value=dirty.value?'有新的修改待保存':`${published.value&&!publish?'草稿已保存':'已保存'} · ${new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})}`
      if(requestReview){moderationWarning.value='';reviewSnapshot.value='';moderationLocations.value=[];reviewStatus.value=dirty.value?'复核申请已提交；新修改保存后会使申请失效。':'已提交人工复核，审核结果将在此显示。';saved.value='已提交人工复核';notify('已提交人工复核');if(dirty.value)schedule()}
      else if(publish){
        moderationWarning.value='';reviewStatus.value='';published.value=true
        if(dirty.value){saved.value='已发布保存时的版本；新修改尚未发布';notify('已发布保存时的版本，请继续保存刚才的新修改')}
        else{notify('作品已发布');void router.push(`/work/${id.value}`)}
      }else if(!automatic)notify('草稿已保存')
      if(dirty.value&&!publish)schedule()
    }catch(e){if((e as {response?:{status?:number}})?.response?.status===422){moderationWarning.value=JSON.stringify(form)===snapshot?message(e):'检查期间文章已修改，请重新检查当前版本。';reviewSnapshot.value=snapshot;const details=(e as {response?:{data?:{locations?:ModerationLocation[];structure?:string[]}}}).response?.data;moderationLocations.value=JSON.stringify(form)===snapshot?(details?.locations||[]):[];moderationStructure.value=JSON.stringify(form)===snapshot?(details?.structure||[]):[];saved.value='内容需要检查，正文仍保留';error.value=''}else if((e as {response?:{status?:number}})?.response?.status===409){conflicted.value=true;error.value='稿件已在其他页面更新，请重新载入后再保存。正文仍保留在当前页面。';saved.value='版本冲突，请重新载入'}else{error.value=message(e);saved.value='保存失败，正文仍保留，请重试'}}
  }).finally(()=>{busy.value=false})
}
function toggleActivity(tag:number){form.activityIds=form.activityIds.includes(tag)?form.activityIds.filter(t=>t!==tag):[...form.activityIds,tag].slice(0,5)}
async function importFile(event:Event){
  const input=event.target as HTMLInputElement,file=input.files?.[0];input.value='';if(file)await importDocument(file)
}
async function importDocument(file:File){
  if(importing.value||uploading.value)return
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
  const [t,c,p]=await Promise.all([api.get('/public/activities'),api.get('/member/collections'),api.get('/member/pen-names')]);tags.value=t.data.filter((t:Tag)=>t.isActivity&&t.activityActive!==false);collections.value=c.data;penNames.value=p.data
  if(id.value){
    const w:Work=(await api.get(`/member/works/${id.value}/draft`)).data
    draftRevision.value=w.draftRevision
    const genre=genres.includes(w.genre)?w.genre:'其他'
    Object.assign(form,{title:w.title,byline:w.byline||'',sourceNote:'',notes:[w.notes,w.sourceNote].filter(Boolean).join('\n\n'),genre,customGenre:w.customGenre||(genre==='其他'&&w.genre!=='其他'?w.genre:''),layout:resolveLayout(w.layout,genre),excerpt:w.excerpt,cover:w.cover,body:w.body,activityIds:w.tags.filter(t=>t.isActivity).map(t=>t.id),contentTags:w.tags.filter(t=>!t.isActivity&&t.name!=='海燕精选').map(t=>t.name),collectionId:w.collectionId||0,penNameId:w.penNameId||0})
    retainedActivities.value=w.tags.filter(t=>t.isActivity&&!tags.value.some(a=>a.id===t.id)).map(t=>({...t,activityActive:false}))
    form.bodyJson=w.bodyJson||null
    form.bylineMode=w.bylineMode==='ANONYMOUS'?'ANONYMOUS':w.penNameId?'BOUND':'TEMPORARY'
    if(form.bylineMode==='ANONYMOUS'){form.penNameId=0;form.byline='佚名'}
    else if(form.bylineMode==='TEMPORARY')form.byline=w.byline||w.displayByline||w.authorName||user.value?.penName||''
    published.value=w.status==='PUBLISHED';saved.value='稿件已载入'
    const review=(await api.get(`/member/works/${id.value}/article-review`).catch(()=>({data:{}}))).data
    if(review.status==='PENDING')reviewStatus.value='待人工复核：'+review.findings
    else if(review.status==='REJECTED')reviewStatus.value='人工复核已退回：'+review.decisionNote
    else if(review.status==='APPROVED')reviewStatus.value='人工复核已通过，提交的版本已公开。'
    else if(review.status==='SUPERSEDED')reviewStatus.value='之前的复核申请因稿件修改已失效。'
  }else {
    if(penNames.value.length){const first=penNames.value[0]!;form.penNameId=first.id;form.byline=first.name;form.bylineMode='BOUND'}
    if(route.query.tag){const activity=tags.value.find(t=>t.id===Number(route.query.tag));if(activity)form.activityIds=[activity.id];else notify('该活动当前不可参与，请选择其他开放活动')}
  }
  const setup=!id.value?takeNewDraftSetup():null
  if(setup){form.title=setup.title;form.genre=setup.genre;form.layout=defaultLayout(setup.genre)}
  await nextTick();ready.value=true
  if(setup){changed();if(setup.file)await importDocument(setup.file)}
}catch(e){error.value=message(e)}})
function unload(e:BeforeUnloadEvent){if(dirty.value||tagInput.value.trim()||uploading.value||importing.value||composing.value){e.preventDefault();e.returnValue=''}}
function restoreVersion(data:{body:string;bodyJson?:JSONContent|null}){if(!confirm('载入历史正文将替换当前编辑内容，是否继续？'))return;articleEditor.value?.replaceContent(data.body,data.bodyJson);changed()}
window.addEventListener('beforeunload',unload)
onBeforeRouteLeave(async()=>{
  clearTimeout(timer)
  if(tagInput.value.trim()&&!addContentTags())return false
  await enqueue(async()=>{})
  if(uploading.value||composing.value)await articleEditor.value?.settle()
  if(importTask)await importTask
  await nextTick()
  if(dirty.value&&!conflicted.value)await save(false,true)
  if(dirty.value)return confirm('仍有未保存的修改，确定离开吗？')
})
onBeforeUnmount(()=>{disposed=true;clearTimeout(timer);actionBarObserver?.disconnect();window.removeEventListener('beforeunload',unload)})
</script>
<template>
  <div class="editor-page" :class="{'is-focused':focused}" :style="{'--toolbar-offset':actionBarHeight+'px'}">
    <header class="workspace-heading">
      <div>
        <RouterLink to="/account" class="workspace-back"><Icon name="ChevronLeft" :size="14"/>我的书桌</RouterLink>
        <div class="workspace-title"><h1>编辑作品</h1><span class="manuscript-badge">{{published?'已发布':'草稿'}}</span></div>
        <p>把灵感写成文字，把故事留在这里。</p>
      </div>
      <div class="workspace-mark" aria-hidden="true"><Icon name="Feather" :size="28"/><span>海燕传信 · 创作空间</span></div>
    </header>

    <div ref="actionBar" class="workspace-actions" aria-label="稿件操作">
      <div class="save-status" :class="{'is-pending':dirty||busy,'is-error':!!error}" role="status" aria-live="polite">
        <span class="status-dot" aria-hidden="true"></span><span>{{!ready&&!error?'正在载入稿件…':saved}}</span>
      </div>
      <div class="workspace-action-buttons">
        <button class="button secondary draft-action" :disabled="busy||importing||!ready||conflicted" @click="save(false)"><Icon name="FileText" :size="16"/>保存为草稿</button>
        <button class="button publish-action" :disabled="busy||importing||!ready||conflicted" @click="save(true)"><Icon name="Send" :size="15"/>{{published?'检查并更新发布':'检查并发布'}}</button>
      </div>
    </div>
    <p v-if="error" class="error-message workspace-error" role="alert">{{error}}</p>
    <div v-if="moderationWarning" class="error-message workspace-error" role="alert"><p>{{moderationWarning}}</p><div v-if="moderationLocations.length"><p>请检查以下位置（正文块从上到下计数，包含空行和图片）：</p><ul><li v-for="(item,index) in moderationLocations" :key="index"><button v-if="item.field==='body'&&item.block&&item.tag" type="button" class="moderation-location" @click="revealLocation(item)">{{locationLabel(item)}} · 定位</button><span v-else>{{locationLabel(item)}}{{item.field==='body'?'（可能涉及多个块）':''}}</span></li></ul></div><p v-else>无法精确定位，请检查文章各字段及相邻正文块。</p><p>本地规则可能误判。你可以修改内容后重新发布，或将当前版本提交人工复核。</p><button class="button secondary" :disabled="busy||JSON.stringify(form)!==reviewSnapshot" @click="save(true,false,true)">提交人工复核</button></div>
    <p v-if="reviewStatus" class="admin-info" role="status">{{reviewStatus}}</p>

    <div class="editor-layout">
      <div class="writing-column">
        <div class="writing-heading">
          <div><Icon :name="preview?'BookOpen':'Feather'" :size="17"/><h2>{{preview?'阅读预览':'正文创作'}}</h2></div>
          <button type="button" class="mobile-preview-toggle" :aria-pressed="preview" :disabled="!ready||composing" @click="articleEditor?.togglePreview()"><Icon :name="preview?'Feather':'BookOpen'" :size="15"/>{{preview?'继续编辑':'阅读预览'}}</button>
          <button class="focus-toggle" :aria-pressed="focused" aria-controls="work-settings" @click="focused=!focused"><Icon :name="focused?'Settings':'BookOpen'" :size="15"/>{{focused?'显示作品设置':'专注写作'}}</button>
        </div>
        <section class="editor-paper" aria-label="文章编辑区" :aria-busy="!ready||importing">
          <div class="paper-title">
            <div class="paper-kicker"><span>{{form.genre==='其他'?(form.customGenre||'作品'):form.genre}} · {{preview?'预览':'正文'}}</span><span>{{form.title.length}} / 100</span></div>
            <input v-model="form.title" class="title-input" placeholder="在这里，写下标题" aria-label="作品标题" maxlength="100" :disabled="!ready" :readonly="preview"/>
          </div>
          <ArticleEditor ref="articleEditor" v-model="form.body" v-model:document="form.bodyJson" :disabled="!ready||importing" :genre="form.genre" :layout="form.layout" :cover="form.cover" @count="count=$event" @pending="uploading=$event" @composition="compositionChanged"/>
          <div class="editor-bottom"><span><strong>{{count.toLocaleString()}}</strong> 字<span v-if="count" class="reading-estimate">预计阅读 {{Math.max(1,Math.ceil(count/500))}} 分钟</span></span><span>{{published?'修改后请更新发布':'草稿将自动保存'}}</span></div>
        </section>
        <section v-if="!preview||form.notes" class="editor-notes">
          <div class="notes-heading"><label for="work-notes">附注</label><span>选填 · 显示在文章末尾</span></div>
          <textarea v-if="!preview" id="work-notes" v-model="form.notes" :disabled="!ready" maxlength="4000" rows="3" placeholder="记录创作背景、内容来源，或想对读者说的话…"></textarea>
          <p v-else class="notes-text">{{form.notes}}</p>
          <div v-if="!preview" class="notes-footer"><span>给文字留一点余韵。</span><span>{{form.notes.length}} / 4000</span></div>
        </section>
        <p class="writing-tip"><Icon name="Feather" :size="14"/>在空白段落输入 /，快速插入标题、图片、引用等内容。</p>
      </div>

      <aside v-show="!focused" id="work-settings" class="editor-sidebar" aria-label="作品设置">
        <div class="settings-heading"><h2>作品设置</h2><span>让读者更好地认识这篇作品</span></div>
        <section class="settings-card">
          <h3><span class="settings-icon"><Icon name="FileText" :size="16"/></span>基本信息</h3>
          <fieldset :disabled="!ready">
            <BylinePicker @bound="includeBoundPen" v-model:mode="form.bylineMode" v-model:pen-name-id="form.penNameId" v-model:byline="form.byline" :pen-names="penNames" :disabled="!ready" :can-bind="user?.accountType!=='PUBLIC'"/>
            <div class="byline-actions"><button v-if="user?.accountType!=='PUBLIC'" type="button" :disabled="!ready" @click="pensOpen=true">管理绑定笔名</button><button type="button" :disabled="!ready" @click="addEditorCredit">添加编辑者字段</button></div>
            <label>体裁<select aria-label="体裁" v-model="form.genre" @change="genreChanged"><option v-for="g in editorGenres" :key="g">{{g}}</option></select></label>
            <label v-if="form.genre==='其他'">具体体裁<input v-model="form.customGenre" maxlength="30" placeholder="例如：童话、剧本、书信"/></label>
          </fieldset>
          <ArticleLayoutMenu v-model="form.layout" :genre="form.genre" :disabled="!ready"/>
        </section>

        <section class="settings-card">
          <h3><span class="settings-icon"><Icon name="BookOpen" :size="16"/></span>展示与归属</h3>
          <fieldset :disabled="!ready">
            <label for="work-excerpt">一小段引言<span class="optional-label">选填</span></label>
            <textarea id="work-excerpt" v-model="form.excerpt" rows="3" maxlength="240" placeholder="用一两句话，邀请读者走进你的作品"></textarea>
            <p class="field-help field-counter"><span>留空时从正文中提取</span><span>{{form.excerpt.length}} / 240</span></p>
            <div class="field-label" id="work-activities-label"><span>参与活动</span><span>{{form.activityIds.length}} / 5</span></div>
            <div class="tag-choices activity-choices" role="group" aria-labelledby="work-activities-label"><button v-for="t in activityOptions" :key="t.id" type="button" :class="{selected:form.activityIds.includes(t.id),'closed-activity':t.activityActive===false}" :aria-pressed="form.activityIds.includes(t.id)" :disabled="form.activityIds.length>=5&&!form.activityIds.includes(t.id)" @click="toggleActivity(t.id)"><Icon :name="form.activityIds.includes(t.id)?'Check':'Feather'" :size="12"/>{{t.name}}<small v-if="t.activityActive===false">已结束</small></button></div>
            <p class="field-help">{{tags.length?'从当前开放的活动中选择，选填。已结束的历史活动可保留或移除。':'暂无开放活动，可直接创作和发布。'}}</p>
            <div class="content-tags-field"><div class="field-label" id="work-content-tags-label"><span>内容标签</span><span>{{form.contentTags.length}} / 5</span></div><div v-if="form.contentTags.length" class="author-tag-list" role="group" aria-label="已添加的内容标签"><span v-for="tag in form.contentTags" :key="tag">#{{tag}}<button type="button" :aria-label="`移除内容标签 ${tag}`" @click="form.contentTags=form.contentTags.filter(t=>t!==tag);tagError='' "><Icon name="X" :size="12"/></button></span></div><div class="content-tag-input"><input v-model="tagInput" aria-label="填写内容标签" :aria-invalid="!!tagError" aria-describedby="content-tag-help" placeholder="如：校园、成长、读书" maxlength="160" @keydown="tagKeydown" @compositionstart="tagComposing=true" @compositionend="tagComposing=false" @blur="!tagComposing&&addContentTags()"/><button type="button" :disabled="!tagInput.trim()" @click="addContentTags">添加</button></div><p v-if="tagError" class="tag-input-error" role="alert">{{tagError}}</p><p id="content-tag-help" class="field-help">由你填写描述内容的关键词，按回车或逗号添加；每个最多 30 字。</p></div>
            <label>所属合集<select v-model="form.collectionId" aria-label="所属合集"><option :value="0">不加入合集</option><option v-for="collection in collections" :key="collection.id" :value="collection.id">{{collection.title}}{{collection.published?'':'（私密）'}}</option></select></label>
            <div class="collection-field-actions"><button type="button" @click="openCollections(true)"><Icon name="Plus" :size="13"/>新建合集</button><button type="button" @click="openCollections(false)">管理合集</button></div>
          </fieldset>
        </section>

        <section class="document-import">
          <div class="import-heading"><Icon name="Upload" :size="18"/><h3>已有写好的文字？</h3></div>
          <p>支持 DOCX、文字型 PDF、TXT。<br>导入正文后，可继续排版和插入图片。</p>
          <button class="button secondary full-width" :disabled="busy||importing||uploading||!ready" @click="fileInput?.click()"><Icon name="Upload" :size="15"/>{{importing?'正在导入…':'选择文档'}}</button>
        </section>
        <p class="publish-note"><Icon name="BookOpen" :size="14"/>发布后，所有访客均可阅读作品。</p>
        <ArticleVersions v-if="id" :work-id="id" :disabled="busy||importing||conflicted" @restore="restoreVersion"/>
      </aside>
    </div>
    <input ref="fileInput" class="visually-hidden" type="file" accept=".docx,.pdf,.txt" @change="importFile"/>
  </div>
<PenNamesDialog v-if="pensOpen" @close="pensOpen=false" @change="updatePenNames"/><CollectionsDialog v-if="collectionsOpen" :initial-id="createCollection?undefined:form.collectionId||undefined" :create="createCollection" @close="collectionsOpen=false" @saved="collectionSaved" @deleted="collectionDeleted"/></template>

<style scoped src="../editor.css"></style>
