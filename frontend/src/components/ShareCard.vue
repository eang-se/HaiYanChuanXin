<script setup lang="ts">
import {ref,onMounted,onBeforeUnmount,watch,computed} from 'vue'
import {siteName,notify} from '../api'
import {renderShareCard,canonicalShareUrl,validShareUrl,type ShareContent} from '../sharing'
import {articleFragment} from '../article/html'
import {shareBlocks,selectedShareBlocks,shareSelectionCount,type ShareSelection} from '../shareSelection'
import Icon from './Icon.vue'
const props=defineProps<{content:ShareContent}>(),emit=defineEmits<{close:[]}>()
const dialog=ref<HTMLDialogElement|null>(null),poster=ref(''),busy=ref(true),error=ref(''),link=ref(''),selection=ref<ShareSelection>({}),selectionError=ref('')
const blocks=computed(()=>props.content.kind==='article'&&props.content.body?shareBlocks(articleFragment(props.content.body)):[])
const selected=computed(()=>selectedShareBlocks(blocks.value,selection.value)),count=computed(()=>shareSelectionCount(blocks.value,selection.value))
let version=0,timer:ReturnType<typeof setTimeout>|undefined,previousOverflow=''
const localLink=computed(()=>{try{return ['localhost','127.0.0.1','[::1]'].includes(new URL(link.value).hostname)}catch{return false}})
function close(){dialog.value?.close();document.body.style.overflow=previousOverflow;emit('close')}
function updateSelection(blockIndex:number,sentenceIndex?:number){
 const block=blocks.value[blockIndex];if(!block)return
 const current=new Set(selection.value[blockIndex]||[])
 if(sentenceIndex===undefined){if(current.size===block.sentences.length)current.clear();else block.sentences.forEach((_,i)=>current.add(i))}
 else if(current.has(sentenceIndex))current.delete(sentenceIndex);else current.add(sentenceIndex)
 const next={...selection.value,[blockIndex]:[...current]},nextCount=shareSelectionCount(blocks.value,next)
 if(nextCount.blocks>10||nextCount.characters>500){selectionError.value='最多选择 10 个文字块、500 字';return}
 selection.value=next;selectionError.value=''
}
async function generate(){const token=++version;busy.value=true;error.value='';poster.value='';try{const url=validShareUrl(link.value);const result=await renderShareCard({...props.content,selectedBlocks:selected.value},url.href,siteName.value);if(token!==version)return;poster.value=result;try{localStorage.setItem('haiyan.shareBaseUrl',url.origin+url.pathname)}catch{}}catch(e){if(token===version)error.value=e instanceof Error?e.message:'分享图片生成失败，请重试'}finally{if(token===version)busy.value=false}}
watch([link,selected],()=>{version++;poster.value='';busy.value=true;clearTimeout(timer);timer=setTimeout(generate,300)})
async function copy(){const input=document.getElementById('share-url') as HTMLInputElement|null;try{validShareUrl(link.value);if(navigator.clipboard&&window.isSecureContext)await navigator.clipboard.writeText(link.value);else{input?.focus();input?.select();if(!document.execCommand('copy'))throw new Error('复制不可用')}notify('分享链接已复制')}catch{input?.focus();input?.select();error.value='链接已选中，请手动复制。'}}
function download(){if(!poster.value)return;const a=document.createElement('a');a.href=poster.value;a.download=props.content.title.replace(/[<>:"/\\|?*]/g,'_').slice(0,80)+'-分享卡片.png';document.body.appendChild(a);a.click();a.remove()}
onMounted(()=>{previousOverflow=document.body.style.overflow;dialog.value?.showModal();document.body.style.overflow='hidden';let base=window.location.href;try{base=localStorage.getItem('haiyan.shareBaseUrl')||base}catch{};try{link.value=canonicalShareUrl(props.content.path,base)}catch{link.value=canonicalShareUrl(props.content.path,window.location.href)}})
onBeforeUnmount(()=>{version++;clearTimeout(timer);document.body.style.overflow=previousOverflow})
</script>
<template>
 <dialog ref="dialog" class="share-dialog" aria-labelledby="share-card-title" @cancel.prevent="close" @click="($event.target===$event.currentTarget)&&close()">
  <div class="share-heading"><h2 id="share-card-title">分享{{content.kind==='collection'?'合集':'文章'}}</h2><button class="dialog-close" aria-label="关闭分享卡片" @click="close"><Icon name="X"/></button></div>
  <div class="share-layout">
   <div class="share-preview"><img v-if="poster" :src="poster" :alt="`${content.title}的分享卡片`"/><p v-else>{{busy?'正在生成…':'请检查分享链接'}}</p></div>
   <section class="share-options">
    <h3>{{content.title}}</h3>
    <div v-if="blocks.length" class="share-excerpts">
     <div class="share-excerpts-heading"><strong>选择摘录</strong><span>{{count.blocks}}/10 块 · {{count.characters}}/500 字</span></div>
     <p class="share-excerpts-help">点击句子或选择整块</p>
     <div class="share-blocks" aria-label="选择文章摘录">
      <div v-for="(block,bi) in blocks" :key="bi" class="share-block">
       <button type="button" class="share-block-toggle" :aria-pressed="(selection[bi]?.length||0)===block.sentences.length" @click="updateSelection(bi)">{{(selection[bi]?.length||0)===block.sentences.length?'取消整块':'选择整块'}}</button>
       <div class="share-sentences"><button v-for="(sentence,si) in block.sentences" :key="si" type="button" :aria-pressed="selection[bi]?.includes(si)||false" @click="updateSelection(bi,si)">{{sentence}}</button></div>
      </div>
     </div>
     <p v-if="selectionError" class="error-message" role="alert">{{selectionError}}</p>
    </div>
    <label for="share-url">分享链接</label><input id="share-url" v-model="link" type="url" maxlength="2000" spellcheck="false"/>
    <p v-if="localLink" class="quiet-note share-local-note">本机地址无法在其他设备访问，请改为可访问的站点地址。</p><p v-if="error" class="error-message" role="alert">{{error}}</p>
    <div class="share-buttons"><button class="button" :disabled="busy||!poster" @click="download"><Icon name="Image" :size="16"/>保存图片</button><button class="button secondary" @click="copy"><Icon name="ArrowUpRight" :size="16"/>复制链接</button></div>
   </section>
  </div>
 </dialog>
</template>
