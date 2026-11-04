<script setup lang="ts">
import {computed,nextTick,onBeforeUnmount,ref,reactive,watch} from 'vue'
import {EditorContent,useEditor} from '@tiptap/vue-3'
import type {JSONContent} from '@tiptap/core'
import {api,message,notify} from '../api'
import {articleExtensions} from '../article/editorExtensions'
import {sanitizeArticle,mediaPath} from '../article/html'
import {prepareImage,ARTICLE_IMAGE_LIMIT} from '../article/imageUpload'
import {expandArticleDocument} from '../article/document'
import {layoutClasses,resolveLayout,type ArticleLayout} from '../article/layout'
import ArticleBody from './ArticleBody.vue'
import Icon from './Icon.vue'
import '../article/article.css'

const props = defineProps<{modelValue:string; document?:JSONContent|null; disabled?:boolean; genre?:string; layout?:ArticleLayout;cover?:string}>()
const presentation=computed(()=>resolveLayout(props.layout,props.genre))
const emit = defineEmits<{ 'update:modelValue':[value:string]; 'update:document':[value:JSONContent]; count:[value:number]; pending:[value:boolean]; composition:[value:boolean] }>()
const toolbarOpen=ref(false)
const preview = ref(false), imageInput = ref<HTMLInputElement|null>(null), insertButton = ref<HTMLButtonElement|null>(null), mobileInsertButton = ref<HTMLButtonElement|null>(null)
const uploading = ref(false), uploadError = ref(''), failedFile = ref<File|null>(null), composing = ref(false)
const sizes=reactive<Record<string,number>>({}),budgetError=ref('')
const imageSources=computed(()=>{
  const doc=new DOMParser().parseFromString(props.modelValue,'text/html')
  return [...new Set([...Array.from(doc.querySelectorAll('img')).map(img=>img.getAttribute('src')||''),props.cover||''].filter(src=>mediaPath.test(src)))]
})
const imageBytes=computed(()=>imageSources.value.reduce((total,src)=>total+(sizes[src]||0),0))
let metadataTask:Promise<void>|null=null
async function ensureSizes(){
  if(metadataTask)await metadataTask
  const unknown=imageSources.value.filter(src=>sizes[src]===undefined)
  if(!unknown.length){budgetError.value='';return}
  metadataTask=(async()=>{for(let offset=0;offset<unknown.length;offset+=100){const batch=unknown.slice(offset,offset+100);const {data}=await api.get('/member/images/sizes',{params:{ids:batch.map(src=>src.split('/').at(-1)).join(',')}});for(const src of batch){const bytes=Number(data[src]);if(!Number.isFinite(bytes)||bytes<0)throw new Error('无法确认图片占用，请重试');sizes[src]=bytes}}})()
  try{await metadataTask;budgetError.value=''}finally{metadataTask=null}
}
watch(()=>imageSources.value.join(','),()=>{void ensureSizes().catch(e=>{budgetError.value=message(e)})},{immediate:true})
let uploadTask:Promise<void>|null = null, uploadPosition:number|null = null, finishComposition:(()=>void)|null = null, compositionTask:Promise<void>|null = null
const menu = ref(false), menuIndex = ref(0), menuPosition = ref({top:0,left:0}), slashFrom = ref<number|null>(null)
let dismissedAt:number|null = null
const choices = [
  {id:'paragraph',label:'正文段落'}, {id:'heading2',label:'小标题'}, {id:'heading3',label:'次级小标题'},
  {id:'quote',label:'引用'}, {id:'bullet',label:'无序列表'}, {id:'ordered',label:'有序列表'},
  {id:'image',label:'图片'}, {id:'divider',label:'分隔线'},
]
const editor = useEditor({
  extensions:articleExtensions(), content:sanitizeArticle(props.modelValue), editable:!props.disabled,
  editorProps:{
    attributes:{'aria-label':'作品正文',class:'prose'},
    transformPastedHTML:sanitizeArticle,
    handlePaste:(_view,event) => {
      const file = event.clipboardData?.files[0]
      if (!file) return false
      void upload(file); return true
    },
    handleDrop:(_view,event) => {
      const file = event.dataTransfer?.files[0]
      if (!file) return false
      void upload(file); return true
    },
    handleKeyDown:(_view,event) => {
      if (event.isComposing || composing.value || event.keyCode===229 || !menu.value) return false
      if (event.key==='Escape') { closeMenu(); return true }
      if (event.key==='ArrowDown'||event.key==='ArrowUp') {
        menuIndex.value=(menuIndex.value+(event.key==='ArrowDown'?1:-1)+choices.length)%choices.length
        scrollChoice(); return true
      }
      if(event.key==='Enter') { choose(choices[menuIndex.value]!.id); return true }
      return false
    },
    handleDOMEvents:{
      compositionstart:() => { composing.value=true; emit('composition',true); menu.value=false; compositionTask=new Promise(resolve=>{finishComposition=resolve}); return false },
      compositionend:() => { void nextTick(() => {
        composing.value=false; emit('composition',false); sync(); finishComposition?.(); finishComposition=null; compositionTask=null
        // The final composition transaction may still contain '/'; only a subsequent intentional input opens the menu.
        dismissedAt=editor.value?.state.selection.from || null
      }); return false },
    },
  },
  onUpdate:() => { sync(); updateSlash() },
  onSelectionUpdate:() => { updateSlash() },
  onTransaction:({transaction}) => { if(uploadPosition!==null)uploadPosition=transaction.mapping.map(uploadPosition) },
})
function sync() {
  if (!editor.value) return
  emit('update:modelValue',editor.value.getHTML()); emit('update:document',editor.value.getJSON()); emit('count',Array.from(editor.value.getText().replace(/\s/g,'')).length)
}
// Older works have HTML only. Invalid or unsupported stored documents fall back to the
// sanitized HTML snapshot instead of silently dropping unknown nodes.
watch(() => [props.modelValue,props.document] as const,([html,document]) => {
  const e=editor.value
  if (!e) return
  if (html===e.getHTML() && (!document || JSON.stringify(document)===JSON.stringify(e.getJSON()))) return
  if(document) {
    try { e.commands.setContent(expandArticleDocument(document),{emitUpdate:false,errorOnInvalidContent:true}) }
    catch { e.commands.setContent(sanitizeArticle(html),{emitUpdate:false}) }
  } else e.commands.setContent(sanitizeArticle(html),{emitUpdate:false})
  emit('count',Array.from(e.getText().replace(/\s/g,'')).length)
})
watch(() => props.disabled,value => editor.value?.setEditable(!value,false))
const selectedImage = computed(() => editor.value?.isActive('image'))
const imageSize = computed(() => editor.value?.getAttributes('image').size)
function positionMenu(rect:{bottom:number;left:number}) {
  menuPosition.value={left:Math.max(16,Math.min(rect.left,window.innerWidth-236)),top:Math.max(12,Math.min(rect.bottom+6,window.innerHeight-Math.min(420,window.innerHeight*.55)-16))}
}
function updateSlash() {
  const e=editor.value
  if(!e || composing.value || e.view.composing || preview.value) return
  const {empty,$from,from} = e.state.selection
  if(!empty || $from.parent.type.name!=='paragraph' || $from.parent.textContent!=='/' || $from.parentOffset!==1) {
    if(slashFrom.value!==null)menu.value=false
    slashFrom.value=null; dismissedAt=null; return
  }
  if(dismissedAt===from)return
  if(!menu.value)menuIndex.value=0
  slashFrom.value=$from.start(); positionMenu(e.view.coordsAtPos(from)); menu.value=true
}
function closeMenu() { dismissedAt=editor.value?.state.selection.from || null; menu.value=false; slashFrom.value=null }
function toggleMenu() {
  if(menu.value){closeMenu();return}
  slashFrom.value=null; menuIndex.value=0
  const trigger=mobileInsertButton.value?.getClientRects().length?mobileInsertButton.value:insertButton.value
  if(trigger)positionMenu(trigger.getBoundingClientRect())
  menu.value=true
  void nextTick(() => document.getElementById('article-insert-0')?.focus({preventScroll:true}))
}
function scrollChoice() { void nextTick(() => {
  const choice=document.getElementById(`article-insert-${menuIndex.value}`)
  if(document.activeElement?.closest('.block-menu'))choice?.focus({preventScroll:true})
  choice?.scrollIntoView({block:'nearest'})
}) }
function choose(kind:string) {
  const e=editor.value
  if(!e || props.disabled)return
  const start=slashFrom.value; closeMenu()
  if(start!==null)e.chain().focus().deleteRange({from:start,to:start+1}).run()
  const chain=e.chain().focus()
  switch(kind) {
    case 'paragraph':chain.setParagraph().run();break
    case 'heading2':chain.setHeading({level:2}).run();break
    case 'heading3':chain.setHeading({level:3}).run();break
    case 'quote':chain.toggleBlockquote().run();break
    case 'bullet':chain.toggleBulletList().run();break
    case 'ordered':chain.toggleOrderedList().run();break
    case 'divider':chain.setHorizontalRule().run();break
    case 'image':imageInput.value?.click();break
  }
}
async function upload(file:File) {
  if(uploadTask || props.disabled){notify('请等待当前操作完成');return}
  uploading.value=true; uploadError.value=''; failedFile.value=null; emit('pending',true)
  uploadPosition=editor.value?.state.selection.to ?? null
  uploadTask=(async()=>{
    try {
      await ensureSizes()
      const remaining=ARTICLE_IMAGE_LIMIT-imageBytes.value
      const prepared=await prepareImage(file,remaining), data=new FormData(); data.append('file',prepared)
      const response=await api.post('/member/images',data)
      if(!mediaPath.test(response.data.url))throw new Error('上传返回了无效图片地址，请重试')
      const bytes=Number(response.data.sizeBytes??prepared.size)
      if(!Number.isFinite(bytes)||bytes<0)throw new Error('上传返回了无效图片大小，请重试')
      await ensureSizes()
      if(imageBytes.value+(imageSources.value.includes(response.data.url)?0:bytes)>ARTICLE_IMAGE_LIMIT)throw new Error('一篇文章的图片总大小不能超过 5 MB，请先删除部分图片')
      sizes[response.data.url]=bytes
      const e=editor.value
      if(!e || e.isDestroyed)return
      const position=Math.min(uploadPosition??e.state.selection.to,e.state.doc.content.size)
      const inserted=e.chain().focus().insertContentAt(position,{type:'image',attrs:{src:response.data.url,alt:file.name,width:Number(response.data.width)||null,height:Number(response.data.height)||null,size:'full'}}).run()
      if(!inserted)throw new Error('当前位置无法插入图片，请移到正文段落后重试')
    } catch(error) { uploadError.value=message(error); failedFile.value=file }
    finally { uploading.value=false; uploadPosition=null; emit('pending',false) }
  })()
  await uploadTask; uploadTask=null
}
function selectedFile(event:Event) { const input=event.target as HTMLInputElement,file=input.files?.[0]; input.value=''; if(file)void upload(file) }
function cancelFailedImage() { failedFile.value=null; uploadError.value='' }
function outside(event:PointerEvent) {
  if(!(event.target as HTMLElement).closest('.block-menu,.block-insert-toggle'))closeMenu()
}
function escapeMenu(event:KeyboardEvent) { if(event.key==='Escape'&&menu.value){closeMenu();editor.value?.commands.focus()} }
window.addEventListener('pointerdown',outside); window.addEventListener('keydown',escapeMenu)
onBeforeUnmount(() => { window.removeEventListener('pointerdown',outside); window.removeEventListener('keydown',escapeMenu); finishComposition?.(); editor.value?.destroy() })
defineExpose({
  async settle() { if(compositionTask)await compositionTask; if(uploadTask)await uploadTask;try{await ensureSizes()}catch(e){budgetError.value=message(e);return false}return !failedFile.value&&imageBytes.value<=ARTICLE_IMAGE_LIMIT },
  replaceContent(html:string, document?:JSONContent|null) {
    const e=editor.value
    if(!e)return
    if(document) {
      try { e.commands.setContent(expandArticleDocument(document),{errorOnInvalidContent:true}) }
      catch { e.commands.setContent(sanitizeArticle(html)) }
    } else e.commands.setContent(sanitizeArticle(html))
    sync()
  },
  getSnapshot() { const e=editor.value; return e ? {body:e.getHTML(),bodyJson:e.getJSON()} : null },
  async revealBlock(index:number,tag:string,structure:string[]) {
    preview.value=false
    await nextTick()
    const e=editor.value, target=e?.view.dom.children.item(index-1) as HTMLElement|null
    const actual=e?Array.from(e.view.dom.children,element=>element.tagName.toLowerCase()):[]
    if(!target||target.tagName.toLowerCase()!==tag||actual.length!==structure.length||actual.some((name,i)=>name!==structure[i]))return false
    target.scrollIntoView({behavior:'smooth',block:'center'})
    target.animate([{outline:'3px solid #d99270',outlineOffset:'6px'},{outline:'3px solid #d99270',outlineOffset:'6px'},{outline:'3px solid transparent',outlineOffset:'6px'}],{duration:3500})
    return true
  },
  togglePreview(){if(!props.disabled&&!composing.value){preview.value=!preview.value;closeMenu()}},
  preview,
})
</script>

