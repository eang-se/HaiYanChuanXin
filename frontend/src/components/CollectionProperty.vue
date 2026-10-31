<script setup lang="ts">
import type {CollectionSummary} from '../api'
defineProps<{collection:CollectionSummary|null}>()
</script>
<template><div v-if="collection" class="work-collection-property"><span>合集</span><RouterLink :to="`/collection/${collection.id}`">{{collection.title}}</RouterLink></div></template>
