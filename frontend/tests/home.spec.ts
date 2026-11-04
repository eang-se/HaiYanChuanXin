import {test,expect,type Page} from '@playwright/test'
import {mkdir} from 'node:fs/promises'

const featuredTag={id:7,name:'海燕精选',kind:'TOPIC',isActivity:false,description:'编辑部推荐',workCount:3}
const topics=[featuredTag,{id:1,name:'校园日常',kind:'TOPIC',isActivity:false,description:'',workCount:10},{id:9,name:'秋日来信',kind:'TOPIC',isActivity:true,description:'把秋天的一个瞬间，写成一封寄给远方的信。不限体裁，期待你的落笔。',workCount:6}]
const titles=['风经过有你的走廊','写在秋天的最后一页','我们与故乡之间的距离','图书馆靠窗的座位','一封没有地址的信','天台上的晚风','等待一棵树开花','雨后，校园慢慢醒来','在旧书里遇见你','山海之间','留给明天的诗','小城的第一个冬天','白昼与星光','落日很慢','那些没有说完的话','经过人间','一盏灯的远方','慢慢读懂生活']
const works=titles.map((title,i)=>({id:i+1,title,genre:i%2?'散文':'诗歌',excerpt:i%3===0?'风经过校园，留下一封短短的来信。':i%3===1?'晚自习的铃声停了，校园里只留下轻轻的脚步声。我们把尚未写完的句子收进书页，等明天的光再照亮它。'.repeat(2):'那些没有说完的话，都留给明天。',tags:i<3?[featuredTag]:[topics[1]],authorId:i+20,authorName:['林间','知秋','远山'][i%3],displayByline:['林间','知秋','远山'][i%3],wordCount:520+i*31,likes:8+i,commentCount:0,publishedAt:'2026-11-01T01:00:00Z',status:'PUBLISHED',cover:'',collection:null}))
async function fixture(page:Page,mode='normal') {
  const state={randomCalls:0,feedCalls:[] as URL[],failFeed:false,featured:true,trafficCalls:0,editor:false,featureChanges:[] as boolean[]}
  await page.route('**/api/**',async route=>{
    const url=new URL(route.request().url()),path=url.pathname
    const json=(value:unknown,status=200)=>route.fulfill({json:value,status})
    if(path==='/api/auth/me')return json(state.editor?{id:10,username:'haiyan_editor',accountType:'PUBLIC',penName:'海燕编辑部',role:'MEMBER',actorRole:'MEMBER',profileCompleted:true,profilePromptSeen:true}:null)
    if(path==='/api/auth/csrf')return json({token:'fixture'})
    if(path==='/api/public/site')return json({name:'海燕传信'})
    if(path==='/api/public/traffic'){state.trafficCalls++;return mode==='offline'?json({message:'暂不可用'},503):json({online:8,visitors:1024})}
    if(path==='/api/public/tags')return json(mode==='empty'?[]:topics)
    if(path==='/api/public/home')return json({workCount:mode==='empty'?0:18,authorCount:mode==='empty'?0:3,featured:mode==='empty'?[]:mode==='single'?works.slice(1,2):works.slice(0,3)})
    if(path==='/api/public/works/random'){state.randomCalls++;return json(mode==='empty'?[]:works.slice(state.randomCalls===1?3:6,state.randomCalls===1?6:9))}
    if(path==='/api/public/works'){
      state.feedCalls.push(url)
      if(state.failFeed)return json({message:'暂时无法收取来信'},503)
      const selected=mode==='empty'?[]:works.filter(w=>!url.searchParams.get('genre')||w.genre===url.searchParams.get('genre'))
      const p=Number(url.searchParams.get('page')||1)
      return json({items:selected.slice((p-1)*12,p*12),total:selected.length})
    }
    if(path==='/api/admin/overview')return json({members:[],works:[{...works[0],tags:state.featured?[featuredTag]:[]}],invitations:[]})
    if(path==='/api/admin/works')return json({items:[{...works[0],tags:state.featured?[featuredTag]:[]}],total:1,page:1,pageSize:12,summary:{total:1,published:1,draft:0,withdrawn:0,featured:state.featured?1:0}})
    if(path==='/api/admin/works/1/featured'){state.featured=route.request().postDataJSON().active;state.featureChanges.push(state.featured);return route.fulfill({status:200,body:''})}
    return json([],404)
  })
  return state
}
async function noOverflow(page:Page){const overflow=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth,nodes:[...document.querySelectorAll('body *')].filter(n=>{const r=n.getBoundingClientRect();return r.right>innerWidth+1&&r.width>0}).slice(0,12).map(n=>`${n.tagName}.${n.className}`)}));expect(overflow.scroll,JSON.stringify(overflow)).toBeLessThanOrEqual(overflow.width)}
test.beforeAll(async()=>{await mkdir('../output/home-redesign',{recursive:true})})

