import {test,expect,type Page} from '@playwright/test'
import {mkdir} from 'node:fs/promises'

const featuredTag={id:7,name:'海燕精选',kind:'TOPIC',isActivity:false,description:'编辑部推荐',workCount:3}
const actor={id:1,username:'admin',penName:'知秋',role:'SUPER_ADMIN',accountType:'PERSONAL',permissions:{WRITE:true,COMMENT:true,UPLOAD:true,COLLECTION:true,REACT:true},profileCompleted:true,profilePromptSeen:true}
async function fixture(page:Page,identity='admin'){
  const state={
    identity,actorRoleOverride:undefined as string|undefined,publicStoredRole:'MEMBER',failDeleteWork:false,failDeleteAccount:false,failApprove:false,failWorks:false,failWithdraw:false,failPermissions:false,requests:[] as URL[],changes:[] as {path:string;body:any}[],
    settings:{siteName:'海燕传信',siteDescription:'以文字相遇，以热爱同行。',registrationEnabled:true,registrationMode:'live',permissionCatalog:{WRITE:'投稿与创作',COMMENT:'发表评论',UPLOAD:'上传文件',COLLECTION:'管理合集',REACT:'点赞与收藏'}},
    accounts:Array.from({length:16},(_,i)=>({...actor,id:i+1,username:i===0?'admin':`member${i}`,penName:i===0?'知秋':`社员${i}`,role:i===0?'SUPER_ADMIN':i===2?'RESTRICTED':'MEMBER',qqNumber:String(12345000+i),createdAt:'2026-10-20T08:00:00Z'})),
    publicAccounts:[{id:101,username:'haiyan_editor',penName:'海燕编辑部',accountType:'PUBLIC',memberIds:[1,2]},{id:102,username:'haiyan_activity',penName:'海燕活动部',accountType:'PUBLIC',memberIds:[1]}],
    qqs:[{groupId:'123456',qqNumber:'12345000',name:'知秋',active:true,bound:true},{groupId:'123456',qqNumber:'12345001',name:'林间',active:true,bound:false},{groupId:'123456',qqNumber:'12345002',name:'远山',active:false,bound:false}],
    tags:[featuredTag,{id:8,name:'秋日来信',description:'把秋天写成一封信。',kind:'TOPIC',isActivity:true,workCount:9}],
    works:Array.from({length:137},(_,i)=>({id:i+1,title:i===0?'风经过有你的走廊':`第${i+1}封校园来信`,genre:i%2?'散文':'诗歌',customGenre:'',displayByline:i%2?'林间':'远山',authorName:'海燕编辑部',authorId:101,wordCount:520+i,updatedAt:'2026-11-01T08:00:00Z',publishedAt:'2026-11-01T08:00:00Z',status:i<120?'PUBLISHED':i<132?'DRAFT':'WITHDRAWN',tags:i<3?[featuredTag]:[],body:'<h2>校园来信</h2><p>晚风穿过走廊，带来一封久候的来信。</p>',cover:'',sourceNote:'海燕文学社',notes:'',collection:null,likes:0,commentCount:0})),
  }
  await page.route('**/api/**',async route=>{
    const request=route.request(),url=new URL(request.url()),path=url.pathname,method=request.method()
    const json=(value:unknown,status=200)=>route.fulfill({json:value,status})
    if(path==='/api/auth/me'){
      const role=state.actorRoleOverride||(state.identity==='admin'?'SUPER_ADMIN':state.identity==='administrator'?'ADMIN':state.identity==='restricted'?'RESTRICTED':'MEMBER')
      return json(['admin','administrator','member'].includes(state.identity)?{...actor,role,actorRole:role,actorId:1}:{...actor,id:101,role:state.publicStoredRole,actorRole:role,actorId:1,actorPenName:'知秋',username:state.identity==='restricted'?'haiyan_editor':`haiyan_${state.identity}`,penName:'海燕公用身份',accountType:'PUBLIC'})
    }
    if(path==='/api/auth/csrf')return json({token:'fixture'})
    if(path==='/api/public/site')return json({name:state.settings.siteName})
    if(path==='/api/public/traffic')return json({online:8,visitors:1024})
    if(path==='/api/public/tags')return json(state.tags)
    if(path==='/api/member/works'&&method==='GET')return json([])
    if(path==='/api/member/bookmarks'||path==='/api/member/collections'||path==='/api/member/pen-names')return json([])
    if(path==='/api/public/activities')return json(state.tags.filter(t=>t.isActivity))
    if(method!=='GET')state.changes.push({path,body:request.headers()['content-type']?.includes('application/json')?request.postDataJSON():null})
    if(path==='/api/admin/settings'){if(method==='PUT'){Object.assign(state.settings,request.postDataJSON());return json({})}return json(state.settings)}
    if(path==='/api/admin/accounts')return json(state.accounts)
    if(/^\/api\/admin\/accounts\/\d+$/.test(path)&&method==='DELETE'){if(state.failDeleteAccount)return json({message:'删除用户失败，请重试'},503);state.accounts=state.accounts.filter(a=>a.id!==Number(path.split('/').at(-1)));return json({})}
    if(path==='/api/admin/public-accounts')return json(state.publicAccounts)
    const permissionId=path.match(/^\/api\/admin\/accounts\/(\d+)\/permissions$/)?.[1]
    if(permissionId){const account=state.accounts.find(a=>a.id===Number(permissionId))!;if(method==='PUT'){if(state.failPermissions)return json({message:'必须至少保留一位超级管理员'},400);Object.assign(account,request.postDataJSON());return json({})}return json({id:account.id,username:account.username,role:account.role,permissions:{...account.permissions}})}
    const publicId=path.match(/^\/api\/admin\/public-accounts\/(\d+)\/members$/)?.[1]
    if(publicId){state.publicAccounts.find(a=>a.id===Number(publicId))!.memberIds=request.postDataJSON().memberIds;return json({})}
    if(path==='/api/admin/works'){
      state.requests.push(url);if(state.failWorks)return json({message:'暂时无法加载文章'},503)
      const q=(url.searchParams.get('q')||'').toLowerCase()
      const selected=state.works.filter(w=>w.status!=='DRAFT'&&(!url.searchParams.get('status')||w.status===url.searchParams.get('status'))&&(!url.searchParams.get('genre')||w.genre===url.searchParams.get('genre'))&&(url.searchParams.get('featured')!=='true'||w.tags.length>0)&&`${w.title} ${w.displayByline} ${w.authorName}`.toLowerCase().includes(q))
      const size=Number(url.searchParams.get('pageSize')||12),p=Math.min(Number(url.searchParams.get('page')||1),Math.max(1,Math.ceil(selected.length/size)))
      return json({items:selected.slice((p-1)*size,p*size),total:selected.length,page:p,pageSize:size,summary:{total:state.works.filter(w=>w.status!=='DRAFT').length,published:state.works.filter(w=>w.status==='PUBLISHED').length,draft:12,withdrawn:state.works.filter(w=>w.status==='WITHDRAWN').length,featured:state.works.filter(w=>w.tags.length).length}})
    }
    const workId=path.match(/^\/api\/admin\/works\/(\d+)(?:\/(featured|withdraw|approve))?$/)
    if(workId){const work=state.works.find(w=>w.id===Number(workId[1]));if(!work||work.status==='DRAFT')return json({message:'文章不存在'},404);if(method==='DELETE'){if(state.failDeleteWork)return json({message:'删除文章失败，请重试'},503);state.works=state.works.filter(w=>w.id!==work.id);return json({})}if(workId[2]==='approve'){if(state.failApprove)return json({message:'审核未通过，请重试'},503);work.status='PUBLISHED';return json({})}if(workId[2]==='featured'){work.tags=request.postDataJSON().active?[featuredTag]:[];return json({})}if(workId[2]==='withdraw'){if(state.failWithdraw)return json({message:'下架失败，请稍后重试'},503);work.status='WITHDRAWN';return json({})}return json(work)}
    if(path==='/api/admin/registration/qq-groups')return json([{groupId:'123456',count:3}])
    if(path==='/api/admin/registration/qq-allowlist'){if(method==='POST'){for(const q of state.qqs)if(request.postDataJSON().qqNumbers.includes(q.qqNumber))q.active=true;return json({count:request.postDataJSON().qqNumbers.length})}return json(state.qqs)}
    if(path.startsWith('/api/admin/registration/qq-allowlist/')){state.qqs.find(q=>q.qqNumber===path.split('/').at(-1))!.active=false;return json({})}
    if(path==='/api/admin/tags'){state.tags.push({id:state.tags.length+10,kind:'TOPIC',workCount:0,...request.postDataJSON()});return json({})}
    if(path.match(/^\/api\/admin\/activities\/\d+\/availability$/)){Object.assign(state.tags.find(t=>t.id===Number(path.split('/')[4]))!,{activityActive:request.postDataJSON().active});return json({})}
    if(path==='/api/member/import')return json({text:'导入的校园文字。\n\n第二段文字。'})
    if(path==='/api/member/works'&&method==='POST')return json({id:999,status:request.postDataJSON().publish?'PUBLISHED':'DRAFT'})
    return json([],404)
  })
  return state
}
async function noOverflow(page:Page){expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(await page.evaluate(()=>innerWidth))}
test.beforeAll(async()=>{await mkdir('../output/admin-redesign',{recursive:true})})

