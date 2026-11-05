<script setup lang="ts">
import {ref,onMounted,watch} from 'vue'
import {api,message,notify,type PenName} from '../api'
const emit=defineEmits<{change:[PenName[]];busy:[boolean]}>()
const names=ref<PenName[]>([]),name=ref(''),busy=ref(false),error=ref('')
watch(busy,value=>emit('busy',value))
async function load(){names.value=(await api.get('/member/pen-names')).data;emit('change',names.value)}
async function bind(){if(busy.value)return;if([...name.value.trim()].length>10){error.value='笔名最多 10 个字符';return}if(names.value.length>=3){error.value='每个账号最多绑定 3 个笔名';return}busy.value=true;error.value='';try{await api.post('/member/pen-names',{name:name.value});name.value='';await load();notify('笔名已绑定')}catch(e){error.value=message(e)}finally{busy.value=false}}
async function remove(pen:PenName){if(busy.value)return;busy.value=true;error.value='';try{await api.delete(`/member/pen-names/${pen.id}`);await load();notify('笔名已解绑')}catch(e){error.value=message(e)}finally{busy.value=false}}
onMounted(async()=>{try{await load()}catch(e){error.value=message(e)}})
</script>
<template><section class="panel pen-names-panel"><h2>绑定笔名</h2><p class="quiet-note">一个账号最多绑定 3 个笔名，每个最多 10 个字符。绑定后可在投稿时选择；文章仍由发布账号管理，已被他人绑定的笔名只能用作临时署名。</p><form class="pen-name-bind" @submit.prevent="bind"><label for="bind-pen-name">笔名<input id="bind-pen-name" v-model="name" required placeholder="填写笔名或联合署名" :disabled="busy"/></label><button class="button" :disabled="busy||!name.trim()||names.length>=3">绑定</button></form><p v-if="error" class="error-message" role="alert">{{error}}</p><ul class="pen-name-list"><li v-for="pen in names" :key="pen.id"><div><RouterLink v-if="pen.workCount>1" :to="`/pen-name/${pen.id}`">{{pen.name}} →</RouterLink><strong v-else>{{pen.name}}</strong><small>{{pen.workCount}} 篇公开作品</small></div><button class="text-button" :disabled="busy" @click="remove(pen)">解绑</button></li></ul><p v-if="!names.length" class="quiet-note">尚未绑定笔名。</p></section></template>
