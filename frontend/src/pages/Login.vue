<script setup lang="ts">
import {reactive,ref,onMounted,onBeforeUnmount} from 'vue'
import {useRouter,useRoute} from 'vue-router'
import {api,refreshAuth,message,notify,identityPrompt} from '../api'
import Icon from '../components/Icon.vue'
import Avatar from '../components/Avatar.vue'
const router=useRouter(),route=useRoute(),register=ref(route.query.mode==='register'),recovering=ref(false),busy=ref(false),sending=ref(false),error=ref(''),seconds=ref(0)
const canRemember=location.protocol==='https:'||['localhost','127.0.0.1','[::1]','::1'].includes(location.hostname)
const rememberDevice=ref(canRemember),remembered=ref<{username:string;penName:string;avatarUrl:string}|null>(null),showPassword=ref(false)
const options=ref({enabled:true,testMode:true}),form=reactive({username:'',password:'',qq:'',code:'',channel:'email'})
let timer:ReturnType<typeof setInterval>|undefined
onMounted(async()=>{try{options.value=(await api.get('/auth/registration-options')).data}catch(e){error.value=message(e)}try{const result=(await api.get('/auth/remembered')).data;remembered.value=result?.username?result:null}catch{/* 快捷登录不可用时仍可用密码登录 */}})
onBeforeUnmount(()=>clearInterval(timer))
async function send(){sending.value=true;error.value='';try{const r=await api.post('/auth/verification-code',{qq:form.qq,channel:form.channel});notify(r.data.message);seconds.value=r.data.retryAfter;clearInterval(timer);timer=setInterval(()=>{if(seconds.value>0)seconds.value--;else clearInterval(timer)},1000)}catch(e){error.value=message(e)}finally{sending.value=false}}
async function sendReset(){sending.value=true;error.value='';try{const r=await api.post('/auth/password-reset/code',{username:form.username,channel:form.channel});notify(r.data.message);seconds.value=r.data.retryAfter;clearInterval(timer);timer=setInterval(()=>{if(seconds.value>0)seconds.value--;else clearInterval(timer)},1000)}catch(e){error.value=message(e)}finally{sending.value=false}}
async function resetPassword(){busy.value=true;error.value='';try{await api.post('/auth/password-reset',{username:form.username,code:form.code,password:form.password});notify('密码已重设，请登录');recovering.value=false;form.password='';form.code=''}catch(e){error.value=message(e)}finally{busy.value=false}}
async function afterLogin(){await refreshAuth();try{identityPrompt.value=(await api.get('/auth/identities')).data.identities.length>1}catch{/* 登录成功时不因身份列表失败而阻断 */}const next=String(route.query.next||'/account');await router.push(next.startsWith('/')&&!next.startsWith('//')?next:'/account')}
async function quickLogin(){busy.value=true;error.value='';try{await api.post('/auth/quick-login');await afterLogin()}catch(e){error.value=message(e);showPassword.value=true}finally{busy.value=false}}
async function forgetDevice(){busy.value=true;error.value='';try{await api.post('/auth/remembered/forget');remembered.value=null;showPassword.value=true;notify('已移除本设备快捷登录')}catch(e){error.value=message(e)}finally{busy.value=false}}
async function submit(){busy.value=true;error.value='';try{
 if(register.value){await api.post('/auth/register',form);notify('注册成功')}
 await api.post('/auth/login',{username:form.username,password:form.password,remember:rememberDevice.value});await afterLogin()
}catch(e){error.value=message(e)}finally{busy.value=false}}
</script>
<template>
<div class="auth-layout container">
 <section class="auth-aside"><p class="eyebrow">海燕传信</p><h1>账号入口</h1><p>投稿、阅读与作品管理</p><span>校内文学社</span></section>
 <section class="auth-panel">
  <div class="auth-tabs"><button :class="{selected:!register}" @click="register=false;recovering=false;error=''">登录</button><button :class="{selected:register}" @click="register=true;recovering=false;error=''">注册</button></div>
  <h2>{{recovering?'找回密码':register?'注册账号':'登录'}}</h2>
  <p v-if="register&&options.testMode" class="test-mode-note">测试注册：QQ 号填 12345，验证码填 0000，可重复使用。</p>
  <p v-if="register&&!options.enabled" class="error-message">注册暂未开放</p>
  <form v-if="recovering" @submit.prevent="resetPassword">
   <label>账号<input v-model="form.username" autocomplete="username" required minlength="3" maxlength="30" placeholder="请输入账号"/></label>
   <p class="quiet-note">验证码发送至该账号绑定的 QQ 邮箱。未绑定 QQ 的账号请联系管理员。</p>
   <p v-if="options.testMode" class="quiet-note">测试 QQ 号 12345 使用验证码 0000。</p>
   <label>验证码<div class="verification-row"><input v-model="form.code" inputmode="numeric" autocomplete="one-time-code" required maxlength="6" placeholder="请输入验证码"/><button type="button" class="button secondary" :disabled="sending||seconds>0||!form.username" @click="sendReset">{{seconds>0?seconds+' 秒':sending?'发送中…':'获取验证码'}}</button></div></label>
   <label>新密码<input v-model="form.password" type="password" autocomplete="new-password" required minlength="10" placeholder="至少 10 位"/></label>
   <p v-if="error" class="error-message" role="alert">{{error}}</p>
   <button class="button full-width" :disabled="busy">{{busy?'请稍候…':'重设密码'}}</button>
   <button type="button" class="text-button" @click="recovering=false;error=''">返回登录</button>
  </form>
  <div v-else-if="!register&&remembered&&!showPassword" class="quick-login">
   <button class="quick-login-account" type="button" :disabled="busy" @click="quickLogin"><Avatar :src="remembered.avatarUrl" :name="remembered.penName" :size="56"/><span><strong>{{remembered.penName}}</strong><small>{{remembered.username}} · 个人账号</small></span><Icon name="ArrowRight" :size="19"/></button>
   <p class="quiet-note">点击账号快捷登录，之后可选择已授权的身份。</p><p v-if="error" class="error-message" role="alert">{{error}}</p>
   <div class="quick-login-actions"><button type="button" class="text-button" @click="showPassword=true;error=''">使用其他账号</button><button type="button" class="text-button" :disabled="busy" @click="forgetDevice">移除本设备记录</button></div>
  </div>
  <form v-else @submit.prevent="submit">
   <label>账号<input v-model="form.username" autocomplete="username" required minlength="3" maxlength="30" :pattern="register?'[A-Za-z0-9_]{3,30}':undefined" placeholder="3～30 位字母、数字或下划线"/></label>
   <label>密码<input v-model="form.password" type="password" :autocomplete="register?'new-password':'current-password'" required :minlength="register?10:undefined" :placeholder="register?'至少 10 位':'请输入密码'"/></label>
   <label class="checkbox-label remember-device"><input v-model="rememberDevice" type="checkbox" :disabled="!canRemember"/>记住此设备，下次快捷登录</label>
   <p v-if="!canRemember" class="quiet-note">快捷登录需要本机地址或 HTTPS。</p>
   <template v-if="register">
    <label>邀请码（QQ 号）<input v-model="form.qq" inputmode="numeric" required pattern="[1-9][0-9]{4,11}" maxlength="12" placeholder="请输入已登记的 QQ 号"/></label>
    <p class="quiet-note">每个正式 QQ 号只能注册一个账号；验证码将发送至对应的 QQ 邮箱。</p>
    <label>验证码<div class="verification-row"><input v-model="form.code" inputmode="numeric" autocomplete="one-time-code" required maxlength="6" placeholder="请输入验证码"/><button type="button" class="button secondary" :disabled="sending||seconds>0||!form.qq||!options.enabled" @click="send">{{seconds>0?seconds+' 秒':sending?'发送中…':'获取验证码'}}</button></div></label>
   </template>
   <p v-if="error" class="error-message" role="alert">{{error}}</p>
   <button class="button full-width" :disabled="busy||(register&&!options.enabled)">{{busy?'请稍候…':register?'注册并登录':'登录'}}<Icon name="ArrowRight" :size="16"/></button>
   <button v-if="!register&&remembered&&showPassword" type="button" class="text-button" @click="showPassword=false;error=''">返回快捷登录</button>
  </form>
  <p v-if="!recovering" class="auth-note"><span v-if="register">注册后可设置笔名和签名，头像使用注册时的 QQ 号。</span><button v-else type="button" class="text-button" @click="recovering=true;form.code='';form.password='';error=''">忘记密码？</button></p>
 </section>
</div>
</template>
