<script setup lang="ts">
import {ref,watch} from 'vue'
import type {JSONContent} from '@tiptap/core'
import {api,message} from '../api'

interface Version {id:number;title:string;createdAt:string}
const props=defineProps<{workId:number;disabled?:boolean}>()
const emit=defineEmits<{restore:[value:{body:string;bodyJson?:JSONContent|null}]}>()
const open=ref(false),loading=ref(false),restoring=ref<number|null>(null),error=ref(''),versions=ref<Version[]>([])
async function load(){
  loading.value=true;error.value=''
  try { versions.value=(await api.get(`/member/works/${props.workId}/versions`)).data }
  catch(e){error.value=message(e)}
  finally{loading.value=false}
}
watch(open,value=>{if(value)void load()})
async function restore(version:Version){
  if(restoring.value!==null)return
  restoring.value=version.id;error.value=''
  try { emit('restore',(await api.get(`/member/works/${props.workId}/versions/${version.id}`)).data) }
  catch(e){error.value=message(e)}
  finally{restoring.value=null}
}
function displayTime(value:string){
  const time=new Date(value)
  return Number.isNaN(time.getTime())?value:time.toLocaleString('zh-CN',{dateStyle:'medium',timeStyle:'short'})
}
</script>
<template>
  <section class="panel article-versions">
    <button class="article-versions-toggle" :aria-expanded="open" @click="open=!open">历史版本 <span>{{open?'收起':'查看'}}</span></button>
    <template v-if="open">
      <p class="quiet-note">发布时记录正文。载入后可检查和编辑，保存草稿或再次发布。</p>
      <p v-if="loading" role="status" class="quiet-note">正在载入…</p>
      <p v-else-if="error" role="alert" class="error-message">{{error}} <button class="article-version-action" @click="load">重试</button></p>
      <p v-else-if="!versions.length" class="quiet-note">暂无历史版本</p>
      <ol v-else class="article-versions-list">
        <li v-for="version in versions" :key="version.id">
          <div><time>{{displayTime(version.createdAt)}}</time><span>{{version.title}}</span></div>
          <button class="article-version-action" :disabled="disabled||restoring!==null" @click="restore(version)">{{restoring===version.id?'载入中…':'载入正文'}}</button>
        </li>
      </ol>
    </template>
  </section>
</template>
