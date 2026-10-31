<script setup lang="ts">
import {computed,nextTick,onBeforeUnmount,ref,watch} from 'vue'
import {EditorContent,useEditor} from '@tiptap/vue-3'
import {api,message,notify} from '../api'
import {articleExtensions} from '../article/editorExtensions'
import {sanitizeArticle,mediaPath} from '../article/html'
import {prepareImage} from '../article/imageUpload'
import {layoutClasses,resolveLayout,type ArticleLayout} from '../article/layout'
import ArticleBody from './ArticleBody.vue'
import Icon from './Icon.vue'
import '../article/article.css'

const props = defineProps<{modelValue:string; disabled?:boolean; genre?:string; layout?:ArticleLayout}>()
const presentation=computed(()=>resolveLayout(props.layout,props.genre))
const emit = defineEmits<{ 'update:modelValue':[value:string]; count:[value:number]; pending:[value:boolean]; composition:[value:boolean] }>()
const preview = ref(false), imageInput = ref<HTMLInputElement|null>(null), insertButton = ref<HTMLButtonElement|null>(null)
const uploading = ref(false), uploadError = ref(''), failedFile = ref<File|null>(null), composing = ref(false)
let uploadTask:Promise<void>|null = null, uploadPosition:number|null = null, finishComposition:(()=>void)|null = null, compositionTask:Promise<void>|null = null
const menu = ref(false), menuIndex = ref(0), menuPosition = ref({top:0,left:0}), slashFrom = ref<number|null>(null)
let dismissedAt:number|null = null
const choices = [
  {id:'paragraph',label:'正文段落'}, {id:'heading2',label:'小标题'}, {id:'heading3',label:'次级小标题'},
  {id:'quote',label:'引用'}, {id:'bullet',label:'无序列表'}, {id:'ordered',label:'有序列表'},
  {id:'image',label:'图片'}, {id:'divider',label:'分隔线'}, {id:'disclosure',label:'折叠内容'},
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
  emit('update:modelValue',editor.value.getHTML()); emit('count',Array.from(editor.value.getText().replace(/\s/g,'')).length)
}
watch(() => props.modelValue,value => {
  if (editor.value && value!==editor.value.getHTML()) { editor.value.commands.setContent(sanitizeArticle(value),{emitUpdate:false}); emit('count',Array.from(editor.value.getText().replace(/\s/g,'')).length) }
})
watch(() => props.disabled,value => editor.value?.setEditable(!value))
const inDisclosure = computed(() => editor.value?.isActive('disclosure'))
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
  if(insertButton.value)positionMenu(insertButton.value.getBoundingClientRect())
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
  if(!e || props.disabled || (kind==='disclosure'&&inDisclosure.value))return
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
    case 'disclosure':chain.insertContent({type:'disclosure',content:[{type:'disclosureTitle',content:[{type:'text',text:'折叠内容'}]},{type:'disclosureBody',content:[{type:'paragraph'}]}]}).run();break
  }
}
async function upload(file:File) {
  if(uploadTask || props.disabled){notify('请等待当前操作完成');return}
  uploading.value=true; uploadError.value=''; failedFile.value=null; emit('pending',true)
  uploadPosition=editor.value?.state.selection.to ?? null
  uploadTask=(async()=>{
    try {
      const prepared=await prepareImage(file), data=new FormData(); data.append('file',prepared)
      const response=await api.post('/member/images',data)
      if(!mediaPath.test(response.data.url))throw new Error('上传返回了无效图片地址，请重试')
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
  async settle() { if(compositionTask)await compositionTask; if(uploadTask)await uploadTask; return !failedFile.value },
  replaceContent(html:string) { editor.value?.commands.setContent(sanitizeArticle(html)); sync() },
  preview,
})
</script>

<template>
  <div class="editor-controls">
  <div class="editor-toolbar">
    <template v-if="!preview">
      <button ref="insertButton" class="block-insert-toggle" aria-haspopup="menu" :aria-expanded="menu" :disabled="disabled" @click="toggleMenu"><Icon name="Plus"/>插入</button>
      <button aria-label="加粗" :aria-pressed="editor?.isActive('bold')" :disabled="disabled" @click="editor?.chain().focus().toggleBold().run()"><Icon name="Bold"/></button>
      <button aria-label="斜体" :aria-pressed="editor?.isActive('italic')" :disabled="disabled" @click="editor?.chain().focus().toggleItalic().run()"><Icon name="Italic"/></button>
      <button aria-label="小标题" :disabled="disabled" @click="editor?.chain().focus().toggleHeading({level:2}).run()"><Icon name="Heading2"/></button>
      <button aria-label="引用" :disabled="disabled" @click="editor?.chain().focus().toggleBlockquote().run()"><Icon name="Quote"/></button>
      <button aria-label="无序列表" :disabled="disabled" @click="editor?.chain().focus().toggleBulletList().run()"><Icon name="List"/></button>
      <button aria-label="有序列表" :disabled="disabled" @click="editor?.chain().focus().toggleOrderedList().run()">1.</button>
      <button aria-label="插入图片" :disabled="disabled||uploading" @click="imageInput?.click()"><Icon name="Image"/></button>
      <button aria-label="撤销" :disabled="disabled||!editor?.can().undo()" @click="editor?.chain().focus().undo().run()"><Icon name="Undo"/></button>
      <button aria-label="重做" :disabled="disabled||!editor?.can().redo()" @click="editor?.chain().focus().redo().run()"><Icon name="Redo"/></button>
    </template>
    <button class="preview-toggle" :disabled="composing" @click="preview=!preview;closeMenu()">{{preview?'继续编辑':'阅读预览'}}</button>
  </div>
  <div v-if="selectedImage&&!preview" class="image-controls" aria-label="图片显示设置"><span>图片宽度</span>
    <button :class="{selected:imageSize==='full'}" @click="editor?.chain().focus().updateAttributes('image',{size:'full'}).run()">正文宽度</button>
    <button :class="{selected:imageSize==='narrow'}" @click="editor?.chain().focus().updateAttributes('image',{size:'narrow'}).run()">较窄显示</button>
  </div>
  <div v-if="uploading" class="upload-status" role="status">图片处理中… 保存会等待上传完成</div>
  <div v-if="uploadError" class="upload-status failed" role="alert"><span>{{uploadError}}，图片尚未插入，正文仍保留。</span><button @click="failedFile&&upload(failedFile)">重试图片</button><button @click="cancelFailedImage">取消本次图片</button></div>
  </div>
  <EditorContent v-show="!preview" :editor="editor" class="writing-area" :class="layoutClasses(presentation)"/>
  <ArticleBody v-if="preview" :html="modelValue" :genre="genre" :layout="presentation" compact class="preview-area"/>
  <Teleport to="body"><div v-if="menu&&!preview" class="block-menu" role="menu" aria-label="插入内容" :style="{top:menuPosition.top+'px',left:menuPosition.left+'px'}" @keydown.down.prevent="menuIndex=(menuIndex+1)%choices.length;scrollChoice()" @keydown.up.prevent="menuIndex=(menuIndex+choices.length-1)%choices.length;scrollChoice()" @keydown.enter.prevent="choose(choices[menuIndex]!.id)">
    <button v-for="(choice,index) in choices" :id="`article-insert-${index}`" :key="choice.id" role="menuitem" :class="{active:index===menuIndex}" :disabled="(choice.id==='disclosure'&&inDisclosure)||(choice.id==='image'&&uploading)" @pointerdown.prevent @click="choose(choice.id)" @focus="menuIndex=index">{{choice.label}}</button>
  </div></Teleport>
  <input ref="imageInput" class="visually-hidden" type="file" accept="image/jpeg,image/png" @change="selectedFile"/>
</template>
