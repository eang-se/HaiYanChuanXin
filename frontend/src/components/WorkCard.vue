<script setup lang="ts">
import type { Work } from '../api'
import {date} from '../api'
import Icon from './Icon.vue'
import Avatar from './Avatar.vue'
defineProps<{work:Work;index?:number}>()
</script>
<template><article class="work-card"><div v-if="work.cover" class="card-cover"><img :src="work.cover" alt="作品配图" loading="lazy"/></div><div class="card-top"><span class="genre-label">{{work.genre}}</span><span class="demo-label" v-if="work.isDemo">示例作品</span><span class="card-number">{{String((index||0)+1).padStart(2,'0')}}</span></div><RouterLink :to="`/work/${work.id}`" class="card-title">{{work.title||'未命名草稿'}}</RouterLink><p class="card-excerpt">{{work.excerpt}}</p><div class="card-tags"><RouterLink v-for="tag in work.tags" :key="tag.id" :class="{'collection-tag':tag.kind==='COLLECTION'}" :to="tag.kind==='COLLECTION'?`/collection/${tag.collectionId}`:{path:'/explore',query:{tag:tag.id}}">#{{tag.name}}</RouterLink></div><div class="card-bottom"><RouterLink :to="`/author/${work.authorId}`" class="author-mini"><Avatar :src="work.authorAvatar" :name="work.authorName" :size="24"/>{{work.authorName}}</RouterLink><div class="card-meta"><span>{{date(work.publishedAt)}}</span><span><Icon name="Heart" :size="13"/>{{work.likes}}</span></div></div></article></template>
