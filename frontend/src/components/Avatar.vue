<script setup lang="ts">
defineProps<{src?:string;name?:string;size?:number}>()
</script>
<template><span class="member-avatar" :style="size?{width:size+'px',height:size+'px',fontSize:Math.round(size*.4)+'px'}:undefined"><img v-if="src" :src="src" :alt="`${name||'用户'}的头像`"/><span v-else>{{name?.slice(0,1)||'文'}}</span></span></template>