<template>
  <div class="editor-controls">
  <div class="editor-toolbar" role="group" aria-label="正文格式工具">
    <template v-if="!preview"><button ref="mobileInsertButton" type="button" class="mobile-quick-format block-insert-toggle" aria-label="插入内容" aria-haspopup="menu" :aria-expanded="menu" :disabled="disabled" @click="toggleMenu"><Icon name="Plus"/></button><button type="button" class="mobile-quick-format" aria-label="加粗" title="加粗" :aria-pressed="editor?.isActive('bold')" :disabled="disabled" @click="editor?.chain().focus().toggleBold().run()"><Icon name="Bold"/></button><button type="button" class="mobile-quick-format" aria-label="斜体" title="斜体" :aria-pressed="editor?.isActive('italic')" :disabled="disabled" @click="editor?.chain().focus().toggleItalic().run()"><Icon name="Italic"/></button><button type="button" class="mobile-quick-format" aria-label="撤销" title="撤销" :disabled="disabled||!editor?.can().undo()" @click="editor?.chain().focus().undo().run()"><Icon name="Undo"/></button><button type="button" class="mobile-quick-format" aria-label="重做" title="重做" :disabled="disabled||!editor?.can().redo()" @click="editor?.chain().focus().redo().run()"><Icon name="Redo"/></button><button type="button" class="mobile-quick-format" aria-label="插入图片" title="插入图片" :disabled="disabled||uploading" @click="imageInput?.click()"><Icon name="Image"/></button></template>
    <button v-if="!preview" type="button" class="mobile-format-toggle" :aria-expanded="toolbarOpen" aria-controls="format-options" aria-label="展开更多工具" @click="toolbarOpen=!toolbarOpen"><Icon :name="toolbarOpen?'X':'ArrowDown'" :size="17"/></button>
    <div v-if="!preview" id="format-options" class="toolbar-options" :class="{'is-open':toolbarOpen}">
      <button ref="insertButton" class="block-insert-toggle mobile-duplicate" aria-haspopup="menu" :aria-expanded="menu" :disabled="disabled" @click="toggleMenu"><Icon name="Plus"/>插入</button>
      <button class="mobile-duplicate" aria-label="加粗" title="加粗" :aria-pressed="editor?.isActive('bold')" :disabled="disabled" @click="editor?.chain().focus().toggleBold().run()"><Icon name="Bold"/></button>
      <button class="mobile-duplicate" aria-label="斜体" title="斜体" :aria-pressed="editor?.isActive('italic')" :disabled="disabled" @click="editor?.chain().focus().toggleItalic().run()"><Icon name="Italic"/></button>
      <button aria-label="小标题" title="小标题" :aria-pressed="editor?.isActive('heading',{level:2})" :disabled="disabled" @click="editor?.chain().focus().toggleHeading({level:2}).run()"><Icon name="Heading2"/></button>
      <button aria-label="引用" title="引用" :aria-pressed="editor?.isActive('blockquote')" :disabled="disabled" @click="editor?.chain().focus().toggleBlockquote().run()"><Icon name="Quote"/></button>
      <button aria-label="无序列表" title="无序列表" :aria-pressed="editor?.isActive('bulletList')" :disabled="disabled" @click="editor?.chain().focus().toggleBulletList().run()"><Icon name="List"/></button>
      <button aria-label="有序列表" title="有序列表" :aria-pressed="editor?.isActive('orderedList')" :disabled="disabled" @click="editor?.chain().focus().toggleOrderedList().run()">1.</button>
      <button class="mobile-duplicate" aria-label="插入图片" title="插入图片" :disabled="disabled||uploading" @click="imageInput?.click()"><Icon name="Image"/></button>
      <button class="mobile-duplicate" aria-label="撤销" title="撤销" :disabled="disabled||!editor?.can().undo()" @click="editor?.chain().focus().undo().run()"><Icon name="Undo"/></button>
      <button class="mobile-duplicate" aria-label="重做" title="重做" :disabled="disabled||!editor?.can().redo()" @click="editor?.chain().focus().redo().run()"><Icon name="Redo"/></button>
    </div>
    <button class="preview-toggle" :aria-pressed="preview" :disabled="disabled||composing" @click="preview=!preview;closeMenu()"><Icon :name="preview?'Feather':'BookOpen'" :size="14"/>{{preview?'继续编辑':'阅读预览'}}</button>
  </div>
  <div v-if="selectedImage&&!preview" class="image-controls" aria-label="图片显示设置"><span>图片宽度</span>
    <button :class="{selected:imageSize==='full'}" @click="editor?.chain().focus().updateAttributes('image',{size:'full'}).run()">正文宽度</button>
    <button :class="{selected:imageSize==='narrow'}" @click="editor?.chain().focus().updateAttributes('image',{size:'narrow'}).run()">较窄显示</button>
  </div>
  <div v-if="uploading" class="upload-status" role="status">图片处理中… 保存会等待上传完成</div>
  <p class="image-budget" :class="{'is-over':imageBytes>ARTICLE_IMAGE_LIMIT}" aria-live="polite">图片自动压缩 · 本篇 {{(imageBytes/1024/1024).toFixed(2)}} / 5 MB<span v-if="cover">（含封面）</span></p>
  <div v-if="budgetError||imageBytes>ARTICLE_IMAGE_LIMIT" class="upload-status failed" role="alert">{{budgetError||'本篇图片总大小超过 5 MB，请删除部分图片后保存。'}}<button v-if="budgetError" type="button" @click="ensureSizes().catch(e=>budgetError=message(e))">重试检查</button></div>
  <div v-if="uploadError" class="upload-status failed" role="alert"><span>{{uploadError}}，图片尚未插入，正文仍保留。</span><button @click="failedFile&&upload(failedFile)">重试图片</button><button @click="cancelFailedImage">取消本次图片</button></div>
  </div>
  <EditorContent v-show="!preview" :editor="editor" class="writing-area" :class="layoutClasses(presentation)"/>
  <ArticleBody v-if="preview" :html="modelValue" :genre="genre" :layout="presentation" compact class="preview-area"/>
  <Teleport to="body"><div v-if="menu&&!preview" class="block-menu" role="menu" aria-label="插入内容" :style="{top:menuPosition.top+'px',left:menuPosition.left+'px'}" @keydown.down.prevent="menuIndex=(menuIndex+1)%choices.length;scrollChoice()" @keydown.up.prevent="menuIndex=(menuIndex+choices.length-1)%choices.length;scrollChoice()" @keydown.enter.prevent="choose(choices[menuIndex]!.id)">
    <button v-for="(choice,index) in choices" :id="`article-insert-${index}`" :key="choice.id" role="menuitem" :class="{active:index===menuIndex}" :disabled="choice.id==='image'&&uploading" @pointerdown.prevent @click="choose(choice.id)" @focus="menuIndex=index">{{choice.label}}</button>
  </div></Teleport>
  <input ref="imageInput" class="visually-hidden" type="file" accept="image/jpeg,image/png" @change="selectedFile"/>
</template>
