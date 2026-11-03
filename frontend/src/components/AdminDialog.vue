<script setup lang="ts">
import {ref,onMounted,onBeforeUnmount} from 'vue'
import Icon from './Icon.vue'
defineProps<{title:string;wide?:boolean;busy?:boolean}>()
const emit=defineEmits<{close:[]}>(),dialog=ref<HTMLDialogElement|null>(null)
let previousFocus:HTMLElement|null=null
onMounted(()=>{previousFocus=document.activeElement as HTMLElement;dialog.value?.showModal()})
onBeforeUnmount(()=>{dialog.value?.close();previousFocus?.focus()})
</script>
<template><Teleport to="body"><dialog ref="dialog" class="admin-dialog" :class="{'admin-dialog-wide':wide}" :aria-label="title" @cancel.prevent="!busy&&emit('close')" @click="e=>{if(e.target===dialog&&!busy)emit('close')}"><div class="admin-dialog-inner"><header><div><p class="admin-kicker">社务台</p><h2>{{title}}</h2></div><button type="button" class="icon-button" :disabled="busy" aria-label="关闭弹窗" @click="emit('close')"><Icon name="X"/></button></header><slot/></div></dialog></Teleport></template>
