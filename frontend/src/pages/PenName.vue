<script setup lang="ts">
import {ref,onMounted} from 'vue'
import {useRoute} from 'vue-router'
import {api,message,type Work,type PenName} from '../api'
import WorkFeed from '../components/WorkFeed.vue'
import LayoutSwitch from '../components/LayoutSwitch.vue'
const route=useRoute(),pen=ref<PenName|null>(null),works=ref<Work[]>([]),total=ref(0),page=ref(1),loading=ref(true),error=ref('')
async function load(target=1){loading.value=true;error.value='';try{const result=await api.get('/public/works',{params:{penName:route.params.id,page:target}});works.value=result.data.items;total.value=result.data.total;page.value=target}catch(e){error.value=message(e)}finally{loading.value=false}}
onMounted(async()=>{try{pen.value=(await api.get(`/public/pen-names/${route.params.id}`)).data;await load()}catch(e){error.value=message(e);loading.value=false}})
</script>
<template><div class="container page-space pen-name-page"><RouterLink to="/explore" class="back-link">← 作品书架</RouterLink><header v-if="pen" class="pen-name-heading"><p class="eyebrow">笔名 / 作品</p><h1 class="page-title">{{pen.name}}</h1><p>以这个笔名发表的 {{pen.workCount}} 篇作品</p></header><p v-if="error" role="alert" class="error-message">{{error}}</p><LayoutSwitch v-if="pen"/><div v-if="loading" class="empty-state">加载中…</div><WorkFeed v-else-if="works.length" :works="works"/><div v-else-if="!error" class="empty-state">暂无公开作品</div><div v-if="total>12" class="pagination"><button :disabled="loading||page===1" @click="load(page-1)">上一页</button><span>{{page}} / {{Math.ceil(total/12)}}</span><button :disabled="loading||page*12>=total" @click="load(page+1)">下一页</button></div></div></template>
