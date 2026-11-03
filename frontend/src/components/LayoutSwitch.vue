<script setup lang="ts">
import {computed} from 'vue'
import {feedLayout,type FeedLayout} from '../composables/feedLayout'
const props=defineProps<{modelValue?:FeedLayout}>()
const emit=defineEmits<{'update:modelValue':[value:FeedLayout]}>()
const layout=computed({get:()=>props.modelValue??feedLayout.value,set:(value:FeedLayout)=>{if(props.modelValue===undefined)feedLayout.value=value;else emit('update:modelValue',value)}})
</script>
<template><div class="feed-controls"><div class="view-switch" role="group" aria-label="列表展示方式"><button :class="{selected:layout==='rows'}" :aria-pressed="layout==='rows'" @click="layout='rows'">横条</button><button :class="{selected:layout==='cards'}" :aria-pressed="layout==='cards'" @click="layout='cards'">卡片</button></div></div></template>
