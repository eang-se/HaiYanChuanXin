<script setup lang="ts">
import {ref,onMounted,onBeforeUnmount} from 'vue'
import {user} from '../api'
import IntroHero from './IntroHero.vue'
import Icon from './Icon.vue'
const dialog=ref<HTMLDialogElement|null>(null)
const key='haiyan.welcomeDismissed'
let previousOverflow=''
function dismiss(){try{localStorage.setItem(key,'1')}catch{};dialog.value?.close();document.body.style.overflow=previousOverflow}
onMounted(()=>{let dismissed=false;try{dismissed=localStorage.getItem(key)==='1'}catch{};if(!user.value&&!dismissed){previousOverflow=document.body.style.overflow;dialog.value?.showModal();document.body.style.overflow='hidden'}})
onBeforeUnmount(()=>{if(dialog.value?.open)document.body.style.overflow=previousOverflow})
</script>
<template><dialog ref="dialog" class="welcome-dialog" aria-label="欢迎来到海燕传信" @cancel.prevent="dismiss" @click="($event.target===$event.currentTarget)&&dismiss()"><button class="dialog-close" aria-label="关闭欢迎弹窗" @click="dismiss"><Icon name="X"/></button><IntroHero welcome @action="dismiss"/><div class="welcome-footer"><button class="text-button" @click="dismiss">先逛逛作品</button><RouterLink to="/about" @click="dismiss">关于海燕传信 →</RouterLink></div></dialog></template>
