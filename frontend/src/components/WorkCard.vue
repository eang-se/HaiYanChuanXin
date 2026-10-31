<script setup lang="ts">
import type { Work } from '../api'
import {date} from '../api'
import Icon from './Icon.vue'
import VerifiedBadge from './VerifiedBadge.vue'
import WorkByline from './WorkByline.vue'
import CollectionProperty from './CollectionProperty.vue'
defineProps<{work:Work;index?:number;collectionId?:number}>()
</script>
<template><article class="work-card"><div v-if="work.cover" class="card-cover"><img :src="work.cover" alt="作品配图" loading="lazy"/></div><div class="card-top"><span class="genre-label">{{work.genre}}</span><span class="demo-label" v-if="work.isDemo">示例作品</span><span class="card-number">{{String((index||0)+1).padStart(2,'0')}}</span></div><RouterLink :to="{path:`/work/${work.id}`,query:collectionId?{collection:collectionId}:undefined}" class="card-title">{{work.title||'未命名草稿'}}</RouterLink><p class="card-excerpt">{{work.excerpt}}</p><CollectionProperty :collection="work.collection"/><div class="card-tags"><RouterLink v-for="tag in work.tags" :key="tag.id" :to="{path:'/explore',query:{tag:tag.id}}">#{{tag.name}}</RouterLink></div><div class="card-bottom"><div class="work-credits"><WorkByline :work="work"/><RouterLink :to="`/author/${work.authorId}`" class="publisher-link" :title="`发布账号：${work.authorName}`">{{work.authorName}} <VerifiedBadge :verified="work.authorVerified||work.authorAccountType==='PUBLIC'"/></RouterLink></div><div class="card-meta"><span>{{date(work.publishedAt)}}</span><span><Icon name="Heart" :size="13"/>{{work.likes}}</span></div></div></article></template>
