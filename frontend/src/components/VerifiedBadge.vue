<script setup lang="ts">
defineProps<{verified?:boolean}>()
</script>
<template><span v-if="verified" class="verified-badge" title="已认证公用账号">认证</span></template>
