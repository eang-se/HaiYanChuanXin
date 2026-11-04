import {test,expect,type Page} from '@playwright/test'

async function setup(page:Page,role:'guest'|'member'|'admin'='member'){
 let collections=[{id:4,title:'林间手记',kind:'选集',published:true,authorId:29,authorName:'林间',workCount:1,description:'一些文字'}]
 let deleted=0
 await page.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname,json=(data:unknown,status=200)=>route.fulfill({json:data,status})
  if(path==='/api/auth/me')return json(role==='guest'?null:{id:29,username:'writer',penName:'林间',role:role==='admin'?'ADMIN':'MEMBER',profileCompleted:true,profilePromptSeen:true})
  if(path==='/api/auth/csrf')return json({token:'fixture'})
  if(path==='/api/public/site')return json({name:'海燕传信'})
  if(path==='/api/public/traffic')return json({online:1,visitors:2})
  if(path==='/api/member/collections/4'&&route.request().method()==='DELETE'){deleted++;collections=[];return route.fulfill({status:200,body:''})}
  if(path==='/api/member/collections')return json(collections)
  if(path==='/api/public/collections')return json({items:collections,total:collections.length})
  if(path==='/api/public/collections/4')return json({...collections[0],works:[{id:8,title:'来信',genre:'散文',excerpt:'文字',authorId:29,authorName:'林间',displayByline:'林间',wordCount:10,likes:0,tags:[],collection:null,status:'PUBLISHED'}]})
  if(path==='/api/member/works')return json([{id:8,title:'来信',status:'PUBLISHED',collectionId:4}])
  return json([])
 })
 return {deleted:()=>deleted}
}

test('collection lists and contents use rows and deletion keeps a confirmation gate',async({page})=>{
 const state=await setup(page);await page.setViewportSize({width:390,height:844});await page.goto('/#/collections')
 await expect(page.locator('.collection-row')).toHaveCount(1);await expect(page.locator('.view-switch')).toHaveCount(0)
 await page.getByRole('button',{name:'创建与管理合集'}).click();const dialog=page.getByRole('dialog',{name:'创建与管理合集'});await dialog.getByRole('button',{name:/林间手记/}).click();await expect(dialog.getByRole('button',{name:'删除合集'})).toBeVisible()
 page.once('dialog',d=>d.dismiss());await dialog.getByRole('button',{name:'删除合集'}).click();expect(state.deleted()).toBe(0)
 page.once('dialog',d=>d.accept());await dialog.getByRole('button',{name:'删除合集'}).click();await expect.poll(state.deleted).toBe(1);await expect(dialog.getByText('暂无合集，可以新建。')).toBeVisible()
})

test('collection detail stays in rows despite stored cards preference',async({page})=>{
 await setup(page);await page.addInitScript(()=>localStorage.setItem('haiyan.feedLayout','cards'));await page.setViewportSize({width:320,height:844});await page.goto('/#/collection/4')
 await expect(page.locator('.collection-detail .work-row')).toHaveCount(1);await expect(page.locator('.collection-detail .view-switch')).toHaveCount(0)
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})

for(const role of ['guest','member','admin'] as const){
 test(`mobile header and footer follow ${role} access`,async({page})=>{
  await setup(page,role);await page.setViewportSize({width:390,height:844});await page.goto('/#/collections')
  await expect(page.locator('.site-header .mobile-menu')).toBeHidden()
  if(role==='guest')await expect(page.getByRole('link',{name:'登录或注册'})).toBeVisible();else await expect(page.getByRole('button',{name:'账号与身份'})).toBeVisible()
  const footer=page.getByRole('navigation',{name:'页脚导航'});await expect(footer.getByRole('link',{name:'征稿活动'})).toHaveCount(0);await expect(footer.getByRole('link',{name:'社务台'})).toHaveCount(role==='admin'?1:0)
 })
}


test('mobile pages do not add dock padding before the footer',async({page})=>{
 await setup(page,'admin');await page.route('**/api/public/home',route=>route.fulfill({json:{workCount:0,authorCount:0,featured:[]}}));await page.route('**/api/public/works/random',route=>route.fulfill({json:[]}));await page.route('**/api/public/works**',route=>route.fulfill({json:{items:[],total:0}}));await page.route('**/api/admin/overview',route=>route.fulfill({json:{members:[],works:[],invitations:[]}}))
 await page.setViewportSize({width:390,height:844});for(const path of ['/#/','/#/activities','/#/admin']){await page.goto(path,{waitUntil:'networkidle'});await expect(page.locator('.site-footer')).toBeVisible();await expect.poll(()=>page.locator('.main-content').evaluate(el=>getComputedStyle(el).paddingBottom)).toBe('0px');const gap=await page.evaluate(()=>document.querySelector('.site-footer')!.getBoundingClientRect().top-document.querySelector('.main-content')!.getBoundingClientRect().bottom);expect(gap).toBeLessThan(2)}
})
