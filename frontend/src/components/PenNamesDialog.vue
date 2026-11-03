<script setup lang="ts">
import {ref} from 'vue'
import type {PenName} from '../api'
import ManagementDialog from './ManagementDialog.vue'
import PenNamesPanel from './PenNamesPanel.vue'
const emit=defineEmits<{close:[];change:[PenName[]]}>(),busy=ref(false)
</script>
<template><ManagementDialog title="管理绑定笔名" :busy="busy" @close="emit('close')"><PenNamesPanel @busy="busy=$event" @change="emit('change',$event)"/></ManagementDialog></template>
