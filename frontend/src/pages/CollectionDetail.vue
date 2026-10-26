<script setup lang="ts">
import {ref,onMounted} from 'vue'
import {useRoute} from 'vue-router'
import {api,user,message} from '../api'
const route=useRoute(),collection=ref<any>(null),error=ref('')
onMounted(async()=>{try{collection.value=(await api.get(`/public/collections/${route.params.id}`)).data}catch(e){error.value=message(e)}})
</script>
<template><div class="container page-space collection-detail"><RouterLink to="/collections" class="back-link">← 全部合集</RouterLink><p v-if="error" class="error-message">{{error}}</p><template v-if="collection"><header class="collection-heading"><p class="eyebrow">{{collection.kind}} / {{collection.published?'公开':'仅自己可见'}}</p><h1 class="page-title">{{collection.title}}</h1><RouterLink :to="`/author/${collection.authorId}`">{{collection.authorName}}</RouterLink><p class="collection-description">{{collection.description}}</p><RouterLink v-if="user?.id===collection.authorId" :to="`/collection-edit/${collection.id}`" class="button secondary">编辑合集</RouterLink></header><div class="section-heading"><h2>目录<span class="section-count">{{collection.works.length}} 篇</span></h2></div><ol class="collection-contents"><li v-for="(w,i) in collection.works" :key="w.id"><span>{{String(Number(i)+1).padStart(2,'0')}}</span><RouterLink :to="{path:`/work/${w.id}`,query:{collection:collection.id}}">{{w.title||'未命名稿件'}}</RouterLink><small>{{w.status==='PUBLISHED'?w.genre:'未公开'}} · {{w.wordCount}} 字</small></li></ol><p v-if="!collection.works.length" class="empty-state">暂无可阅读篇目</p></template><p v-else-if="!error" class="empty-state">加载中…</p></div></template>
