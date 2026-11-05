<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {api,message,type Work} from '../api'
import Icon from '../components/Icon.vue'
import WorkFeed from '../components/WorkFeed.vue'
import '../search.css'
type Category='works'|'authors'|'tags'
const route=useRoute(),router=useRouter(),input=ref(String(route.query.q||'')),items=ref<any[]>([]),total=ref(0),loading=ref(false),error=ref('')
const keyword=computed(()=>String(route.query.q||'').trim())
const category=computed<Category>(()=>route.query.type==='authors'?'authors':route.query.type==='tags'?'tags':'works')
const page=computed(()=>Math.max(1,Number(route.query.page)||1))
const tabs:{id:Category;label:string;description:string}[]=[{id:'works',label:'标题与正文',description:'按内容查找文章'},{id:'authors',label:'笔名与账号',description:'按作者查找文章'},{id:'tags',label:'标签与活动',description:'按主题查找文章'}]
let sequence=0
async function load(){const ticket=++sequence;input.value=keyword.value;error.value='';items.value=[];total.value=0;if(!keyword.value){loading.value=false;return}loading.value=true;try{const {data}=await api.get('/public/search',{params:{q:keyword.value,type:category.value,page:page.value}});if(ticket!==sequence)return;items.value=data.items;total.value=data.total}catch(e){if(ticket===sequence)error.value=message(e)}finally{if(ticket===sequence)loading.value=false}}
function go(type:Category=category.value,p=1){void router.push({path:'/search',query:{q:input.value.trim()||undefined,type:type==='works'?undefined:type,page:p>1?p:undefined}})}
watch(()=>route.fullPath,load,{immediate:true})
</script>
<template>
<div class="container search-page">
  <header class="search-hero"><div><p class="eyebrow">DISCOVER HAIYAN</p><h1>在海燕，寻找文字</h1><p>从文章内容、作者或主题中寻找作品。</p></div><form class="search-form" role="search" @submit.prevent="go('works')"><Icon name="Search" :size="20"/><input v-model="input" maxlength="100" aria-label="搜索关键词" placeholder="搜索文章内容、笔名、账号或主题"/><button type="submit">搜索</button></form></header>
  <nav class="search-tabs" aria-label="搜索结果分类"><button v-for="tab in tabs" :key="tab.id" :class="{selected:category===tab.id}" :aria-current="category===tab.id?'page':undefined" @click="input=keyword;go(tab.id)"><strong>{{tab.label}}</strong><small>{{tab.description}}</small></button></nav>
  <section class="search-results" aria-live="polite"><div class="search-results-heading"><div><p class="eyebrow">SEARCH RESULTS</p><h2>{{tabs.find(t=>t.id===category)?.label}}</h2></div><span v-if="keyword&&!loading&&!error">{{total}} 篇文章</span></div>
    <div v-if="!keyword" class="search-state"><Icon name="Search" :size="26"/><h3>想找些什么？</h3><p>输入关键词，发现站内作品。</p></div>
    <div v-else-if="loading" class="search-state">正在搜索…</div><div v-else-if="error" class="search-state"><p>{{error}}</p><button class="button secondary" @click="load">重试</button></div>
    <div v-else-if="!items.length" class="search-state"><h3>没有找到相关结果</h3><p>换个关键词，或看看其他分类。</p></div>
    <WorkFeed v-else :works="items as Work[]" layout="rows"/>
    <div v-if="!loading&&total>12" class="pagination"><button :disabled="page===1" @click="input=keyword;go(category,page-1)">上一页</button><span>{{page}} / {{Math.ceil(total/12)}}</span><button :disabled="page*12>=total" @click="input=keyword;go(category,page+1)">下一页</button></div>
  </section>
</div>
</template>
