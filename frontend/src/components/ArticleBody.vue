<script setup lang="ts">
import {computed,nextTick,onBeforeUnmount,ref,useId,watch} from 'vue'
import {prepareArticle,mediaPath} from '../article/html'
import {layoutClasses,resolveLayout,type ArticleLayout} from '../article/layout'

const props = defineProps<{html:string; namespace?:string; genre?:string; layout?:ArticleLayout; compact?:boolean}>()
const presentation=computed(()=>layoutClasses(resolveLayout(props.layout,props.genre)))
const instance = useId(), content = ref<HTMLElement|null>(null), toc = ref<HTMLDetailsElement|null>(null)
const prepared = computed(() => prepareArticle(props.html, props.namespace || instance))
const active = ref(''), dialog = ref<HTMLDialogElement|null>(null), large = ref({src:'',alt:'',caption:''})
let headings: HTMLElement[] = [], frame = 0, restoreFocus: HTMLElement|null = null, previousOverflow = ''
function updateSection() {
  cancelAnimationFrame(frame)
  frame = requestAnimationFrame(() => {
    let current = headings[0]?.id || ''
    for (const heading of headings) {
      if (!heading.getClientRects().length) continue
      if (heading.getBoundingClientRect().top > 140) break
      current = heading.id
    }
    active.value = current
  })
}
watch(() => prepared.value.html, async () => {
  await nextTick(); headings = [...(content.value?.querySelectorAll<HTMLElement>('h2,h3') || [])]; updateSection()
}, {immediate:true})
window.addEventListener('scroll',updateSection,{passive:true})
function locate(id:string) {
  const heading = headings.find(h => h.id === id)
  if (!heading) return
  let parent = heading.parentElement
  while (parent && parent !== content.value) { if (parent instanceof HTMLDetailsElement) parent.open = true; parent = parent.parentElement }
  if (toc.value) toc.value.open = false
  heading.scrollIntoView({block:'start',behavior:'auto'}); heading.focus({preventScroll:true}); active.value = id
}
async function openImage(event:Event) {
  const target = event.target
  if (large.value.src || !(target instanceof HTMLImageElement) || !mediaPath.test(target.getAttribute('src') || '')) return
  if (event instanceof KeyboardEvent && !['Enter',' '].includes(event.key)) return
  event.preventDefault(); restoreFocus = target
  large.value = {src:target.getAttribute('src')!,alt:target.alt,caption:target.closest('figure')?.querySelector('figcaption')?.textContent || ''}
  await nextTick()
  if (!dialog.value) return
  previousOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden'; dialog.value.showModal()
}
function closed() { document.body.style.overflow = previousOverflow; large.value.src = ''; restoreFocus?.focus({preventScroll:true}) }
onBeforeUnmount(() => { window.removeEventListener('scroll',updateSection); cancelAnimationFrame(frame); if (large.value.src) document.body.style.overflow = previousOverflow })
</script>

<template>
  <div class="article-body" :class="{'article-body-compact':compact}">
    <details v-if="prepared.headings.length" ref="toc" class="article-toc-mobile">
      <summary>文章目录 <span>{{prepared.headings.length}} 节</span></summary>
      <nav aria-label="文章目录"><button v-for="heading in prepared.headings" :key="heading.id" :class="{subheading:heading.level===3,active:active===heading.id}" :aria-current="active===heading.id?'location':undefined" @click="locate(heading.id)">{{heading.title}}</button></nav>
    </details>
    <nav v-if="prepared.headings.length&&!compact" class="article-toc-side" aria-label="文章目录">
      <p>本文目录</p><button v-for="heading in prepared.headings" :key="heading.id" :class="{subheading:heading.level===3,active:active===heading.id}" :aria-current="active===heading.id?'location':undefined" @click="locate(heading.id)">{{heading.title}}</button>
    </nav>
    <div ref="content" class="prose article-content" :class="presentation" @click="openImage" @keydown="openImage" @toggle.capture="updateSection" v-html="prepared.html"></div>
    <Teleport to="body"><dialog ref="dialog" class="article-lightbox" aria-label="图片大图" @close="closed" @click="e=>{if(e.target===dialog)dialog?.close()}">
      <button class="lightbox-close" aria-label="关闭大图" autofocus @click="dialog?.close()">关闭 ×</button>
      <img v-if="large.src" :src="large.src" :alt="large.alt"/><p v-if="large.caption">{{large.caption}}</p>
    </dialog></Teleport>
  </div>
</template>
