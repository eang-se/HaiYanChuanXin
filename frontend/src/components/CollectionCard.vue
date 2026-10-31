<script setup lang="ts">
defineProps<{collection:any;index:number;rows?:boolean}>()
</script>
<template><RouterLink :to="`/collection/${collection.id}`" class="collection-card" :class="{'collection-row':rows}"><span class="collection-spine">{{String(index+1).padStart(2,'0')}}</span><div><span class="genre-label">{{collection.kind}}{{!collection.published?' · 私密':''}}</span><h2>{{collection.title}}</h2><p>{{collection.description||'暂无简介'}}</p><small>{{collection.authorName}} · {{collection.workCount}} 篇已发布</small></div></RouterLink></template>
