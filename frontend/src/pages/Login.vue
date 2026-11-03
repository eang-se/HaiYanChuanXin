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
 <section class="auth-aside"><p class="eyebrow">海燕传信</p><h1>账号入口</h1><p>投稿、阅读与作品管理</p></section>
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
    <div class="registration-agreement">
     <p>点击“注册并登录”，即表示您已阅读并同意《海燕传信用户协议》，包括<strong>授权海燕文学社合理分发和使用您上传的作品</strong>。</p>
     <details><summary>阅读用户协议</summary>
      <div class="agreement-text">
       <h3>海燕传信用户协议</h3>
       <p>欢迎使用海燕文学社运营的海燕传信。注册账号前，请仔细阅读本协议；点击“注册并登录”即表示您同意本协议。</p>
       <p><strong>一、账号与使用。</strong>您应提供真实有效的注册信息，妥善保管账号和密码，并对账号下的行为负责。使用平台时，请遵守法律法规，尊重他人权益，不发布违法、侵权或损害他人合法权益的内容。</p>
       <p><strong>二、作品权利与授权。</strong>您对上传的作品依法享有的著作权仍归您或相关权利人所有。您保证有权上传作品并作出本协议约定的授权。您授予海燕文学社一项全球范围内、免费、非独占的许可，在提供和推广海燕传信及文学社活动所需的合理范围内，对您上传的作品进行存储、复制、展示、汇编、信息网络传播，以及必要的排版、格式转换和宣传推广；文学社可为上述目的委托服务提供方或合作渠道在必要范围内使用作品。使用时应尊重您的署名权和作品完整性。未公开的草稿不会仅因本授权而公开发布。</p>
       <p><strong>三、授权期限与撤回。</strong>授权自作品上传时生效，您可联系文学社管理员申请停止后续使用或下架作品。文学社会在合理期限内处理；已依法留存的备份、已制作或发放的刊物和宣传材料，以及法律法规要求保留的内容，可能无法立即撤回。作品的其他使用方式、收费出版或实质性改编，应另行取得权利人同意。</p>
       <p><strong>四、内容管理。</strong>如发现内容涉嫌违法、侵权或违反本协议，文学社可依法采取提醒、限制展示、下架等措施。权利人或用户可联系文学社管理员，提供相关信息并申请处理。</p>
       <p><strong>五、个人信息。</strong>平台为账号注册、身份验证、作品管理和安全维护等目的处理必要的个人信息。您可联系文学社管理员查询、更正或申请删除个人信息及注销账号；平台将依法处理。</p>
       <p><strong>六、协议更新与争议。</strong>协议更新时，文学社将在平台公布；涉及重大变化的，将依法提示并在需要时重新征求同意。因本协议产生争议，双方可先协商解决；协商不成的，可依法向有管辖权的人民法院提起诉讼。</p>
      </div>
     </details>
    </div>
   </template>
   <p v-if="error" class="error-message" role="alert">{{error}}</p>
   <button class="button full-width" :disabled="busy||(register&&!options.enabled)">{{busy?'请稍候…':register?'注册并登录':'登录'}}<Icon name="ArrowRight" :size="16"/></button>
   <button v-if="!register&&remembered&&showPassword" type="button" class="text-button" @click="showPassword=false;error=''">返回快捷登录</button>
  </form>
  <p v-if="!recovering" class="auth-note"><span v-if="register">注册后可设置笔名和签名，头像使用注册时的 QQ 号。</span><button v-else type="button" class="text-button" @click="recovering=true;form.code='';form.password='';error=''">忘记密码？</button></p>
 </section>
</div>
</template>
<style scoped>
.registration-agreement{margin:16px 0;color:#536b59;font-size:12px;line-height:1.8}.registration-agreement>p{margin:0 0 6px}.registration-agreement summary{color:var(--green);cursor:pointer;text-decoration:underline;text-underline-offset:3px}.registration-agreement summary:focus-visible{outline:2px solid #608a6c;outline-offset:4px}.agreement-text{margin-top:10px;padding:14px;background:#f2f6ef;border:1px solid #dce7da;border-radius:5px;max-height:300px;overflow:auto;color:#465a4a}.agreement-text h3{font-size:14px;margin:0 0 8px}.agreement-text p{margin:0 0 10px}.agreement-text p:last-child{margin-bottom:0}
</style>
