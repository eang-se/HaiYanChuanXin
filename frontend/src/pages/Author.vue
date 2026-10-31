<script setup lang="ts">
import {ref,onMounted} from 'vue'
import {useRoute} from 'vue-router'
import {api,message,type Work} from '../api'
import WorkFeed from '../components/WorkFeed.vue'
import LayoutSwitch from '../components/LayoutSwitch.vue'
import Avatar from '../components/Avatar.vue'
import VerifiedBadge from '../components/VerifiedBadge.vue'
const route=useRoute(),author=ref<any>(null),works=ref<Work[]>([]),error=ref(''),page=ref(1),total=ref(0),loading=ref(true)
async function load(target=1){loading.value=true;error.value='';try{const w=await api.get('/public/works',{params:{author:route.params.id,page:target}});works.value=w.data.items;total.value=w.data.total;page.value=target}catch(e){error.value=message(e)}finally{loading.value=false}}
onMounted(async()=>{try{author.value=(await api.get(`/public/authors/${route.params.id}`)).data;await load()}catch(e){error.value=message(e);loading.value=false}})
</script>
<template><div class="container page-space"><p v-if="error" class="error-message">{{error}}</p><header v-if="author" class="author-heading"><div class="author-avatar"><Avatar :src="author.avatarUrl" :name="author.penName" :size="72"/></div><p class="eyebrow">发布账号</p><h1 class="page-title">{{author.penName}} <VerifiedBadge :verified="author.verified||author.accountType==='PUBLIC'"/></h1><p v-if="author.signature" class="author-signature">{{author.signature}}</p><p>{{author.bio||'暂无简介'}}</p></header><RouterLink :to="{path:'/collections',query:{author:route.params.id}}" class="text-button">查看作者合集 →</RouterLink><div class="section-heading"><h2>公开作品<span class="section-count">{{total}} 篇</span></h2></div><LayoutSwitch/><div v-if="loading" class="empty-state">加载中…</div><WorkFeed v-else :works="works"/><div v-if="author&&!loading&&!error&&!works.length" class="empty-state">暂无公开作品</div><div v-if="total>12" class="pagination"><button :disabled="loading||page===1" @click="load(page-1)">上一页</button><span>{{page}} / {{Math.ceil(total/12)}}</span><button :disabled="loading||page*12>=total" @click="load(page+1)">下一页</button></div></div></template>
