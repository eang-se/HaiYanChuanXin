import {test,expect,type Page} from '@playwright/test'
import {mkdir} from 'node:fs/promises'

const currentName='海燕传信 - 海燕文学社综合活动平台'
async function fixture(page:Page,role='SUPER_ADMIN'){
 const state={siteName:currentName,profile:{penName:'海燕编辑部',bio:'编辑部简介'},avatarUrl:'/brand/editorial-account.jpg',avatarUploads:0,profileSaves:0,rosterUploads:0,rosterBody:'',groups:[] as {groupId:string;count:number}[]}
 await page.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname,method=route.request().method(),json=(value:unknown,status=200)=>route.fulfill({status,json:value})
  if(path==='/api/auth/me')return json({id:1,username:'admin',penName:'管理员',accountType:'PERSONAL',role,actorRole:role,bio:'签名\n简介',profileCompleted:true,profilePromptSeen:true,permissions:{}})
  if(path==='/api/auth/csrf')return json({token:'fixture'})
  if(path==='/api/public/site')return json({name:state.siteName,description:'文学社'})
  if(path==='/api/public/traffic')return json({online:1,visitors:1})
  if(path==='/api/admin/settings'&&method==='GET')return json({siteName:state.siteName,siteDescription:'文学社',registrationEnabled:true,registrationMode:'live',permissionCatalog:{WRITE:'投稿'}})
  if(path==='/api/admin/settings'&&method==='PUT'){state.siteName=route.request().postDataJSON().siteName;return route.fulfill({status:200,body:''})}
  if(path==='/api/admin/accounts')return json([{id:1,username:'admin',penName:'管理员',accountType:'PERSONAL',role}])
  if(path==='/api/admin/public-accounts')return json([{id:7,username:'haiyan_editor',...state.profile,avatarUrl:state.avatarUrl,memberIds:[]}])
  if(path==='/api/admin/public-accounts/7/avatar'){state.avatarUploads++;state.avatarUrl='/api/public/account-avatars/12345678-1234-4123-8123-123456789abc';return json({avatarUrl:state.avatarUrl})}
  if(path==='/api/admin/public-accounts/7/profile'){state.profileSaves++;state.profile=route.request().postDataJSON();return route.fulfill({status:200,body:''})}
  if(path==='/api/admin/registration/qq-groups')return json(state.groups)
  if(path==='/api/admin/registration/qq-allowlist'&&method==='GET')return json(state.groups.length?[{groupId:'测试群',qqNumber:'234567',name:'测试成员',active:true,bound:false}]:[])
  if(path==='/api/admin/registration/qq-roster-file'){state.rosterUploads++;state.rosterBody=route.request().postDataBuffer()?.toString('utf8')||'';state.groups=[{groupId:'测试群',count:1}];return json({groupId:'测试群',count:1})}
  if(path==='/api/member/pen-names')return json([])
  if(path==='/api/member/profile'&&method==='PUT'){state.profile=route.request().postDataJSON();return route.fulfill({status:200,body:''})}
  return json([],404)
 })
 return state
}
async function noOverflow(page:Page){expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)}
test.beforeAll(async()=>{await mkdir('../output/site-profile',{recursive:true})})

test('about uses the supplied history without external links or a timeline on desktop and mobile',async({page})=>{
 await fixture(page);await page.setViewportSize({width:1440,height:1000});await page.goto('/#/about')
 await expect(page.getByRole('heading',{name:'海燕文学社',exact:true})).toBeVisible()
 await expect(page.locator('.about-intro')).toContainText('1955');await expect(page.locator('.about-intro')).toContainText('1992')
 await expect(page.locator('.about-editorial')).toContainText('斧山刑土钻油气，铁骨金心铸精魂')
 await expect(page.locator('.about-timeline')).toHaveCount(0);await expect(page.locator('.about-editorial a[href^="http"]')).toHaveCount(0)
 await expect(page).toHaveTitle(currentName);await noOverflow(page)
 await page.screenshot({path:'../output/site-profile/about-desktop.png',fullPage:true})
 await page.setViewportSize({width:390,height:844});await noOverflow(page);await page.screenshot({path:'../output/site-profile/about-mobile.png',fullPage:true})
 await page.setViewportSize({width:320,height:800});await noOverflow(page)
})

test('administrators edit public profile and upload its avatar independently',async({page})=>{
 const state=await fixture(page,'ADMIN');await page.goto('/#/admin/accounts?view=public')
 await page.getByRole('button',{name:'编辑资料'}).click();await expect(page.getByRole('dialog')).toBeVisible()
 await page.getByLabel('选择公用账号头像').setInputFiles({name:'editor.png',mimeType:'image/png',buffer:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=','base64')})
 await page.getByRole('button',{name:'上传头像'}).click();await expect.poll(()=>state.avatarUploads).toBe(1);expect(state.profileSaves).toBe(0)
 await page.getByLabel('账号昵称',{exact:true}).fill('编辑部新昵称');await page.getByLabel('账号简介',{exact:true}).fill('一段合并后的简介')
 await page.getByRole('button',{name:'保存资料'}).click();await expect.poll(()=>state.profileSaves).toBe(1)
 expect(state.profile).toEqual({penName:'编辑部新昵称',bio:'一段合并后的简介'})
})

test('super administrator uploads a group roster and site settings update the page title while the brand stays fixed',async({page})=>{
 const state=await fixture(page);await page.goto('/#/admin/settings?tab=qq')
 await page.getByLabel('选择名单文件').setInputFiles({name:'测试群.md',mimeType:'text/markdown',buffer:Buffer.from('234567 测试成员\n')})
 await page.getByRole('button',{name:'上传名单'}).click();await expect.poll(()=>state.rosterUploads).toBe(1)
 expect(state.rosterBody).toContain('234567');await expect(page.getByLabel('选择 QQ 群')).toHaveValue('测试群')
 await page.getByRole('button',{name:'站点资料'}).click();await page.getByLabel('站点名称').fill('海燕传信 - 新的平台名称 - 特别版')
 await page.getByRole('button',{name:'保存站点资料'}).click();await expect(page).toHaveTitle('海燕传信 - 新的平台名称 - 特别版')
 await expect(page.locator('.brand')).toHaveAttribute('aria-label','海燕传信')
 await expect(page.locator('.brand small')).toHaveText('HAIYAN · LITERARY SOCIETY')
 await expect(page.locator('.footer-title span')).toHaveText('海燕传信')
})

test('regular administrators cannot see the roster upload control; personal profile has one introduction field',async({page})=>{
 await fixture(page,'ADMIN');await page.goto('/#/admin/settings?tab=qq');await expect(page.getByLabel('选择名单文件')).toHaveCount(0)
 await page.goto('/#/profile');await expect(page.getByLabel('个人简介')).toHaveValue('签名\n简介');await expect(page.getByLabel('签名',{exact:true})).toHaveCount(0)
})
