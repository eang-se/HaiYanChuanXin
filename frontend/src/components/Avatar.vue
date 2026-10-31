<script setup lang="ts">
import {ref,watch} from 'vue'
const props=defineProps<{src?:string;name?:string;size?:number}>()
const failed=ref(false)
watch(()=>props.src,()=>{failed.value=false})
</script>
<template><span class="member-avatar" :style="size?{width:size+'px',height:size+'px',fontSize:Math.round(size*.4)+'px'}:undefined"><img v-if="src&&!failed" :src="src" :alt="`${name||'用户'}的头像`" @error="failed=true"/><span v-else>{{name?.slice(0,1)||'文'}}</span></span></template>