test('overview gives distinct account and article routes with actual totals',async({page})=>{
  await fixture(page);await page.setViewportSize({width:1440,height:1100});await page.goto('/#/admin')
  await expect(page.getByRole('heading',{name:'工作概览',exact:true})).toBeVisible()
  await expect(page.locator('.admin-metric').first()).toContainText('16')
  await expect(page.locator('.admin-metric').nth(1)).toContainText('120')
  await expect(page.locator('.admin-recent')).toHaveCount(5);await noOverflow(page)
  await page.screenshot({path:'../output/admin-redesign/overview-desktop.png',fullPage:true})
  await page.locator('.admin-nav').getByRole('link',{name:'账户管理',exact:true}).click();await expect(page).toHaveURL(/#\/admin\/accounts$/)
  await expect(page.locator('.admin-nav a[aria-current=page]')).toHaveText('账户管理')
  await page.locator('.admin-nav').getByRole('link',{name:'文章管理 / 审核',exact:true}).click();await expect(page).toHaveURL(/#\/admin\/works$/)
})

test('accounts support role filters, pagination, permission changes and public authorization',async({page})=>{
  const state=await fixture(page);await page.goto('/#/admin/accounts');await expect(page.locator('.admin-table tbody tr')).toHaveCount(12)
  await page.getByRole('button',{name:'下一页账户'}).click();await expect(page.locator('.admin-table tbody tr')).toHaveCount(4)
  await page.getByRole('textbox',{name:'搜索账户',exact:true}).fill('社员2');await expect(page.locator('.admin-table tbody tr')).toHaveCount(1)
  await page.getByRole('button',{name:'配置权限',exact:true}).click();const dialog=page.getByRole('dialog')
  await expect(dialog).toBeVisible();await dialog.getByLabel('账户角色').selectOption('MEMBER');await expect(dialog.getByLabel('投稿与创作')).toBeChecked()
  await dialog.getByLabel('发表评论').uncheck();await dialog.getByRole('button',{name:'保存权限',exact:true}).click();await expect(dialog).toHaveCount(0)
  expect(state.accounts[2].role).toBe('MEMBER');expect(state.accounts[2].permissions.COMMENT).toBe(false)
  await page.getByRole('button',{name:/公用账户/}).click();await expect(page).toHaveURL(/view=public/)
  await page.getByRole('button',{name:'管理授权',exact:true}).first().click();await page.getByRole('textbox',{name:'搜索授权社员'}).fill('社员3')
  await page.getByRole('dialog').getByRole('checkbox').check();await page.getByRole('button',{name:'保存授权名单'}).click();await expect.poll(()=>state.publicAccounts[0].memberIds).toContain(4)
  await page.goto('/#/admin/accounts');state.failPermissions=true;await page.locator('.admin-table tbody tr').first().getByRole('button',{name:'配置权限'}).click();await page.getByRole('button',{name:'保存权限',exact:true}).click();await expect(page.getByRole('dialog').getByRole('alert')).toContainText('必须至少保留一位超级管理员')
})

test('articles find old content, preview body, manage picks and confirm withdrawal with retry',async({page})=>{
  const state=await fixture(page);await page.setViewportSize({width:1440,height:1050});await page.goto('/#/admin/works')
  await expect(page.locator('.admin-table tbody tr')).toHaveCount(12);await page.screenshot({path:'../output/admin-redesign/works-desktop.png',fullPage:true})
  await page.getByRole('button',{name:'下一页文章'}).click();expect(state.requests.at(-1)?.searchParams.get('page')).toBe('2')
  await page.getByRole('textbox',{name:'搜索文章',exact:true}).fill('第137封');await page.getByRole('button',{name:'搜索',exact:true}).click();await expect(page.locator('.admin-table tbody tr')).toHaveCount(1);await expect(page.locator('.admin-table')).toContainText('第137封校园来信')
  await page.getByRole('button',{name:'预览',exact:true}).click();await expect(page.getByRole('dialog').locator('.article-content')).toContainText('晚风穿过走廊');await page.getByRole('button',{name:'关闭弹窗'}).click()
  await page.goto('/#/admin/works?status=PUBLISHED');const row=page.locator('.admin-table tbody tr').first()
  await row.getByRole('button',{name:'取消精选',exact:true}).click();await expect(row.getByRole('button',{name:'加入精选',exact:true})).toBeVisible()
  await row.getByRole('button',{name:'加入精选',exact:true}).click();expect(state.works[0].tags).toHaveLength(1)
  await row.getByRole('button',{name:'预览',exact:true}).click();await page.screenshot({path:'../output/admin-redesign/preview-desktop.png',fullPage:true})
  await page.getByRole('button',{name:'下架文章',exact:true}).click();await expect(page.getByRole('dialog',{name:'确认下架文章'})).toBeVisible();expect(state.works[0].status).toBe('PUBLISHED')
  state.failWithdraw=true;await page.getByRole('button',{name:'确认下架',exact:true}).click();await expect(page.getByRole('dialog',{name:'确认下架文章'}).getByRole('alert')).toContainText('下架失败')
  state.failWithdraw=false;await page.getByRole('button',{name:'确认下架',exact:true}).click();await expect(page.getByRole('dialog')).toHaveCount(0);expect(state.works[0].status).toBe('WITHDRAWN')
  state.failWorks=true;await page.getByRole('button',{name:'刷新文章'}).click();await expect(page.getByRole('alert')).toContainText('暂时无法加载文章')
  state.failWorks=false;await page.getByRole('button',{name:'重新加载'}).click();await expect(page.locator('.admin-table tbody tr')).toHaveCount(12)
  await noOverflow(page)
})

test('public identities see only their authorized workspaces and direct routes are guarded',async({page})=>{
  const state=await fixture(page,'editor');await page.goto('/#/admin/works');await expect(page.locator('.admin-nav a')).toHaveCount(4)
  await page.goto('/#/admin/accounts');await expect(page).toHaveURL(/#\/admin$/)
  await page.goto('/#/admin/settings');await expect(page).toHaveURL(/#\/admin$/)
  state.identity='activity';await page.reload();await expect(page.locator('.admin-nav a')).toHaveCount(4);await page.goto('/#/admin/works');await expect(page).toHaveURL(/#\/admin\/works$/)
  state.identity='tech';await page.goto('/#/admin/settings');await page.reload();await page.goto('/#/admin/settings');await expect(page.getByRole('heading',{name:'站点设置',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'注册设置',exact:true})).toHaveCount(0)
  state.identity='restricted';await page.reload();await page.goto('/#/admin/works');await expect(page).toHaveURL(/#\/account$/)
})

test('site settings separate registration and QQ roster with working changes',async({page})=>{
  const state=await fixture(page);await page.goto('/#/admin/settings');await page.getByLabel('站点名称', {exact:true}).fill('海燕文学社');await page.getByRole('button',{name:'保存站点资料'}).click();expect(state.settings.siteName).toBe('海燕文学社')
  await page.getByRole('button',{name:'注册设置',exact:true}).click();await page.getByLabel('开放新用户注册').uncheck();await page.getByRole('button',{name:'保存注册设置'}).click();expect(state.settings.registrationEnabled).toBe(false)
  await page.getByRole('button',{name:'QQ 注册名单',exact:true}).click();await expect(page.locator('.admin-table tbody tr')).toHaveCount(3)
  await page.locator('.admin-table tbody tr').last().getByRole('button',{name:'启用',exact:true}).click();await expect(page.locator('.admin-table tbody tr').last()).toContainText('已启用');expect(state.qqs[2].active).toBe(true)
  await page.getByRole('textbox',{name:'搜索 QQ 注册名单'}).fill('林间');await expect(page.locator('.admin-table tbody tr')).toHaveCount(1)
})

test('activity creation and editorial import stay in their dedicated pages',async({page})=>{
  const state=await fixture(page);await page.goto('/#/admin/tags');await page.getByLabel('活动名称').fill('冬日诗笺');await page.getByLabel('征稿说明').fill('记录冬日里的微光。');await page.getByRole('button',{name:'创建并开放活动'}).click();await expect(page.locator('.admin-tag-list')).toContainText('冬日诗笺')
  await page.locator('.admin-tag-list article').filter({hasText:'冬日诗笺'}).getByRole('button',{name:'结束活动'}).click();await expect(page.locator('.admin-tag-list article').filter({hasText:'冬日诗笺'})).toContainText('已结束');await page.locator('.admin-tag-list article').filter({hasText:'冬日诗笺'}).getByRole('button',{name:'重新开放'}).click();await expect(page.locator('.admin-tag-list article').filter({hasText:'冬日诗笺'})).toContainText('开放中')
  state.identity='editor';await page.reload();await expect(page.getByRole('heading',{name:'活动与标签',exact:true})).toBeVisible();await page.locator('.admin-nav').getByRole('link',{name:'批量导入'}).click();await page.locator('input[type=file]').setInputFiles([{name:'秋日来信.txt',mimeType:'text/plain',buffer:Buffer.from('一封秋日来信。')}]);await page.getByRole('button',{name:/导入并保存草稿/}).click();await expect(page.locator('.admin-import-result')).toContainText('已保存为草稿');expect(state.changes.find(c=>c.path==='/api/member/works')?.body.publish).toBe(false)
})

test('mobile pages and permission dialog fit the viewport',async({page})=>{
  await fixture(page);await page.setViewportSize({width:390,height:844})
  for(const path of ['/admin','/admin/accounts','/admin/works','/admin/tags','/admin/settings?tab=qq']){
    await page.goto(`/#${path}`);await expect(page.locator('.admin-page-heading h1')).toBeVisible();await expect(page.locator('.admin-empty').filter({hasText:/正在加载/})).toHaveCount(0);await noOverflow(page)
    await page.screenshot({path:`../output/admin-redesign/mobile-${path.split('/').at(-1)!.split('?')[0]}.png`,fullPage:true})
  }
  await page.goto('/#/admin/accounts');await page.locator('.admin-table tbody tr').first().getByRole('button',{name:'配置权限'}).click();await expect(page.getByRole('dialog')).toBeVisible();await noOverflow(page);await page.screenshot({path:'../output/admin-redesign/permissions-mobile.png',fullPage:true})
})

test('public identity basic abilities are shared and never grant administrative deletion',async({page})=>{
  const state=await fixture(page,'official');state.publicStoredRole='SUPER_ADMIN'
  for(const identity of ['official','editor','activity','tech']){
    state.identity=identity;await page.goto('/#/admin/works');await page.reload();await expect(page.getByRole('heading',{name:'文章管理 / 审核',exact:true})).toBeVisible()
    await expect(page.getByRole('button',{name:'删除文章',exact:true})).toHaveCount(0);await expect(page.locator('.admin-status-tabs')).not.toContainText('草稿');await expect(page.locator('.admin-table tbody')).not.toContainText('第121封')
    await page.goto('/#/admin/tags');await expect(page.getByRole('button',{name:'创建并开放活动'})).toBeVisible();await expect(page.getByRole('button',{name:'删除活动',exact:true})).toHaveCount(0)
    await page.goto('/#/write');await expect(page.getByLabel('体裁',{exact:true}).locator('option')).toContainText(['诗歌','散文','小说','随笔','公告','其他'])
  }
})

test('administrator deletes articles under any public identity while only super administrators delete users',async({page})=>{
  const state=await fixture(page,'activity');state.actorRoleOverride='ADMIN';await page.goto('/#/admin/works')
  const first=page.locator('.admin-table tbody tr').first();await first.getByRole('button',{name:'删除文章',exact:true}).click();const dialog=page.getByRole('dialog',{name:'永久删除文章'})
  await dialog.getByRole('button',{name:'取消',exact:true}).click();expect(state.works).toHaveLength(137)
  await first.getByRole('button',{name:'删除文章',exact:true}).click();state.failDeleteWork=true;await dialog.getByRole('button',{name:'确认永久删除文章'}).click();await expect(dialog.getByRole('alert')).toContainText('删除文章失败')
  state.failDeleteWork=false;await dialog.getByRole('button',{name:'确认永久删除文章'}).click();await expect(dialog).toHaveCount(0);expect(state.works).toHaveLength(136)
  await page.goto('/#/admin/accounts');await expect(page.getByRole('button',{name:'删除用户',exact:true})).toHaveCount(0)
  state.actorRoleOverride='SUPER_ADMIN';await page.reload();const row=page.locator('.admin-table tbody tr').filter({hasText:'member1'}).first();await row.getByRole('button',{name:'删除用户',exact:true}).click()
  const userDialog=page.getByRole('dialog',{name:'删除用户 · member1'});state.failDeleteAccount=true;await userDialog.getByRole('button',{name:'确认删除用户'}).click();await expect(userDialog.getByRole('alert')).toContainText('删除用户失败')
  state.failDeleteAccount=false;await userDialog.getByRole('button',{name:'确认删除用户'}).click();await expect(userDialog).toHaveCount(0);expect(state.accounts.some(a=>a.username==='member1')).toBe(false)
})

test('public staff can approve withdrawn articles without exposing draft rows',async({page})=>{
  const state=await fixture(page,'official');await page.goto('/#/admin/works?status=WITHDRAWN');await page.locator('.admin-table tbody tr').first().getByRole('button',{name:'审核通过',exact:true}).click()
  const popup=page.getByRole('dialog',{name:'审核通过并恢复公开'});state.failApprove=true;await popup.getByRole('button',{name:'确认审核通过'}).click();await expect(popup.getByRole('alert')).toContainText('审核未通过')
  state.failApprove=false;await popup.getByRole('button',{name:'确认审核通过'}).click();await expect(popup).toHaveCount(0);expect(state.works.find(w=>w.id===133)?.status).toBe('PUBLISHED')
  await page.goto('/#/write');await page.getByLabel('作品标题').fill('文学社公告');await page.getByLabel('体裁',{exact:true}).selectOption('公告');await page.getByRole('button',{name:'保存为草稿'}).click();expect(state.changes.find(c=>c.path==='/api/member/works')?.body.genre).toBe('公告')
  state.identity='member';await page.reload();await expect(page.getByLabel('体裁',{exact:true}).locator('option[value="公告"]')).toHaveCount(0)
})
