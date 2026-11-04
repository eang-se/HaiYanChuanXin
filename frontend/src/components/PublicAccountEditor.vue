<script setup lang="ts">
import {reactive,ref} from 'vue'
import {api,message,notify,refreshAuth} from '../api'
import AdminDialog from './AdminDialog.vue'
import Avatar from './Avatar.vue'
const props=defineProps<{account:{id:number;username:string;penName:string;bio?:string;avatarUrl?:string}}>()
const emit=defineEmits<{close:[];saved:[]}>()
const form=reactive({penName:props.account.penName,bio:props.account.bio||''})
const avatarUrl=ref(props.account.avatarUrl||''),file=ref<File|null>(null),fileInput=ref<HTMLInputElement|null>(null),busy=ref(false),error=ref('')
function choose(event:Event){file.value=(event.target as HTMLInputElement).files?.[0]||null}
async function upload(){if(!file.value||busy.value)return;busy.value=true;error.value='';try{const data=new FormData();data.append('file',file.value);avatarUrl.value=(await api.post(`/admin/public-accounts/${props.account.id}/avatar`,data)).data.avatarUrl;file.value=null;if(fileInput.value)fileInput.value.value='';await refreshAuth();notify('公用账号头像已更新');emit('saved')}catch(e){error.value=message(e)}finally{busy.value=false}}
async function save(){if(busy.value)return;busy.value=true;error.value='';try{await api.put(`/admin/public-accounts/${props.account.id}/profile`,form);await refreshAuth();notify('公用账号资料已保存');emit('saved');emit('close')}catch(e){error.value=message(e)}finally{busy.value=false}}
</script>
<template><AdminDialog :title="`编辑公用账号 · ${account.username}`" :busy="busy" @close="$emit('close')"><div class="admin-form"><p v-if="error" class="error-message" role="alert">{{error}}</p><section class="public-profile-avatar"><Avatar :src="avatarUrl" :name="form.penName" :size="72"/><div><strong>公用账号头像</strong><p class="admin-muted">支持 PNG、JPEG，最大 5 MB。头像可单独上传保存。</p><input ref="fileInput" type="file" accept="image/png,image/jpeg" aria-label="选择公用账号头像" :disabled="busy" @change="choose"/><button type="button" class="button secondary small" :disabled="!file||busy" @click="upload">{{busy?'处理中…':'上传头像'}}</button></div></section><form @submit.prevent="save"><label>账号昵称<input v-model="form.penName" maxlength="40" required :disabled="busy"/></label><label>账号简介<textarea v-model="form.bio" maxlength="500" rows="4" placeholder="介绍这个公用账号" :disabled="busy"></textarea></label><footer class="admin-form-actions"><button type="button" class="button secondary" :disabled="busy" @click="$emit('close')">关闭</button><button class="button" :disabled="busy">{{busy?'保存中…':'保存资料'}}</button></footer></form></div></AdminDialog></template>
<style scoped>.public-profile-avatar{display:flex;gap:18px;align-items:flex-start;padding:0 0 18px;border-bottom:1px solid #e3e8df}.public-profile-avatar strong{font-size:14px}.public-profile-avatar p{margin:6px 0 10px}.public-profile-avatar input{display:block;max-width:100%;font-size:12px;margin-bottom:10px}</style>
