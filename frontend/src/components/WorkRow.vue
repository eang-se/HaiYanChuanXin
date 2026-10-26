<script setup lang="ts">
import {date,type Work} from '../api'
import Avatar from './Avatar.vue'
import Icon from './Icon.vue'
defineProps<{work:Work}>()
</script>
<template><article class="work-row"><div class="row-genre"><span class="genre-label">{{work.genre}}</span><span v-if="work.isDemo" class="demo-label">示例</span></div><div class="row-content"><RouterLink :to="`/work/${work.id}`" class="card-title">{{work.title}}</RouterLink><p class="row-excerpt">{{work.excerpt}}</p><div class="row-tag-list"><RouterLink v-for="t in work.tags" :key="t.id" :class="{'collection-tag':t.kind==='COLLECTION'}" :to="t.kind==='COLLECTION'?`/collection/${t.collectionId}`:{path:'/explore',query:{tag:t.id}}">#{{t.name}}</RouterLink></div><div class="row-meta"><RouterLink :to="`/author/${work.authorId}`" class="author-mini"><Avatar :src="work.authorAvatar" :name="work.authorName" :size="24"/>{{work.authorName}}</RouterLink><time>{{date(work.publishedAt)}}</time><span>{{work.wordCount}} 字</span><span><Icon name="Heart" :size="13"/>{{work.likes}}</span></div></div><RouterLink v-if="work.cover" :to="`/work/${work.id}`" class="row-cover"><img :src="work.cover" alt="作品配图" loading="lazy"/></RouterLink></article></template>
