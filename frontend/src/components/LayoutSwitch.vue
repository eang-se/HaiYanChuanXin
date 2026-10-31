<script setup lang="ts">
import {feedLayout} from '../composables/feedLayout'
</script>
<template><div class="feed-controls"><div class="view-switch" role="group" aria-label="列表展示方式"><button :class="{selected:feedLayout==='rows'}" :aria-pressed="feedLayout==='rows'" @click="feedLayout='rows'">横条</button><button :class="{selected:feedLayout==='cards'}" :aria-pressed="feedLayout==='cards'" @click="feedLayout='cards'">卡片</button></div></div></template>