test('home has real summary, curated picks, activity, random refresh and persistent feed controls',async({page})=>{
  const state=await fixture(page);await page.setViewportSize({width:1440,height:1000});await page.goto('/#/')
  await expect(page.locator('.home-stats')).toContainText('18');await expect(page.locator('.home-stats')).toContainText('3')
  await expect(page.locator('.home-date time')).toContainText('2026年')
  await expect(page.locator('.home-pick')).toHaveCount(3);await expect(page.locator('.home-stream .work-card')).toHaveCount(9)
  await expect(page.locator('.home-activity h2')).toHaveText('秋日来信')
  await expect(page.locator('.home-activity .button')).toHaveAttribute('href','#/write?tag=9')
  await expect(page.locator('.footer-traffic')).toContainText('1,024')
  await noOverflow(page);await page.screenshot({path:'../output/home-redesign/desktop.png',fullPage:true})
  const oldRandom=await page.locator('.home-random-title').allTextContents()
  await page.getByRole('button',{name:'换一换'}).click();await expect(page.locator('.home-random-title').first()).toHaveText('等待一棵树开花')
  expect(await page.locator('.home-random-title').allTextContents()).not.toEqual(oldRandom)
  await page.getByRole('button',{name:'人气作品',exact:true}).click();await expect(page.locator('.home-stream .work-card')).toHaveCount(9)
  expect(state.feedCalls.at(-1)?.searchParams.get('sort')).toBe('popular')
  await expect(page.locator('.home-current-issue')).toContainText('盐与糖')
  await expect(page.locator('.home-issue-entry')).toHaveAttribute('href','#/collection/9')
  await expect(page.locator('.home-genres')).toHaveCount(0)
  await expect(page.getByRole('heading',{name:'循着话题读'})).toHaveCount(0)
  await expect(page.locator('.home-load-more')).toHaveAttribute('href','#/explore')
  const feedCalls=state.feedCalls.length
  await page.locator('.home-load-more').click();await expect(page).toHaveURL(/#\/explore$/)
  expect(state.feedCalls.slice(feedCalls).every(url=>url.searchParams.get('page')!=='2')).toBe(true)
  await page.goto('/#/');await expect(page.locator('.home-stream .work-card')).toHaveCount(9)
  await page.locator('.home-more').filter({hasText:'全部精选'}).click();await expect(page).toHaveURL(/explore\?tag=7/)
})

test('desktop cards align at top and bottom in three columns, and rows keep the shelf preference',async({page})=>{
  await fixture(page);await page.addInitScript(()=>localStorage.setItem('haiyan.feedLayout','rows'))
  await page.setViewportSize({width:1440,height:1000});await page.goto('/#/')
  await expect(page.locator('.home-stream .work-card')).toHaveCount(9)
  const welcome=await page.locator('.home-welcome').boundingBox(),featured=await page.locator('.home-featured').boundingBox()
  expect(welcome!.height).toBeGreaterThanOrEqual(240);expect(welcome!.height).toBeLessThan(340)
  expect(Math.abs(welcome!.y-featured!.y)).toBeLessThan(2)
  expect(Math.abs(welcome!.height-featured!.height)).toBeLessThan(2)
  expect(Math.abs(welcome!.width-featured!.width)).toBeLessThan(2)
  expect(welcome!.x+welcome!.width).toBeLessThan(featured!.x)
  for(const width of [1280,1440]){
    await page.setViewportSize({width,height:1000});await noOverflow(page)
    const boxes=await page.locator('.home-stream .work-card').evaluateAll(nodes=>nodes.slice(0,3).map(n=>{const r=n.getBoundingClientRect();return {left:r.left,top:r.top,bottom:r.bottom}}))
    expect(boxes[0].left).toBeLessThan(boxes[1].left);expect(boxes[1].left).toBeLessThan(boxes[2].left)
    expect(Math.max(...boxes.map(b=>b.bottom))-Math.min(...boxes.map(b=>b.bottom))).toBeLessThan(2)
    expect(Math.max(...boxes.map(b=>b.top))-Math.min(...boxes.map(b=>b.top))).toBeLessThan(2)
  }
  await page.getByRole('button',{name:'横条',exact:true}).click();await expect(page.locator('.home-stream .work-row')).toHaveCount(9)
  await expect(page.locator('.home-stream .row-genre').first()).toBeHidden()
  const row=await page.locator('.home-stream .work-row').first().boundingBox();expect(row!.height).toBeLessThan(180)
  await page.screenshot({path:'../output/home-redesign/desktop-compact-rows.png',fullPage:true})
  await page.getByRole('button',{name:'卡片',exact:true}).click()
  expect(await page.evaluate(()=>localStorage.getItem('haiyan.feedLayout'))).toBe('rows')
  await page.locator('.home-load-more').click();await expect(page.locator('.work-row')).toHaveCount(12)
})

test('a single editorial pick keeps its metadata on the title line above the excerpt',async({page})=>{
  await fixture(page,'single');await page.setViewportSize({width:1440,height:1000});await page.goto('/#/')
  await expect(page.locator('.home-pick')).toHaveCount(1)
  const title=await page.locator('.home-pick-title').boundingBox(),excerpt=await page.locator('.home-pick>p').boundingBox(),meta=await page.locator('.home-pick-meta').boundingBox()
  expect(meta!.x).toBeGreaterThan(title!.x+title!.width)
  expect(meta!.y).toBeGreaterThanOrEqual(title!.y)
  expect(meta!.y+meta!.height).toBeLessThanOrEqual(title!.y+title!.height+1)
  expect(excerpt!.y).toBeGreaterThanOrEqual(title!.y+title!.height)
  const welcome=await page.locator('.home-welcome').boundingBox(),featured=await page.locator('.home-featured').boundingBox()
  expect(welcome!.height).toBeLessThan(300);expect(Math.abs(welcome!.height-featured!.height)).toBeLessThan(2)
  await page.screenshot({path:'../output/home-redesign/desktop-single-featured.png',fullPage:true})
})

test('empty library stays honest and failed feed can be retried',async({page})=>{
  await fixture(page,'empty');await page.goto('/#/');await expect(page.locator('.home-stats')).toContainText('0')
  await expect(page.locator('.home-featured-empty')).toBeVisible();await expect(page.getByRole('heading',{name:'这一页，等你来写'})).toBeVisible()
  await page.getByRole('button',{name:'阅读新作'}).click();await expect(page).toHaveURL(/#\/$/)
  await expect(page.locator('.home-activity')).toContainText('正在酝酿');await noOverflow(page)
  await page.screenshot({path:'../output/home-redesign/empty.png',fullPage:true})
  await page.unroute('**/api/**');const state=await fixture(page);await page.reload();await expect(page.locator('.home-stream .work-card')).toHaveCount(9)
  state.failFeed=true;await page.getByRole('button',{name:'人气作品',exact:true}).click();await expect(page.locator('.home-feed-error')).toContainText('暂时无法收取来信')
  state.failFeed=false;await page.getByRole('button',{name:'重试加载'}).click();await expect(page.locator('.home-stream .work-card')).toHaveCount(9)
})

test('editorial review adds and removes featured tag with visible feedback',async({page})=>{
  const state=await fixture(page);state.editor=true;await page.goto('/#/admin/works')
  await expect(page.getByRole('button',{name:'取消精选',exact:true})).toBeVisible()
  await page.getByRole('button',{name:'取消精选',exact:true}).click();await expect(page.getByRole('button',{name:'加入精选',exact:true})).toBeVisible()
  await page.getByRole('button',{name:'加入精选',exact:true}).click();await expect(page.getByRole('button',{name:'取消精选',exact:true})).toBeVisible()
  expect(state.featureChanges).toEqual([false,true]);await expect(page.locator('.toast')).toContainText('已加入海燕精选')
})

test('unavailable traffic uses a placeholder and never fabricates a visitor count',async({page})=>{
  await fixture(page,'offline');await page.goto('/#/explore');await expect(page.locator('.footer-traffic')).toContainText('统计暂不可用')
  await expect(page.locator('.footer-traffic strong').first()).toHaveText('—');await expect(page.locator('.footer-traffic strong').last()).toHaveText('—')
})

test('mobile discovery switches content and the feed stays in cards',async({page})=>{
  await fixture(page);await page.setViewportSize({width:320,height:844});await page.goto('/#/')
  const panel=page.locator('.home-discover-panel'),tabs=page.locator('.home-discover-tabs')
  await expect(panel.getByRole('heading',{name:'海燕精选'})).toBeVisible()
  await tabs.getByRole('button',{name:'活动',exact:true}).click();await expect(panel.getByRole('heading',{name:'秋日来信'})).toBeVisible()
  await tabs.getByRole('button',{name:'随机文章',exact:true}).click();await expect(panel.getByRole('heading',{name:'偶然读到'})).toBeVisible();await expect(panel.locator('.discover-random article')).toHaveCount(3)
  await tabs.getByRole('button',{name:'当期社刊',exact:true}).click();await expect(panel).toContainText('盐与糖')
  await expect(page.locator('.home-feed-toolbar .view-switch')).toHaveCount(0);await expect(page.locator('.home-stream .work-card')).toHaveCount(9)
  expect(await page.locator('.site-header').evaluate(el=>el.getBoundingClientRect().height)).toBe(60);await noOverflow(page)
  await page.setViewportSize({width:1000,height:844});await expect(page.locator('.home-feed-toolbar .view-switch')).toBeVisible()
})

test('mobile library keeps dense rows and balanced independent cards',async({page})=>{
  await fixture(page);await page.setViewportSize({width:320,height:844});await page.goto('/#/explore')
  await expect(page.locator('.explore-library .work-row')).toHaveCount(12)
  await expect(page.locator('.genre-tabs').getByRole('button',{name:'公告',exact:true})).toHaveCount(0)
  await page.getByRole('button',{name:'卡片',exact:true}).click()
  await expect(page.locator('.library-masonry .work-card')).toHaveCount(12)
  const bounds=await page.locator('.library-masonry-column').evaluateAll(columns=>columns.map(column=>{const cards=[...column.children];return {top:cards[0].getBoundingClientRect().top,bottom:cards[cards.length-1].getBoundingClientRect().bottom,heights:cards.map(card=>card.getBoundingClientRect().height)}}))
  expect(Math.abs(bounds[0].top-bounds[1].top)).toBeLessThan(1);expect(Math.abs(bounds[0].bottom-bounds[1].bottom)).toBeLessThan(1)
  expect(new Set(bounds.flatMap(column=>column.heights)).size).toBeGreaterThan(1);await noOverflow(page)
  await page.getByRole('button',{name:'诗歌',exact:true}).click();await expect(page).toHaveURL(/genre=/);await expect(page.locator('.library-masonry .work-card')).toHaveCount(9)
  await page.getByRole('button',{name:'横条',exact:true}).click();await expect(page.locator('.explore-library .work-row')).toHaveCount(9)
})

test('mobile home writing links share the new draft dialog',async({page})=>{
  await fixture(page);await page.setViewportSize({width:390,height:844});await page.goto('/#/')
  await page.getByRole('link',{name:'写下此刻'}).click();const dialog=page.getByRole('dialog',{name:'开始一篇新作品'});await expect(dialog).toBeVisible();await dialog.getByRole('button',{name:'关闭'}).click()
  await page.locator('.home-discover-tabs').getByRole('button',{name:'活动',exact:true}).click();await page.locator('.home-discover-panel').getByRole('link',{name:'参与写作'}).click();await expect(dialog).toBeVisible();await expect(page).toHaveURL(/#\/$/)
})
