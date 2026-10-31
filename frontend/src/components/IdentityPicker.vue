<script setup lang="ts">
import {ref,watch} from 'vue'
import {useRouter,useRoute} from 'vue-router'
import {api,user,refreshAuth,identityPrompt,message,notify} from '../api'
import Avatar from './Avatar.vue'
interface Identity {id:number;penName:string;avatarUrl:string;accountType:string;active:boolean}
const router=useRouter(),route=useRoute(),open=ref(false),loading=ref(false),busy=ref(false),error=ref(''),identities=ref<Identity[]>([])
async function show(){open.value=true;loading.value=true;error.value='';try{identities.value=(await api.get('/auth/identities')).data.identities}catch(e){error.value=message(e)}finally{loading.value=false}}
watch(identityPrompt,value=>{if(value){identityPrompt.value=false;void show()}},{immediate:true})
async function choose(item:Identity){
 if(item.active){open.value=false;return}
 if(route.path.startsWith('/write')&&!window.confirm('切换身份会离开当前编辑页面，请先保存稿件。继续切换？'))return
 busy.value=true;error.value='';try{await api.post('/auth/identity',{id:item.id});await refreshAuth();open.value=false;window.location.reload()}catch(e){error.value=message(e)}finally{busy.value=false}
}
async function logout(){busy.value=true;error.value='';try{await api.post('/auth/logout');await refreshAuth();open.value=false;notify('已退出登录');await router.push('/')}catch(e){error.value=message(e)}finally{busy.value=false}}
</script>
<template>
 <button class="profile-entry identity-trigger" type="button" title="账号与身份" aria-label="账号与身份" @click="show"><Avatar :src="user?.avatarUrl" :name="user?.penName" :size="35"/></button>
 <Teleport to="body"><div v-if="open" class="modal-backdrop identity-backdrop" @click.self="open=false"><section class="identity-dialog" role="dialog" aria-modal="true" aria-labelledby="identity-title"><div class="identity-dialog-header"><div><h2 id="identity-title">切换身份</h2><p>选择个人账号或已授权的公用账号</p></div><button type="button" class="identity-close" aria-label="关闭" @click="open=false">×</button></div><p v-if="loading" class="quiet-note">加载中…</p><p v-if="error" class="error-message" role="alert">{{error}}</p><div v-if="!loading" class="identity-list"><button v-for="item in identities" :key="item.id" type="button" class="identity-option" :class="{active:item.active}" :disabled="busy" @click="choose(item)"><Avatar :src="item.avatarUrl" :name="item.penName" :size="44"/><span class="identity-option-copy"><strong>{{item.penName}} <span v-if="item.accountType==='PUBLIC'" class="verified-badge">认证</span></strong><small>{{item.accountType==='PUBLIC'?'公用账号':'个人账号'}}</small></span><span v-if="item.active" class="identity-selected" aria-label="当前身份">✓</span></button></div><div class="identity-dialog-footer"><RouterLink v-if="user?.accountType!=='PUBLIC'" to="/profile" class="text-button" @click="open=false">个人中心</RouterLink><button type="button" class="text-button" :disabled="busy" @click="logout">退出登录</button></div></section></div></Teleport>
</template>
