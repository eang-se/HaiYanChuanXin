<script setup lang="ts">
import {reactive,ref} from 'vue'
import {api,user,refreshAuth,message,notify} from '../api'
import Avatar from './Avatar.vue'
const emit=defineEmits<{saved:[]}>(),busy=ref(false),error=ref('')
const form=reactive({penName:user.value?.penName||user.value?.username||'',signature:user.value?.signature||'',bio:user.value?.bio||''})
async function save(){busy.value=true;error.value='';try{await api.put('/member/profile',form);await refreshAuth();notify('个人资料已保存');emit('saved')}catch(e){error.value=message(e)}finally{busy.value=false}}
</script>
<template><form class="profile-form" @submit.prevent="save"><div class="profile-avatar-row"><Avatar :src="user?.avatarUrl" :name="form.penName||user?.username" :size="72"/><p class="quiet-note">{{user?.avatarUrl?'头像来自注册时的 QQ 号':'未关联 QQ 号，显示默认头像'}}</p></div><label>账号昵称<input v-model="form.penName" maxlength="40" :placeholder="user?.username" :disabled="busy"/></label><label>签名<input v-model="form.signature" maxlength="120" placeholder="最多 120 字" :disabled="busy"/></label><label>简介<textarea v-model="form.bio" maxlength="300" rows="3" :disabled="busy"></textarea></label><p v-if="error" class="error-message" role="alert">{{error}}</p><button class="button" :disabled="busy">{{busy?'保存中…':'保存资料'}}</button></form></template>
