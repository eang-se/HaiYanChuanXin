<script setup lang="ts">
import {ref,onMounted} from 'vue'
import {api,message,type Tag} from '../api'
import Icon from '../components/Icon.vue'
const tags=ref<Tag[]>([]),error=ref('');onMounted(async()=>{try{tags.value=(await api.get('/public/tags')).data.filter((t:Tag)=>t.isActivity)}catch(e){error.value=message(e)}})
</script>
<template><div class="container page-space"><p class="eyebrow">文学社 / 活动</p><h1 class="page-title">征稿与活动</h1><p class="page-description">选择主题，查看征稿与投稿。</p><p v-if="error" class="error-message">{{error}}</p><div class="activity-grid"><article class="activity-card" v-for="(t,i) in tags" :key="t.id"><span class="activity-index">0{{i+1}} / 征稿主题</span><Icon name="Feather" :size="36"/><h2>{{t.name}}</h2><p>{{t.description}}</p><div class="activity-links"><RouterLink :to="{path:'/write',query:{tag:t.id}}" class="button">参与写作<Icon name="ArrowUpRight" :size="16"/></RouterLink><RouterLink :to="{path:'/explore',query:{tag:t.id}}">阅读 {{t.workCount}} 篇作品 →</RouterLink></div></article></div><p class="quiet-note">投稿时选择对应活动标签即可参与。作品经基础规则检查后公开展示。</p></div></template>
