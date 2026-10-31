<script setup lang="ts">
import {date,type Work} from '../api'
import Icon from './Icon.vue'
import VerifiedBadge from './VerifiedBadge.vue'
import WorkByline from './WorkByline.vue'
import CollectionProperty from './CollectionProperty.vue'
defineProps<{work:Work;index?:number;collectionId?:number}>()
</script>
<template><article class="work-row"><div class="row-genre"><span v-if="collectionId!==undefined" class="row-number">{{String((index||0)+1).padStart(2,'0')}}</span><span class="genre-label">{{work.genre}}</span><span v-if="work.isDemo" class="demo-label">示例</span></div><div class="row-content"><RouterLink :to="{path:`/work/${work.id}`,query:collectionId?{collection:collectionId}:undefined}" class="card-title">{{work.title||'未命名草稿'}}</RouterLink><p class="row-excerpt">{{work.excerpt}}</p><CollectionProperty :collection="work.collection"/><div class="row-tag-list"><RouterLink v-for="t in work.tags" :key="t.id" :to="{path:'/explore',query:{tag:t.id}}">#{{t.name}}</RouterLink></div><div class="row-meta"><div class="work-credits"><WorkByline :work="work"/><RouterLink :to="`/author/${work.authorId}`" class="publisher-link" :title="`发布账号：${work.authorName}`">{{work.authorName}} <VerifiedBadge :verified="work.authorVerified||work.authorAccountType==='PUBLIC'"/></RouterLink></div><time>{{date(work.publishedAt)}}</time><span>{{work.wordCount}} 字</span><span><Icon name="Heart" :size="13"/>{{work.likes}}</span></div></div><RouterLink v-if="work.cover" :to="{path:`/work/${work.id}`,query:collectionId?{collection:collectionId}:undefined}" class="row-cover"><img :src="work.cover" alt="作品配图" loading="lazy"/></RouterLink></article></template>
