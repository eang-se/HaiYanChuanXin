<script setup lang="ts">
import {ref,watch,nextTick,onBeforeUnmount} from 'vue'
import {api,message,type Work} from '../api'
import Avatar from './Avatar.vue'
const props=defineProps<{work:Work}>()
interface Account {id:number;penName:string;avatarUrl:string;bound?:boolean}
const open=ref(false),loading=ref(false),error=ref(''),accounts=ref<Account[]>([])
const trigger=ref<HTMLButtonElement|null>(null),panel=ref<HTMLElement|null>(null),position=ref({left:'0px',top:'0px'})
function place(){
 if(!trigger.value||!panel.value)return
 const anchor=trigger.value.getBoundingClientRect(),box=panel.value.getBoundingClientRect(),margin=8,gap=6
 const left=Math.max(margin,Math.min(anchor.left,window.innerWidth-box.width-margin))
 const below=anchor.bottom+gap,above=anchor.top-box.height-gap
 const top=below+box.height<=window.innerHeight-margin?below:Math.max(margin,above)
 position.value={left:`${left}px`,top:`${top}px`}
}
function outside(event:PointerEvent){const target=event.target as Node;if(!trigger.value?.contains(target)&&!panel.value?.contains(target))open.value=false}
function escape(event:KeyboardEvent){if(event.key==='Escape'){open.value=false;trigger.value?.focus()}}
watch(open,async value=>{
 if(value){await nextTick();if(!open.value)return;place();document.addEventListener('pointerdown',outside);document.addEventListener('keydown',escape);window.addEventListener('resize',place);window.addEventListener('scroll',place,true)}
 else cleanup()
})
function cleanup(){document.removeEventListener('pointerdown',outside);document.removeEventListener('keydown',escape);window.removeEventListener('resize',place);window.removeEventListener('scroll',place,true)}
onBeforeUnmount(cleanup)
async function show(){
 if(open.value){open.value=false;return}
 open.value=true;error.value='';accounts.value=[]
 if(!props.work.penNameId){accounts.value=[{id:props.work.authorId,penName:props.work.authorName,avatarUrl:props.work.authorAvatar}];return}
 loading.value=true
 try{accounts.value=(await api.get(`/public/pen-names/${props.work.penNameId}`)).data.accounts||[]}
 catch(e){if(props.work.status!=='PUBLISHED')accounts.value=[{id:props.work.authorId,penName:props.work.authorName,avatarUrl:props.work.authorAvatar}];else error.value=message(e)}finally{loading.value=false;await nextTick();if(open.value)place()}
}
</script>
<template>
 <span v-if="work.bylineMode==='ANONYMOUS'" class="work-byline">佚名</span><span v-else-if="work.bylineMode==='TEMPORARY'" class="work-byline">{{work.displayByline}}</span><button v-else ref="trigger" type="button" class="work-byline byline-link" :title="`查看使用${work.displayByline}的账号`" :aria-expanded="open" aria-haspopup="dialog" @click="show">{{work.displayByline}}</button>
 <Teleport to="body"><section v-if="open" ref="panel" class="pen-dialog" :style="position" role="dialog" :aria-label="`笔名 ${work.displayByline}`"><div class="pen-dialog-header"><h2>{{work.displayByline}}</h2><button type="button" class="pen-dialog-close" aria-label="关闭" @click="open=false">×</button></div><p class="pen-dialog-caption">绑定或使用过此笔名的账号</p><p v-if="loading" class="quiet-note">加载中…</p><p v-else-if="error" class="error-message" role="alert">{{error}}</p><div v-else class="pen-account-list"><RouterLink v-for="account in accounts" :key="account.id" :to="`/author/${account.id}`" class="pen-account" :class="{'is-bound':account.bound}" @click="open=false"><Avatar :src="account.avatarUrl" :name="account.penName" :size="30"/><span class="pen-account-name">{{account.penName}}</span><span v-if="account.bound" class="pen-account-bound">已绑定</span></RouterLink><p v-if="!accounts.length" class="quiet-note">暂无关联账号</p></div></section></Teleport>
</template>
