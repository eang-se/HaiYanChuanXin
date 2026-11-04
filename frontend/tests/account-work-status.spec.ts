import {test,expect} from '@playwright/test'

test('desk distinguishes hidden and administratively withdrawn work and allows deleting own withdrawn work',async({page})=>{
 let publishedStatus='PUBLISHED',deleted=false
 await page.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname,method=route.request().method()
  const json=(data:unknown)=>route.fulfill({json:data})
  if(path==='/api/auth/me')return json({id:1,username:'member',penName:'作者',role:'MEMBER',accountType:'PERSONAL',profileCompleted:true,profilePromptSeen:true,permissions:{WRITE:true}})
  if(path==='/api/public/site')return json({name:'海燕传信'})
  if(path==='/api/public/traffic')return json({online:0,visitors:0})
  if(path==='/api/member/bookmarks')return json([])
  if(path==='/api/member/works'&&method==='GET')return json([
   {id:11,title:'自己的文章',status:publishedStatus,genre:'散文',authorId:1,authorName:'作者',displayByline:'作者',tags:[],wordCount:10,updatedAt:'2026-11-01'},
   ...(!deleted?[{id:12,title:'被下架的文章',status:'WITHDRAWN',genre:'散文',authorId:1,authorName:'作者',displayByline:'作者',tags:[],wordCount:10,updatedAt:'2026-11-01'}]:[])
  ])
  if(path==='/api/member/works/11/withdraw'&&method==='POST'){publishedStatus='UNPUBLISHED';return json({})}
  if(path==='/api/member/works/12'&&method==='DELETE'){deleted=true;return route.fulfill({status:204})}
  return json([])
 })
 page.on('dialog',dialog=>dialog.accept())
 await page.goto('/#/account')
 const own=page.locator('.desk-work-item').filter({hasText:'自己的文章'}),withdrawn=page.locator('.desk-work-item').filter({hasText:'被下架的文章'})
 await expect(withdrawn.locator('.status-badge')).toHaveText('下架')
 await own.getByRole('button',{name:'设为未公开'}).click()
 await expect(own.locator('.status-badge')).toHaveText('未公开')
 await withdrawn.getByRole('button',{name:'删除稿件：被下架的文章'}).click()
 await expect(withdrawn).toHaveCount(0)
 expect(deleted).toBe(true)
})
