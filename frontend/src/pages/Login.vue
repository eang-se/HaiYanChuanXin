<script setup lang="ts">
import {reactive,ref,onMounted,onBeforeUnmount} from 'vue'
import {useRouter,useRoute} from 'vue-router'
import {api,refreshAuth,message,notify} from '../api'
import Icon from '../components/Icon.vue'
const router=useRouter(),route=useRoute(),register=ref(false),busy=ref(false),sending=ref(false),error=ref(''),seconds=ref(0)
const options=ref({enabled:true,testMode:true}),form=reactive({username:'',password:'',qq:'',code:'',channel:'email'})
let timer:ReturnType<typeof setInterval>|undefined
onMounted(async()=>{try{options.value=(await api.get('/auth/registration-options')).data}catch(e){error.value=message(e)}})
onBeforeUnmount(()=>clearInterval(timer))
async function send(){sending.value=true;error.value='';try{const r=await api.post('/auth/verification-code',{qq:form.qq,channel:form.channel});notify(r.data.message);seconds.value=r.data.retryAfter;clearInterval(timer);timer=setInterval(()=>{if(seconds.value>0)seconds.value--;else clearInterval(timer)},1000)}catch(e){error.value=message(e)}finally{sending.value=false}}
async function submit(){busy.value=true;error.value='';try{
 if(register.value){await api.post('/auth/register',form);notify('注册成功')}
 await api.post('/auth/login',{username:form.username,password:form.password});await refreshAuth()
 const next=String(route.query.next||'/account');router.push(next.startsWith('/')&&!next.startsWith('//')?next:'/account')
}catch(e){error.value=message(e)}finally{busy.value=false}}
</script>
<template>
<div class="auth-layout container">
 <section class="auth-aside"><p class="eyebrow">海燕传信</p><h1>账号入口</h1><p>投稿、阅读与作品管理</p><span>校内文学社</span></section>
 <section class="auth-panel">
  <div class="auth-tabs"><button :class="{selected:!register}" @click="register=false;error=''">登录</button><button :class="{selected:register}" @click="register=true;error=''">注册</button></div>
  <h2>{{register?'注册账号':'登录'}}</h2>
  <p v-if="register&&options.testMode" class="test-mode-note">测试注册：QQ 号填 12345，验证码填 0000。</p>
  <p v-if="register&&!options.enabled" class="error-message">注册暂未开放</p>
  <form @submit.prevent="submit">
   <label>账号<input v-model="form.username" autocomplete="username" required minlength="3" maxlength="30" :pattern="register?'[A-Za-z0-9_]{3,30}':undefined" placeholder="3～30 位字母、数字或下划线"/></label>
   <label>密码<input v-model="form.password" type="password" :autocomplete="register?'new-password':'current-password'" required :minlength="register?10:undefined" :placeholder="register?'至少 10 位':'请输入密码'"/></label>
   <template v-if="register">
    <label>QQ 号<input v-model="form.qq" inputmode="numeric" required pattern="[1-9][0-9]{4,11}" maxlength="12" placeholder="用于验证注册资格"/></label>
    <label>验证码接收方式<select v-model="form.channel"><option value="email">同名 QQ 邮箱</option><option value="bot">QQ 机器人私信</option></select></label>
    <label>验证码<div class="verification-row"><input v-model="form.code" inputmode="numeric" autocomplete="one-time-code" required maxlength="6" placeholder="请输入验证码"/><button type="button" class="button secondary" :disabled="sending||seconds>0||!form.qq||!options.enabled" @click="send">{{seconds>0?seconds+' 秒':sending?'发送中…':'获取验证码'}}</button></div></label>
   </template>
   <p v-if="error" class="error-message" role="alert">{{error}}</p>
   <button class="button full-width" :disabled="busy||(register&&!options.enabled)">{{busy?'请稍候…':register?'注册并登录':'登录'}}<Icon name="ArrowRight" :size="16"/></button>
  </form>
  <p class="auth-note">{{register?'注册后可在个人中心设置笔名、头像和签名。':'忘记密码请联系管理员。'}}</p>
 </section>
</div>
</template>
