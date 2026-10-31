<script setup lang="ts">
import type {Work} from '../api'
defineProps<{work:Work}>()
</script>
<template><RouterLink v-if="work.penNameId&&work.penNameWorkCount>1" :to="`/pen-name/${work.penNameId}`" class="work-byline byline-link" :title="`查看${work.displayByline}的作品`">{{work.displayByline}}</RouterLink><span v-else class="work-byline">{{work.displayByline}}</span></template>
