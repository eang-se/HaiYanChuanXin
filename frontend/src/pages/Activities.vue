<script setup lang="ts">
import {ref,onMounted} from 'vue'
import {api,message,type Tag} from '../api'
import Icon from '../components/Icon.vue'
import DraftLink from '../components/DraftLink.vue'
const tags=ref<Tag[]>([]),error=ref(''),loading=ref(true)
async function load(){loading.value=true;error.value='';try{tags.value=((await api.get('/public/activities')).data as Tag[]).filter(t=>t.activityActive!==false)}catch(e){error.value=message(e)}finally{loading.value=false}}
onMounted(load)
</script>
<template>
<div class="container page-space activities-page">
  <header class="activities-intro">
    <div><p class="eyebrow">文学社 / 活动</p><h1 class="page-title">征稿与活动</h1><p class="page-description">每一个主题，都是一次提笔的邀请。</p></div>
    <div class="activities-intro-mark" aria-hidden="true"><Icon name="Feather" :size="28"/></div>
  </header>
  <div class="activities-section-heading"><div><span class="activities-dot"></span><h2>正在征稿</h2><span class="activities-count">{{loading?'…':error?'—':tags.length}} 个主题</span></div><RouterLink to="/explore">逛逛文库 <Icon name="ArrowUpRight" :size="14"/></RouterLink></div>
  <div v-if="loading" class="activities-state" role="status">正在收取活动消息…</div>
  <div v-else-if="error" class="activities-state" role="alert"><p>活动暂时无法加载：{{error}}</p><button type="button" @click="load">重新加载</button></div>
  <div v-else-if="!tags.length" class="activities-state"><Icon name="Feather" :size="25"/><strong>下一场相遇，正在酝酿</strong><p>新的征稿主题发布后，会在这里与你见面。</p><RouterLink to="/explore">先去读读来信 <Icon name="ArrowRight" :size="14"/></RouterLink></div>
  <div v-else class="activity-grid"><article v-for="(t,i) in tags" :key="t.id" class="activity-card">
    <div class="activity-card-top"><span class="activity-status"><span></span> 征稿中</span><span class="activity-index">{{String(i+1).padStart(2,'0')}} / 主题</span></div>
    <h2>{{t.name}}</h2><p>{{t.description||'循着这个主题，写下属于你的故事。'}}</p>
    <div class="activity-card-footer"><span>已有 <strong>{{t.workCount||0}}</strong> 篇作品</span><div class="activity-links"><RouterLink :to="{path:'/explore',query:{tag:t.id}}">读作品 <Icon name="ArrowRight" :size="14"/></RouterLink><DraftLink :tag="t.id" class="button">参与写作 <Icon name="ArrowUpRight" :size="15"/></DraftLink></div></div>
  </article></div>
</div>
</template>
<style scoped>
.activities-intro{display:flex;align-items:center;justify-content:space-between;gap:20px}.activities-intro-mark{display:none}.activities-section-heading{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:26px}.activities-section-heading>div,.activities-section-heading>a{display:flex;align-items:center;gap:8px}.activities-section-heading h2{font:500 22px/1.4 var(--serif);margin:0}.activities-section-heading>a{font-size:12px;color:#617b64}.activities-dot,.activity-status>span{width:7px;height:7px;border-radius:50%;background:#6d9770;display:inline-block}.activities-count{font-size:11px;color:#8a998a}.activities-state{margin-top:18px;padding:30px;text-align:center;border:1px dashed #d8e3d5;border-radius:12px;color:#718675;font-size:13px}.activities-state strong{display:block;font:18px var(--serif);margin-top:10px}.activities-state p{margin:8px 0}.activities-state a,.activities-state button{display:inline-flex;align-items:center;gap:5px;color:#32644a;font-size:12px}.activity-grid{margin-top:18px}.activity-card{display:flex;flex-direction:column}.activity-card-top{display:flex;justify-content:space-between;align-items:center;gap:10px;margin-bottom:22px}.activity-index{margin:0}.activity-status{display:inline-flex;align-items:center;gap:6px;color:#597e5e;font-size:11px}.activity-status>span{width:6px;height:6px}.activity-card h2{margin:0 0 10px;overflow-wrap:anywhere}.activity-card p{white-space:pre-line;overflow-wrap:anywhere}.activity-card-footer{margin-top:auto}.activity-card-footer>span{font-size:12px;color:#82917d}.activity-links{margin-top:15px}.activity-links>a:not(.button){display:inline-flex;align-items:center;gap:4px;color:#617c62}.activities-note{display:flex;align-items:center;gap:7px;margin:25px 0 0;color:#90a08b;font-size:12px;line-height:1.7}
@media(max-width:760px){
.activities-page{padding-top:16px;padding-bottom:28px}.activities-intro{position:relative;overflow:hidden;padding:19px 20px;background:#e7efe4;border:1px solid #dce8d9;border-radius:17px}.activities-intro .eyebrow{margin:0 0 7px;letter-spacing:1px;font-size:10px;color:#729077}.activities-intro .page-title{font-size:25px;line-height:1.3;margin:0 0 6px}.activities-intro .page-description{font-size:12px;line-height:1.6;margin:0;color:#708674}.activities-intro-mark{display:grid;place-items:center;flex:none;width:54px;height:54px;border:1px solid #cbdccb;border-radius:18px;color:#709a76;transform:rotate(-8deg)}.activities-section-heading{margin-top:25px}.activities-section-heading h2{font-size:19px}.activities-section-heading>a{font-size:11px}.activity-grid{display:grid;grid-template-columns:minmax(0,1fr);gap:12px;margin:13px 0 0}.activity-card{padding:19px 19px 16px;border:1px solid #e0e9dd;border-radius:16px;background:#fff}.activity-card-top{margin:0 0 15px}.activity-status{padding:4px 8px;border-radius:7px;background:#eaf3e8;font-size:10px}.activity-index{font-size:10px;letter-spacing:1px}.activity-card h2{font-size:22px;line-height:1.4;letter-spacing:.5px;margin:0 0 7px}.activity-card p{min-height:0;margin:0 0 16px;color:#718272;font-size:13px;line-height:1.75}.activity-card-footer{padding-top:13px;border-top:1px solid #edf1eb}.activity-card-footer>span{font-size:11px}.activity-card-footer>span strong{font-size:15px;font-weight:600;color:#376448}.activity-links{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:10px}.activity-links>a:not(.button){min-height:40px;font-size:12px}.activity-links .button{min-height:40px;padding:9px 14px;border-radius:10px;font-size:12px;gap:8px}.activities-note{margin-top:17px;font-size:11px}.activities-state{margin-top:13px;padding:28px 18px;background:#fff}.activities-state strong{font-size:17px}
}
</style>
