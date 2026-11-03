<script setup lang="ts">
import {computed} from 'vue'
import type {Tag} from '../api'
import Icon from './Icon.vue'
const props=defineProps<{tags:Tag[]}>()
const ordered=computed(()=>[...props.tags].sort((a,b)=>Number(!!b.isActivity)-Number(!!a.isActivity)))
</script>
<template><RouterLink v-for="tag in ordered" :key="tag.id" :to="{path:'/explore',query:{tag:tag.id}}" :class="{'activity-tag-link':tag.isActivity,'editorial-tag-link':tag.name==='海燕精选'}" :aria-label="`${tag.isActivity?'活动':tag.name==='海燕精选'?'编辑部推荐':'内容标签'}：${tag.name}`"><Icon v-if="tag.isActivity" name="Feather" :size="11"/><span>{{tag.isActivity?'活动 · ':tag.name==='海燕精选'?'':'#'}}{{tag.name}}</span></RouterLink></template>
<style>
.card-tags>a.activity-tag-link,.row-tag-list>a.activity-tag-link,.reading-tags>a.activity-tag-link{display:inline-flex;align-items:center;gap:4px;color:#527657;background:#eef4e9;border:1px solid #dce7d4;border-radius:4px;padding:1px 6px;overflow-wrap:anywhere}
.card-tags>a.editorial-tag-link,.row-tag-list>a.editorial-tag-link,.reading-tags>a.editorial-tag-link{color:#967e48}
.reading-tags{flex-wrap:wrap;align-items:center}
</style>
