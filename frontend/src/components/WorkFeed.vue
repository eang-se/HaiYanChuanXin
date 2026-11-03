<script setup lang="ts">
import {feedLayout,type FeedLayout} from '../composables/feedLayout'
import type {Work} from '../api'
import WorkCard from './WorkCard.vue'
import WorkRow from './WorkRow.vue'
defineProps<{works:Work[];collectionId?:number;layout?:FeedLayout}>()
</script>
<template><div :class="(layout??feedLayout)==='rows'?'works-rows':'works-grid'"><component :is="(layout??feedLayout)==='rows'?WorkRow:WorkCard" v-for="(w,i) in works" :key="w.id" :work="w" :index="i" :collection-id="collectionId"/></div></template>
