<script setup lang="ts">
import DraftLink from '../components/DraftLink.vue'
import {computed,onMounted,onBeforeUnmount,ref} from 'vue'
import {api,date,message,user,type Tag,type Work} from '../api'
import type {FeedLayout} from '../composables/feedLayout'
import Icon from '../components/Icon.vue'
import WorkFeed from '../components/WorkFeed.vue'
import LayoutSwitch from '../components/LayoutSwitch.vue'
import WorkByline from '../components/WorkByline.vue'
import '../home.css'

const works=ref<Work[]>([]),featured=ref<Work[]>([]),random=ref<Work[]>([]),tags=ref<Tag[]>([])
const total=ref<number|null>(null),authors=ref<number|null>(null),feedTotal=ref(0),loading=ref(true),error=ref(''),overviewError=ref(''),randomError=ref(''),randomBusy=ref(false)
const sort=ref('latest'),clock=ref(new Date()),homeLayout=ref<FeedLayout>('cards')
const mobile=ref(window.matchMedia('(max-width: 760px)').matches),mobilePanel=ref<'featured'|'activity'|'random'|'issue'>('featured')
const mobileQuery=window.matchMedia('(max-width: 760px)')
function updateMobile(event:MediaQueryListEvent){mobile.value=event.matches}
// Fixed editorial entry: update this selection when a new society issue is published.
const currentIssue={id:9,title:'盐与糖',subtitle:'海燕文学社 · 2026 年刊'}
const today=computed(()=>new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',year:'numeric',month:'long',day:'numeric',weekday:'long'}).format(clock.value))
const greeting=computed(()=>{const hour=Number(new Intl.DateTimeFormat('en-US',{timeZone:'Asia/Shanghai',hour:'numeric',hourCycle:'h23'}).format(clock.value));return hour<6?'夜深了':hour<11?'早上好':hour<14?'中午好':hour<18?'下午好':'晚上好'})
const activity=computed(()=>tags.value.filter(t=>t.isActivity&&t.activityActive!==false).sort((a,b)=>b.id-a.id)[0])
const featuredTag=computed(()=>tags.value.find(t=>t.name==='海燕精选'))
let sequence=0,disposed=false,timer:ReturnType<typeof setInterval>|undefined

async function loadOverview(){overviewError.value='';try{
  const [overview,allTags]=await Promise.all([api.get('/public/home'),api.get('/public/tags')])
  if(disposed)return
  total.value=overview.data.workCount;authors.value=overview.data.authorCount;featured.value=overview.data.featured;tags.value=allTags.data
}catch(e){if(!disposed)overviewError.value=message(e)}}
async function loadFeed(){const token=++sequence;loading.value=true;error.value='';works.value=[]
  try{const {data}=await api.get('/public/works',{params:{sort:sort.value,page:1}})
    if(disposed||token!==sequence)return
    works.value=data.items.slice(0,9);feedTotal.value=data.total
  }catch(e){if(!disposed&&token===sequence)error.value=message(e)}finally{if(!disposed&&token===sequence)loading.value=false}}
function changeSort(value:string){sort.value=value;void loadFeed()}
function readStream(){document.getElementById('home-stream')?.scrollIntoView({behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth'})}
async function shuffle(){if(randomBusy.value)return;randomBusy.value=true;randomError.value=''
  try{const {data}=await api.get('/public/works/random',{params:{exclude:random.value.map(w=>w.id).join(',')}});if(!disposed)random.value=data}
  catch(e){if(!disposed)randomError.value=message(e)}finally{if(!disposed)randomBusy.value=false}}
onMounted(()=>{mobileQuery.addEventListener('change',updateMobile);void loadOverview();void loadFeed();void shuffle();timer=setInterval(()=>clock.value=new Date(),30000)})
onBeforeUnmount(()=>{mobileQuery.removeEventListener('change',updateMobile);disposed=true;sequence++;clearInterval(timer)})
</script>

<template>
  <div class="container home-page">
    <div class="home-top">
    <section class="home-welcome" aria-labelledby="home-welcome-title">
      <div class="home-welcome-copy">
        <p class="home-mobile-eyebrow"><span>HAIYAN LETTERS</span><span>NO. 01 / 今日来信</span></p>
        <p class="home-date"><span class="home-live-dot"></span><time :datetime="clock.toISOString()">{{today}}</time><span class="home-date-divider">/</span> 今日书页</p>
        <h1 id="home-welcome-title">{{greeting}}<span v-if="user">，{{user.penName}}</span>。<br class="home-mobile-break"/> 来读一篇好文字。</h1>
        <p class="home-welcome-note">在这里，日常有回声，文字有归处。</p>
        <div class="home-welcome-bottom"><div class="home-stats"><span>文章库已收录 <strong>{{total===null?'—':total.toLocaleString()}}</strong> 篇</span><span><strong>{{authors===null?'—':authors.toLocaleString()}}</strong> 位创作者留下文字</span></div><DraftLink class="text-button">写下此刻<Icon name="ArrowUpRight" :size="15"/></DraftLink></div>
      </div>
      <div class="home-welcome-art" aria-hidden="true"><span class="home-art-orbit"></span><img src="/brand/haiyan-bird.svg" alt=""/><span class="home-art-caption">字有回声<br/><small>LET WORDS TAKE FLIGHT</small></span></div>
    </section>

    <section class="home-mobile-discover" aria-label="发现内容">
      <div class="home-discover-tabs" role="group" aria-label="选择展示内容">
        <button v-for="item in [{id:'featured',label:'精选',icon:'Sparkles'},{id:'activity',label:'活动',icon:'Feather'},{id:'random',label:'随机文章',icon:'Shuffle'},{id:'issue',label:'当期社刊',icon:'BookOpen'}] as const" :key="item.id" type="button" :class="{selected:mobilePanel===item.id}" :aria-pressed="mobilePanel===item.id" @click="mobilePanel=item.id"><span><Icon :name="item.icon" :size="18"/></span><strong>{{item.label}}</strong></button>
      </div>
      <div class="home-discover-panel" aria-live="polite">
        <template v-if="mobilePanel==='featured'">
          <div class="discover-heading"><h2>海燕精选</h2><RouterLink v-if="featuredTag" :to="{path:'/explore',query:{tag:featuredTag.id}}">全部精选 <Icon name="ArrowRight" :size="14"/></RouterLink></div>
          <div v-if="featured.length" class="discover-picks"><article v-for="w in featured" :key="w.id"><RouterLink :to="`/work/${w.id}`">{{w.title}}</RouterLink><p>{{w.excerpt}}</p><small>{{w.displayByline||w.authorName}} · {{w.genre}}</small></article></div>
          <div v-else class="discover-empty"><strong>{{total===null?'正在翻开精选书页…':'好文字，值得被认真看见。'}}</strong><p>{{overviewError?'精选暂未加载，请稍后重试。':'编辑部正在挑选作品，先去读读新来信吧。'}}</p><button @click="readStream">阅读新作 <Icon name="ArrowRight" :size="14"/></button></div>
        </template>
        <template v-else-if="mobilePanel==='activity'">
          <div class="discover-heading"><h2>征稿与活动</h2><RouterLink to="/activities">全部活动 <Icon name="ArrowRight" :size="14"/></RouterLink></div>
          <template v-if="activity"><span class="discover-kicker">提笔赴约 · 主题征稿</span><h3>{{activity.name}}</h3><p>{{activity.description||'循着这个主题，写下属于你的故事。'}}</p><div class="discover-actions"><span>已有 {{activity.workCount}} 篇作品赴约</span><DraftLink :tag="activity.id">参与写作 <Icon name="ArrowUpRight" :size="14"/></DraftLink><RouterLink :to="{path:'/explore',query:{tag:activity.id}}">阅读作品</RouterLink></div></template>
          <div v-else class="discover-empty"><strong>下一场相遇，正在酝酿。</strong><p>新的征稿主题发布后，会在这里与你见面。</p><RouterLink to="/activities">前往活动页 <Icon name="ArrowRight" :size="14"/></RouterLink></div>
        </template>
        <template v-else-if="mobilePanel==='random'">
          <div class="discover-heading"><h2>偶然读到</h2><button :disabled="randomBusy" @click="shuffle"><Icon name="RefreshCw" :size="14"/>{{randomBusy?'收信中':'换一换'}}</button></div>
          <p v-if="randomError" class="error-message" role="alert">{{randomError}}</p><div v-if="random.length" class="discover-random"><article v-for="(w,i) in random" :key="w.id"><span>{{String(i+1).padStart(2,'0')}}</span><RouterLink :to="`/work/${w.id}`">{{w.title}}</RouterLink><small>{{w.displayByline||w.authorName}} · {{w.genre}}</small></article></div><p v-else-if="!randomError">{{randomBusy?'正在寻找一篇好文字…':'文章库正在等待新的来信。'}}</p>
        </template>
        <template v-else><div class="discover-heading"><h2>当期社刊</h2><span>ISSUE 2026</span></div><RouterLink :to="`/collection/${currentIssue.id}`" class="discover-issue"><small>{{currentIssue.subtitle}}</small><strong>{{currentIssue.title}}</strong><span>翻开这一期，读一读属于海燕的文字。 <Icon name="ArrowUpRight" :size="15"/></span></RouterLink></template>
      </div>
    </section>
    <section class="home-featured" aria-labelledby="home-featured-title">
      <div class="home-section-heading"><div class="home-section-title"><Icon name="Sparkles" :size="18"/><h2 id="home-featured-title">海燕精选</h2><span class="home-section-note">编辑部的认真推荐</span></div><RouterLink v-if="featuredTag" :to="{path:'/explore',query:{tag:featuredTag.id}}" class="home-more">全部精选<Icon name="ArrowRight" :size="14"/></RouterLink></div>
      <div v-if="featured.length" class="home-featured-grid"><article v-for="(w,i) in featured" :key="w.id" class="home-pick">
        <div class="home-pick-top"><span class="home-pick-label"><Icon name="Bookmark" :size="12"/> 编辑精选</span><span>{{String(i+1).padStart(2,'0')}}</span></div>
        <div class="home-pick-heading"><RouterLink :to="`/work/${w.id}`" class="home-pick-title" :title="w.title">{{w.title}}</RouterLink><div class="home-pick-meta"><WorkByline :work="w"/><span>{{w.genre}} · {{date(w.publishedAt)}}</span></div></div>
        <p>{{w.excerpt}}</p>
      </article></div>
      <div v-else class="home-featured-empty"><Icon name="Bookmark" :size="22"/><div><strong>{{total===null?'正在翻开精选书页…':'好文字，值得被认真看见。'}}</strong><p>{{overviewError?'精选暂未加载，请稍后重试。':'编辑部正在挑选作品，先去读读新来信吧。'}}</p></div><button @click="readStream">阅读新作 <Icon name="ArrowRight" :size="14"/></button></div>
    </section>
    </div>
    <p v-if="overviewError" class="error-message" role="alert">站点概况暂未加载：{{overviewError}} <button class="text-button" @click="loadOverview">重试</button></p>

    <div class="home-columns">
      <section id="home-stream" class="home-stream" aria-labelledby="home-stream-title">
        <div class="home-section-heading"><div class="home-section-title"><h2 id="home-stream-title">文字广场</h2><span class="home-section-note">每一次落笔，都值得相遇</span></div><RouterLink to="/explore" class="home-more">文库<Icon name="ArrowUpRight" :size="14"/></RouterLink></div>
        <div class="home-feed-toolbar"><div class="home-feed-tabs" aria-label="信息流排序"><button :class="{active:sort==='latest'}" :aria-pressed="sort==='latest'" @click="changeSort('latest')">最新来信</button><button :class="{active:sort==='popular'}" :aria-pressed="sort==='popular'" @click="changeSort('popular')">人气作品</button></div><LayoutSwitch v-if="!mobile" v-model="homeLayout"/></div>
        <div v-if="loading&&!works.length" class="empty-state" role="status">正在收取来信…</div>
        <div v-else-if="!error&&!works.length" class="empty-state"><Icon name="BookOpen" :size="30"/><h3>这一页，等你来写</h3><p>还没有作品，来留下第一篇吧。</p><DraftLink class="button secondary">开始写作</DraftLink></div>
        <WorkFeed v-else-if="works.length" :works="works" :layout="mobile?'cards':homeLayout"/>
        <div v-if="error" class="home-feed-error" role="alert"><p>{{error}}</p><button class="button secondary" @click="loadFeed">重试加载</button></div>
        <RouterLink v-else-if="works.length" to="/explore" class="home-load-more">再读一些<Icon name="ArrowUpRight" :size="15"/><span class="home-library-total">文库 · {{feedTotal}} 篇</span></RouterLink>
      </section>

      <aside class="home-sidebar" aria-label="发现更多">
        <section class="home-activity" aria-labelledby="home-activity-title"><div class="home-section-heading"><span class="home-side-kicker"><span class="home-live-dot"></span> 主打活动</span><RouterLink to="/activities" aria-label="查看全部活动"><Icon name="ArrowUpRight" :size="17"/></RouterLink></div><div class="home-activity-mark" aria-hidden="true"><Icon name="Feather" :size="44"/></div>
          <template v-if="activity"><p class="home-activity-eyebrow">提笔赴约 · 主题征稿</p><h2 id="home-activity-title">{{activity.name}}</h2><p class="home-activity-description">{{activity.description||'循着这个主题，写下属于你的故事。'}}</p><div class="home-activity-count">已有 {{activity.workCount}} 篇作品赴约</div><DraftLink :tag="activity.id" class="button full-width">参与这次写作<Icon name="ArrowUpRight" :size="15"/></DraftLink><RouterLink :to="{path:'/explore',query:{tag:activity.id}}" class="home-activity-read">读读大家的来信<Icon name="ArrowRight" :size="14"/></RouterLink></template>
          <template v-else><h2 id="home-activity-title">下一场相遇，<br/>正在酝酿。</h2><p class="home-activity-description">{{total===null?'正在收取活动消息…':'新的征稿主题发布后，会在这里与你见面。'}}</p><RouterLink to="/activities" class="text-button">前往活动页<Icon name="ArrowRight" :size="14"/></RouterLink></template>
        </section>
        <section class="home-side-panel home-random" aria-labelledby="home-random-title"><div class="home-section-heading"><div class="home-section-title"><Icon name="Shuffle" :size="16"/><h2 id="home-random-title">偶然读到</h2></div><button class="home-more" :disabled="randomBusy" @click="shuffle"><Icon name="RefreshCw" :size="13"/>{{randomBusy?'收信中':'换一换'}}</button></div><p class="home-side-description">不妨把下一篇，交给一点运气。</p>
          <p v-if="randomError" class="home-side-message" role="alert">{{randomError}}</p><div v-if="random.length" class="home-random-list"><article v-for="(w,i) in random" :key="w.id"><span class="home-random-index">{{String(i+1).padStart(2,'0')}}</span><div><RouterLink :to="`/work/${w.id}`" class="home-random-title">{{w.title}}</RouterLink><p>{{w.displayByline||w.authorName}}<span>{{w.genre}}</span></p></div></article></div><p v-else-if="!randomError" class="home-side-message">{{randomBusy?'正在寻找一篇好文字…':'文章库正在等待新的来信。'}}</p>
        </section>
        <section class="home-side-panel home-current-issue" aria-labelledby="home-issue-title"><div class="home-section-heading"><div class="home-section-title"><Icon name="BookOpen" :size="17"/><h2 id="home-issue-title">当期社刊</h2></div><span class="home-issue-label">ISSUE 2026</span></div><RouterLink :to="`/collection/${currentIssue.id}`" class="home-issue-entry"><span class="home-issue-subtitle">{{currentIssue.subtitle}}</span><strong>{{currentIssue.title}}</strong><span class="home-issue-description">翻开这一期，读一读属于海燕的文字。</span><span class="home-issue-read">阅读社刊合集<Icon name="ArrowUpRight" :size="15"/></span></RouterLink></section>
      </aside>
    </div>
  </div>
</template>
