import {ref,watch} from 'vue'
export type FeedLayout='cards'|'rows'
export const feedLayout=ref<FeedLayout>('rows')
try{if(localStorage.getItem('haiyan.feedLayout')==='cards')feedLayout.value='cards'}catch{}
watch(feedLayout,value=>{try{localStorage.setItem('haiyan.feedLayout',value)}catch{}})
window.addEventListener('storage',event=>{if(event.key==='haiyan.feedLayout')feedLayout.value=event.newValue==='rows'?'rows':'cards'})
