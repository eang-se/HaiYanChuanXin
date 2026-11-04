<script setup lang="ts">
import {inject} from 'vue'
import {openDraftKey} from '../article/newDraft'
const props=defineProps<{tag?:number}>()
const openDraft=inject(openDraftKey)
function follow(event:MouseEvent,navigate:(event:MouseEvent)=>unknown){
  if(openDraft&&window.matchMedia('(max-width: 760px)').matches&&!event.defaultPrevented&&event.button===0&&!event.metaKey&&!event.ctrlKey&&!event.shiftKey&&!event.altKey){event.preventDefault();openDraft(props.tag);return}
  void navigate(event)
}
</script>
<template><RouterLink :to="{path:'/write',query:tag?{tag}:undefined}" custom v-slot="{href,navigate}"><a :href="href" @click="follow($event,navigate)"><slot/></a></RouterLink></template>
