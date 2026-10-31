<script setup lang="ts">
import {ref,computed,watch} from 'vue'
import {useRoute} from 'vue-router'
import {api,user,message} from '../api'
import {feedLayout} from '../composables/feedLayout'
import LayoutSwitch from '../components/LayoutSwitch.vue'
import CollectionCard from '../components/CollectionCard.vue'
const route=useRoute(),mine=computed(()=>route.query.mine==='1'),items=ref<any[]>([]),total=ref(0),page=ref(1),loading=ref(false),error=ref('')
async function load(p=1){loading.value=true;error.value='';try{const r=await api.get(mine.value?'/member/collections':'/public/collections',{params:{page:p,author:route.query.author}});items.value=mine.value?r.data:r.data.items;total.value=mine.value?r.data.length:r.data.total;page.value=p}catch(e){error.value=message(e)}finally{loading.value=false}}
watch(()=>route.query,()=>load(),{immediate:true})
</script>
<template><div class="container page-space"><div class="section-heading"><div><p class="eyebrow">文学社 / 合集</p><h1 class="page-title">{{mine?'我的合集':'合集'}}</h1></div><RouterLink v-if="user" to="/collection-edit" class="button">新建合集</RouterLink></div><LayoutSwitch/><p v-if="error" class="error-message">{{error}}</p><div v-if="loading" class="empty-state">加载中…</div><div v-else :class="feedLayout==='rows'?'collections-rows':'collection-grid'"><CollectionCard v-for="(c,i) in items" :key="c.id" :collection="c" :index="(page-1)*12+i" :rows="feedLayout==='rows'"/></div><p v-if="!loading&&!error&&!items.length" class="empty-state">暂无合集</p><div v-if="!mine&&total>12" class="pagination"><button :disabled="loading||page===1" @click="load(page-1)">上一页</button><span>{{page}} / {{Math.ceil(total/12)}}</span><button :disabled="loading||page*12>=total" @click="load(page+1)">下一页</button></div></div></template>
