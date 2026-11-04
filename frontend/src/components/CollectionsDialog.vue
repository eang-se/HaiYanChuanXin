<script setup lang="ts">
import {ref,onMounted} from 'vue'
import {api,message,notify,type CollectionSummary} from '../api'
import ManagementDialog from './ManagementDialog.vue'
import CollectionForm from './CollectionForm.vue'
import Icon from './Icon.vue'
const props=defineProps<{initialId?:number;create?:boolean}>()
const emit=defineEmits<{close:[];saved:[number];deleted:[number]}>()
const items=ref<CollectionSummary[]>([]),selected=ref<number|undefined>(props.initialId),editing=ref(!!props.initialId||!!props.create),busy=ref(false),error=ref(''),loading=ref(true),form=ref<InstanceType<typeof CollectionForm>|null>(null)
async function load(){error.value='';try{items.value=(await api.get('/member/collections')).data}catch(e){error.value=message(e)}finally{loading.value=false}}
function select(id?:number){if(busy.value||(editing.value&&!form.value?.canClose()))return;selected.value=id;editing.value=true}
function close(){if(!busy.value&&(!editing.value||!form.value||form.value.canClose()))emit('close')}
async function remove(){
  if(busy.value||!selected.value)return
  const id=selected.value,name=items.value.find(c=>c.id===id)?.title||'此合集'
  if(!confirm(`确定删除合集《${name}》？合集中的文章会保留，但将解除与合集的关联；未保存的合集修改也会丢失。`))return
  busy.value=true;error.value=''
  try{await api.delete(`/member/collections/${id}`);editing.value=false;selected.value=undefined;emit('deleted',id);await load();notify('合集已删除，文章已保留')}
  catch(e){error.value=message(e)}finally{busy.value=false}
}
async function saved(id:number){busy.value=false;selected.value=id;emit('saved',id);await load()}
onMounted(load)
</script>
<template><ManagementDialog title="创建与管理合集" wide :busy="busy" @close="close"><div class="collection-manager"><nav aria-label="我的合集"><button type="button" class="collection-create" :disabled="busy" @click="select()"><Icon name="Plus" :size="16"/>新建合集</button><p v-if="loading" role="status">正在载入…</p><p v-if="error" role="alert" class="error-message">{{error}} <button @click="load">重试</button></p><button v-for="c in items" :key="c.id" type="button" :disabled="busy" :aria-pressed="selected===c.id" @click="select(c.id)"><span>{{c.title}}</span><small>{{c.published?'公开':'私密'}}</small></button><p v-if="!loading&&!error&&!items.length" class="quiet-note">暂无合集，可以新建。</p></nav><div v-if="editing" class="collection-editor-wrap"><CollectionForm :key="selected||'new'" ref="form" :collection-id="selected" @busy="busy=$event" @saved="saved"/><button v-if="selected" type="button" class="collection-delete" :disabled="busy" @click="remove">{{busy?'处理中…':'删除合集'}}</button></div><p v-else class="quiet-note">选择一部合集，管理简介、公开状态和篇目顺序，或新建合集。</p></div></ManagementDialog></template>
<style scoped>
.collection-manager{display:grid;grid-template-columns:180px minmax(0,1fr);gap:26px}.collection-manager nav{display:flex;flex-direction:column;gap:8px;border-right:1px solid #e5eadf;padding-right:20px}.collection-manager nav button{display:flex;align-items:center;gap:8px;text-align:left;padding:10px;border:1px solid #e1e7db;border-radius:6px;overflow-wrap:anywhere;font-size:13px}.collection-manager nav button span{flex:1;min-width:0}.collection-manager nav button[aria-pressed=true]{background:#edf4e6;border-color:#91a67e}.collection-manager small{font-size:11px;color:#829374}.collection-delete{display:inline-flex;align-items:center;min-height:40px;margin-top:18px;padding:8px 13px;border:1px solid #e6cfc7;border-radius:7px;color:#a05541;font-size:13px}.collection-create{color:#426638;background:#f1f5ed}@media(max-width:640px){.collection-manager{grid-template-columns:minmax(0,1fr);gap:22px}.collection-manager nav{flex-direction:row;flex-wrap:wrap;border-right:0;border-bottom:1px solid #e5eadf;padding:0 0 18px}.collection-manager nav button{max-width:100%}}
</style>
