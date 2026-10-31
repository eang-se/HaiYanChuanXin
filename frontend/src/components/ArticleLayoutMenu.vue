<script setup lang="ts">
import {computed,onBeforeUnmount,ref,useId,watch} from 'vue'
import {defaultLayout,layoutClasses,layoutPresets,matchingPreset,type ArticleLayout} from '../article/layout'
import Icon from './Icon.vue'
const props=defineProps<{modelValue:ArticleLayout;genre:string;disabled?:boolean}>()
const emit=defineEmits<{'update:modelValue':[value:ArticleLayout]}>()
const dialog=ref<HTMLDialogElement|null>(null),trigger=ref<HTMLButtonElement|null>(null),draft=ref<ArticleLayout>({...props.modelValue}),advanced=ref(false)
const titleId=useId(),opened=ref(false)
let previousOverflow=''
const currentPreset=computed(()=>matchingPreset(props.modelValue,props.genre))
const currentLabel=computed(()=>layoutPresets.find(p=>p.id===currentPreset.value)?.label||'自定义版式')
const preset=computed(()=>matchingPreset(draft.value,props.genre))
const presets=computed(()=>layoutPresets.filter(p=>p.genre===props.genre||p.id==='default'||p.id===currentPreset.value))
function open(){
  if(props.disabled||opened.value||!dialog.value)return
  draft.value={...props.modelValue};advanced.value=false
  previousOverflow=document.body.style.overflow;document.body.style.overflow='hidden';opened.value=true;dialog.value.showModal()
}
function close(){dialog.value?.close()}
function closed(){
  if(!opened.value)return
  opened.value=false;document.body.style.overflow=previousOverflow
  if(trigger.value?.isConnected)trigger.value.focus({preventScroll:true})
}
function apply(){if(props.disabled)return;if(Object.entries(draft.value).some(([key,value])=>props.modelValue[key as keyof ArticleLayout]!==value))emit('update:modelValue',{...draft.value});close()}
function choose(id:string){const item=layoutPresets.find(p=>p.id===id);if(item)draft.value={...item.layout}}
function adjust(key:keyof ArticleLayout,event:Event){
  const target=event.target as HTMLInputElement
  draft.value={...draft.value,[key]:key==='indent'?target.checked:target.value}
  if(draft.value.bodyAlign==='center')draft.value.indent=false
}
watch(()=>[props.genre,props.disabled],()=>{if(opened.value)close()})
onBeforeUnmount(()=>{if(opened.value){document.body.style.overflow=previousOverflow;opened.value=false}})
</script>
<template>
  <div class="layout-entry"><div><span>版式</span><strong>{{currentLabel}}</strong></div><button ref="trigger" type="button" class="layout-settings-button" aria-haspopup="dialog" :aria-expanded="opened" :disabled="disabled" @click="open">设置版式</button></div>
  <Teleport to="body"><dialog ref="dialog" class="layout-dialog" :aria-labelledby="titleId" @close="closed" @cancel.prevent="close" @click="($event.target===$event.currentTarget)&&close()">
    <header class="layout-dialog-heading"><div><h2 :id="titleId">版式</h2><p>{{genre}} · 选择预设，或按需调整</p></div><button type="button" class="layout-close" aria-label="关闭版式设置" @click="close"><Icon name="X"/></button></header>
    <section class="layout-dialog-content">
      <fieldset class="layout-presets"><legend>预设</legend><div><button v-for="item in presets" :key="item.id" type="button" :class="{selected:preset===item.id}" :aria-pressed="preset===item.id" @click="choose(item.id)">{{item.label}}</button></div></fieldset>
      <div class="layout-sample prose" :class="layoutClasses(draft)" aria-label="版式预览">
        <template v-if="genre==='诗歌'"><h3>晚风</h3><p>风经过树梢<br>也经过我们的书页</p><p class="article-empty-line"><br></p><p>一行字，留给明天</p></template>
        <template v-else><h3>第一节</h3><p>窗边的光落在书页上，晚风带来草木的气息。</p><p>我们把尚未写完的句子收好，等待明天继续。</p></template>
      </div>
      <details :open="advanced" class="layout-adjustments" @toggle="advanced=($event.target as HTMLDetailsElement).open"><summary>细节调整<span v-if="preset==='custom'">已自定义</span></summary><div class="layout-option-grid">
        <label>正文对齐<select aria-label="正文对齐" :value="draft.bodyAlign" @change="adjust('bodyAlign',$event)"><option value="left">靠左</option><option value="center">居中</option></select></label>
        <label>节标题对齐<select aria-label="节标题对齐" :value="draft.headingAlign" @change="adjust('headingAlign',$event)"><option value="left">靠左</option><option value="center">居中</option></select></label>
        <label>行距<select aria-label="行距" :value="draft.lineSpacing" @change="adjust('lineSpacing',$event)"><option value="normal">紧凑</option><option value="relaxed">舒展</option><option value="airy">宽松</option></select></label>
        <label>段距 / 节距<select aria-label="段距 / 节距" :value="draft.paragraphSpacing" @change="adjust('paragraphSpacing',$event)"><option value="compact">紧凑（压缩空行）</option><option value="normal">标准</option><option value="loose">宽松</option></select></label>
        <label class="layout-indent-option"><input type="checkbox" :checked="draft.indent" :disabled="draft.bodyAlign==='center'" @change="adjust('indent',$event)"/>正文首行缩进两字</label>
      </div></details>
    </section>
    <footer class="layout-dialog-footer"><button type="button" class="layout-reset" @click="draft=defaultLayout(genre)">恢复默认</button><div><button type="button" class="button secondary" @click="close">取消</button><button type="button" class="button" :disabled="disabled" @click="apply">应用版式</button></div></footer>
  </dialog></Teleport>
</template>
