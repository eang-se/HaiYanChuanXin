<script setup lang="ts">
import {ref,onMounted,computed} from 'vue'
import WorkFeed from '../components/WorkFeed.vue'
import LayoutSwitch from '../components/LayoutSwitch.vue'
import ShareCard from '../components/ShareCard.vue'
import Icon from '../components/Icon.vue'
import {useRoute} from 'vue-router'
import {api,user,message} from '../api'
const route=useRoute(),collection=ref<any>(null),error=ref(''),shareOpen=ref(false)
const shareContent=computed(()=>collection.value?{kind:'collection' as const,title:collection.value.title,credit:collection.value.authorName,description:collection.value.description,detail:`${collection.value.kind} · ${collection.value.works.length} 篇作品`,path:`/collection/${collection.value.id}`}:null)
onMounted(async()=>{try{collection.value=(await api.get(`/public/collections/${route.params.id}`)).data}catch(e){error.value=message(e)}})
</script>
<template><div class="container page-space collection-detail"><RouterLink to="/collections" class="back-link">← 全部合集</RouterLink><p v-if="error" class="error-message">{{error}}</p><template v-if="collection"><header class="collection-heading"><p class="eyebrow">{{collection.kind}} / {{collection.published?'公开':'仅自己可见'}}</p><h1 class="page-title">{{collection.title}}</h1><RouterLink :to="`/author/${collection.authorId}`">{{collection.authorName}}</RouterLink><p class="collection-description">{{collection.description}}</p><div class="collection-actions"><RouterLink v-if="user?.id===collection.authorId" :to="`/collection-edit/${collection.id}`" class="button secondary">编辑合集</RouterLink><button v-if="collection.published" class="button secondary" @click="shareOpen=true"><Icon name="ArrowUpRight" :size="16"/>分享合集</button></div></header><div class="section-heading"><h2>目录<span class="section-count">{{collection.works.length}} 篇</span></h2></div><LayoutSwitch/><WorkFeed :works="collection.works" :collection-id="collection.id"/><p v-if="!collection.works.length" class="empty-state">暂无可阅读篇目</p></template><p v-else-if="!error" class="empty-state">加载中…</p><ShareCard v-if="shareOpen&&shareContent" :content="shareContent" @close="shareOpen=false"/></div></template>
