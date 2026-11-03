import {test,expect,type Page} from '@playwright/test'
import {mkdir} from 'node:fs/promises'

async function fixture(page:Page){
  const state={accountType:'PERSONAL',actorPenName:'个人编辑者',collections:[] as any[],collectionSaves:[] as any[],failCollection:false,pens:[{id:3,name:'林间',workCount:1}],saves:[] as any[],binds:[] as string[],availability:'AVAILABLE',failAvailability:false,bindConflict:false,failDelete:false,deletes:0,
    tags:[{id:5,name:'秋日来信',description:'秋天的一封信',isActivity:true,activityActive:true,kind:'TOPIC',workCount:1}],
    work:{id:42,title:'走廊里的来信',genre:'散文',body:'<p>晚风穿过走廊。</p>',bodyJson:null as any,draftRevision:0,status:'DRAFT',bylineMode:'BOUND',byline:'林间',penNameId:3 as number|null,displayByline:'林间',authorId:29,authorName:'发布账号',authorAvatar:'',tags:[],excerpt:'一封校园来信',wordCount:9,cover:'',notes:'',sourceNote:'',collectionId:null,collection:null,publishedAt:'2026-11-03T02:00:00Z',updatedAt:'2026-11-03T02:00:00Z',likes:0,commentCount:0}}
  await page.route('**/api/**',async route=>{
    const request=route.request(),url=new URL(request.url()),path=url.pathname,method=request.method(),json=(value:unknown,status=200)=>route.fulfill({json:value,status})
    if(path==='/api/auth/me')return json({id:29,username:'writer',penName:'发布账号',role:'ADMIN',accountType:state.accountType,actorRole:'ADMIN',actorPenName:state.actorPenName,actorUsername:'personal_writer',permissions:{WRITE:true,UPLOAD:true},profileCompleted:true,profilePromptSeen:true})
    if(path==='/api/auth/csrf')return json({token:'fixture'})
    if(path==='/api/public/site')return json({name:'海燕传信'})
    if(path==='/api/public/traffic')return json({online:1,visitors:10})
    if(path==='/api/public/activities')return json(state.tags)
    if(path==='/api/public/tags')return json(state.tags)
    if(path==='/api/admin/activities/5'&&method==='DELETE'){state.deletes++;if(state.failDelete)return json({message:'活动删除失败，请重试'},503);state.tags=[];return json({})}
    if(path==='/api/member/bookmarks')return json([])
    if(path==='/api/member/collections'){
      if(method==='POST'){const c={...request.postDataJSON(),id:77,authorId:29,works:[]};state.collectionSaves.push(c);if(state.failCollection)return json({message:'合集保存失败'},503);state.collections.push(c);return json({id:c.id})}return json(state.collections)
    }
    if(path.startsWith('/api/member/collections/')&&method==='PUT'){const c={...request.postDataJSON(),id:Number(path.split('/').at(-1)),authorId:29,works:[]};state.collectionSaves.push(c);if(state.failCollection)return json({message:'合集保存失败'},503);Object.assign(state.collections.find(item=>item.id===c.id),c);return json({id:c.id})}
    if(path.startsWith('/api/public/collections/'))return json(state.collections.find(c=>c.id===Number(path.split('/').at(-1))))
    if(path==='/api/member/pen-names/availability')return state.failAvailability?json({message:'暂时无法确认笔名'},503):json(url.searchParams.get('name')==='林间'?{status:'SELF',pen:state.pens[0]}:{status:state.availability})
    if(path==='/api/member/pen-names'){
      if(method==='POST'){state.binds.push(request.postDataJSON().name);if(state.bindConflict)return json({message:'该笔名已被他人绑定'},409);const pen={id:state.pens.length+10,name:request.postDataJSON().name,workCount:0};state.pens.push(pen);return json(pen)}return json(state.pens)
    }
    if(path==='/api/member/works'&&method==='GET')return json([state.work])
    if(path==='/api/member/works/42/draft'||path==='/api/public/works/42')return json(state.work)
    if(path==='/api/member/works/42/versions'||path==='/api/public/works/42/comments'||path==='/api/public/works/42/collections')return json([])
    if((path==='/api/member/works/42'||path==='/api/member/works')&&['POST','PUT'].includes(method)){
      const payload=request.postDataJSON();state.saves.push(payload);Object.assign(state.work,payload,{id:42,displayByline:payload.byline,draftRevision:state.work.draftRevision+1,status:payload.publish?'PUBLISHED':'DRAFT'});return json({id:42,status:state.work.status,draftRevision:state.work.draftRevision})
    }
    if(path==='/api/public/authors/29')return json({id:29,penName:'发布账号',bio:'',avatarUrl:''})
    if(path==='/api/public/works')return json({items:[state.work],total:1})
    return json([],404)
  })
  return state
}
async function enterName(page:Page,name:string){await page.getByRole('button',{name:'添加临时署名'}).click();await page.getByRole('dialog',{name:'编辑临时署名'}).getByRole('textbox',{name:'临时署名',exact:true}).fill(name);await page.getByRole('button',{name:'使用此署名'}).click()}
test.beforeAll(async()=>{await mkdir('../output/signature-management',{recursive:true})})

test('the writing desk opens bound pen name management directly',async({page})=>{
  await fixture(page);await page.goto('/#/account');await page.getByRole('button',{name:'管理绑定笔名',exact:true}).click()
  await expect(page).toHaveURL(/#\/account$/);await expect(page.getByRole('dialog',{name:'管理绑定笔名'})).toBeVisible();await expect(page.locator('.pen-name-list')).toContainText('林间')
  await page.getByLabel('笔名',{exact:true}).fill('书桌新笔名');await page.getByRole('button',{name:'绑定',exact:true}).click();await expect(page.locator('.pen-name-list')).toContainText('书桌新笔名')
})

test('finishing a new name asks immediately and yes adds a bound option before publication',async({page})=>{
  const state=await fixture(page);await page.goto('/#/write/42');await expect(page.getByRole('group',{name:'作者署名'}).getByRole('button',{name:'林间',exact:true})).toHaveAttribute('aria-pressed','true')
  await expect(page.getByRole('group',{name:'作者署名'}).locator('select')).toHaveCount(0)
  await expect(page.getByRole('group',{name:'作者署名'}).getByRole('button',{name:'添加临时署名'})).toHaveText('')
  await enterName(page,'远山');await expect(page.getByRole('dialog',{name:'是否绑定临时署名'})).toBeVisible();expect(state.binds).toEqual([])
  await page.getByRole('button',{name:'是，绑定笔名'}).click();await expect(page.getByRole('dialog')).toHaveCount(0);await expect(page.getByRole('group',{name:'作者署名'}).getByRole('button',{name:'远山',exact:true})).toHaveAttribute('aria-pressed','true');expect(state.binds).toEqual(['远山'])
  await page.getByRole('group',{name:'作者署名'}).getByRole('button',{name:'林间',exact:true}).click()
  await expect(page.getByRole('group',{name:'作者署名'}).getByRole('button',{name:'远山',exact:true})).toHaveAttribute('aria-pressed','false')
  await page.getByRole('group',{name:'作者署名'}).getByRole('button',{name:'远山',exact:true}).click()
  await page.getByRole('button',{name:'保存为草稿'}).click();await expect.poll(()=>state.saves.at(-1)?.penNameId).toBe(11);expect(state.saves.at(-1).bylineMode).toBe('BOUND')
})

test('no adds a temporary option and publishing does not ask again',async({page})=>{
  const state=await fixture(page);await page.goto('/#/write/42');await enterName(page,'过客');await page.getByRole('button',{name:'否，仅作临时署名'}).click()
  await expect(page.getByRole('group',{name:'作者署名'}).getByRole('button',{name:'过客 临时',exact:true})).toHaveAttribute('aria-pressed','true');await expect(page.getByRole('group',{name:'作者署名'}).getByRole('button',{name:'过客 临时',exact:true})).toHaveCount(1);expect(state.binds).toEqual([])
  await page.getByRole('group',{name:'作者署名'}).getByRole('button',{name:'佚名',exact:true}).click()
  await page.getByRole('group',{name:'作者署名'}).getByRole('button',{name:'过客 临时',exact:true}).click()
  await page.getByRole('button',{name:'检查并发布',exact:true}).click();await expect(page).toHaveURL(/#\/work\/42$/);expect(state.saves.at(-1)).toMatchObject({publish:true,byline:'过客',bylineMode:'TEMPORARY',penNameId:0});expect(state.binds).toEqual([])
})

test('a name held by someone else is added as temporary without a binding question',async({page})=>{
  const state=await fixture(page);state.availability='OTHER';await page.goto('/#/write/42');await enterName(page,'他人笔名')
  await expect(page.getByRole('dialog')).toHaveCount(0);await expect(page.getByRole('group',{name:'作者署名'}).getByRole('button',{name:'他人笔名 临时',exact:true})).toHaveAttribute('aria-pressed','true');expect(state.binds).toEqual([])
  await page.getByRole('button',{name:'保存为草稿'}).click();await expect.poll(()=>state.saves.at(-1)?.bylineMode).toBe('TEMPORARY');expect(state.saves.at(-1).penNameId).toBe(0)
})

test('a concurrent binding conflict becomes temporary and lookup failures can be retried',async({page})=>{
  const state=await fixture(page);state.failAvailability=true;await page.goto('/#/write/42');await enterName(page,'并发笔名');await expect(page.getByRole('dialog').getByRole('alert')).toContainText('暂时无法确认')
  await expect(page.getByRole('group',{name:'作者署名'}).getByRole('button',{name:'林间',exact:true})).toHaveAttribute('aria-pressed','true');state.failAvailability=false;await page.getByRole('button',{name:'使用此署名'}).click();state.bindConflict=true;await page.getByRole('button',{name:'是，绑定笔名'}).click()
  await expect(page.getByRole('dialog')).toHaveCount(0);await expect(page.getByRole('group',{name:'作者署名'}).getByRole('button',{name:'并发笔名 临时',exact:true})).toHaveAttribute('aria-pressed','true')
})

test('anonymous credit preserves the publishing account and stays in desk and author lists',async({page})=>{
  const state=await fixture(page);await page.goto('/#/write/42');await page.getByRole('group',{name:'作者署名'}).getByRole('button',{name:'佚名',exact:true}).click();await page.getByRole('button',{name:'检查并发布',exact:true}).click();await expect(page).toHaveURL(/#\/work\/42$/)
  expect(state.saves.at(-1)).toMatchObject({bylineMode:'ANONYMOUS',byline:'佚名',penNameId:0});await expect(page.locator('.reading-byline .work-byline')).toHaveText('佚名');await expect(page.locator('.reading-byline .publisher-link')).toHaveAttribute('href','#/author/29')
  await page.goto('/#/account');await expect(page.locator('.desk-work-item')).toContainText('走廊里的来信');await expect(page.locator('.desk-work-item .publisher-link')).toContainText('发布账号')
  await page.goto('/#/author/29');await expect(page.locator('.work-card,.work-row')).toContainText('走廊里的来信')
})

test('activity deletion confirms, cancels and retries while preserving the article',async({page})=>{
  const state=await fixture(page);await page.goto('/#/admin/tags');await page.getByRole('button',{name:'删除活动',exact:true}).click();await expect(page.getByRole('dialog',{name:'删除活动'})).toContainText('文章内容保留');expect(state.deletes).toBe(0)
  await page.getByRole('button',{name:'取消',exact:true}).click();expect(state.tags).toHaveLength(1)
  await page.getByRole('button',{name:'删除活动',exact:true}).click();state.failDelete=true;await page.getByRole('button',{name:'确认删除活动',exact:true}).click();await expect(page.getByRole('dialog').getByRole('alert')).toContainText('活动删除失败')
  state.failDelete=false;await page.getByRole('button',{name:'确认删除活动',exact:true}).click();await expect(page.getByRole('dialog')).toHaveCount(0);expect(state.tags).toHaveLength(0);expect(state.work.body).toBe('<p>晚风穿过走廊。</p>')
})

test('mobile signature entry and immediate binding choice fit the viewport',async({page})=>{
  await fixture(page);await page.setViewportSize({width:390,height:844});await page.goto('/#/write/42');await page.getByRole('button',{name:'添加临时署名'}).click()
  await page.getByRole('dialog').getByRole('textbox',{name:'临时署名',exact:true}).fill('手机里的来信');await page.screenshot({path:'../output/signature-management/entry-mobile.png'})
  await page.getByRole('button',{name:'使用此署名'}).click();await expect(page.getByRole('dialog',{name:'是否绑定临时署名'})).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:'../output/signature-management/bind-mobile.png'})
  await page.getByRole('button',{name:'否，仅作临时署名'}).click();await expect(page.getByRole('group',{name:'作者署名'}).getByRole('button',{name:'手机里的来信 临时',exact:true})).toHaveAttribute('aria-pressed','true')
  await page.getByRole('group',{name:'作者署名'}).scrollIntoViewIfNeeded();await page.screenshot({path:'../output/signature-management/buttons-mobile.png'})
})

test('bound name buttons wrap on mobile and remain directly selectable',async({page})=>{
  const state=await fixture(page);state.pens.push(...Array.from({length:12},(_,i)=>({id:i+20,name:`写在山海之间的笔名${i+1}`,workCount:0})))
  await page.setViewportSize({width:390,height:844});await page.goto('/#/write/42')
  const group=page.getByRole('group',{name:'作者署名'})
  await expect(group.getByRole('button')).toHaveCount(15)
  await expect(group.locator('select')).toHaveCount(0)
  await group.getByRole('button',{name:'写在山海之间的笔名12',exact:true}).click()
  await expect(group.getByRole('button',{name:'写在山海之间的笔名12',exact:true})).toHaveAttribute('aria-pressed','true')
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
  await page.screenshot({path:'../output/signature-management/buttons-wrapped-mobile.png'})
})

test('pen name management stays in the editor and updates its signature buttons',async({page})=>{
  await fixture(page);await page.goto('/#/write/42');await page.getByRole('button',{name:'管理绑定笔名',exact:true}).click()
  const popup=page.getByRole('dialog',{name:'管理绑定笔名'});await expect(popup).toBeVisible();await expect(page).toHaveURL(/#\/write\/42$/)
  await popup.getByLabel('笔名',{exact:true}).fill('弹窗里的笔名');await popup.getByRole('button',{name:'绑定',exact:true}).click();await expect(popup.locator('.pen-name-list')).toContainText('弹窗里的笔名')
  await popup.getByRole('button',{name:'关闭管理弹窗'}).click();await expect(page.getByRole('group',{name:'作者署名'}).getByRole('button',{name:'弹窗里的笔名',exact:true})).toBeVisible()
  await expect(page.getByLabel('作品标题')).toHaveValue('走廊里的来信');await expect(page.getByLabel('作品正文')).toContainText('晚风穿过走廊。')
})

test('editor credit appends once and uses the personal operator under a public identity',async({page})=>{
  const state=await fixture(page);state.accountType='PUBLIC';await page.goto('/#/write/42')
  await page.getByLabel('附注',{exact:true}).fill('保留原有创作背景。');await page.getByRole('button',{name:'添加编辑者字段'}).click();await page.getByRole('button',{name:'添加编辑者字段'}).click()
  await expect(page.getByLabel('附注',{exact:true})).toHaveValue('保留原有创作背景。\n本文编辑为：个人编辑者')
  await page.getByRole('button',{name:'保存为草稿'}).click();await expect.poll(()=>state.saves.at(-1)?.notes).toBe('保留原有创作背景。\n本文编辑为：个人编辑者')
})

test('collections are created and edited in a dialog with failed saves retryable and text retained',async({page})=>{
  const state=await fixture(page);await page.goto('/#/write/42');await page.getByRole('button',{name:'新建合集',exact:true}).click()
  const popup=page.getByRole('dialog',{name:'创建与管理合集'});await popup.getByLabel('合集名称').fill('窗边的信');state.failCollection=true;await popup.getByRole('button',{name:'保存合集',exact:true}).click()
  await expect(popup.getByRole('alert')).toContainText('合集保存失败');state.failCollection=false;await popup.getByRole('button',{name:'保存合集',exact:true}).click()
  await expect(popup.getByLabel('合集名称')).toHaveValue('窗边的信');await popup.getByLabel('合集名称').fill('窗边的来信');await popup.getByRole('button',{name:'保存合集',exact:true}).click()
  await expect(popup.getByRole('button',{name:'窗边的来信',exact:false})).toBeVisible();await popup.getByRole('button',{name:'关闭管理弹窗'}).click()
  await expect(page).toHaveURL(/#\/write\/42$/);await expect(page.getByLabel('所属合集',{exact:true})).toHaveValue('77');await expect(page.getByLabel('作品正文')).toContainText('晚风穿过走廊。')
})

test('desk management dialogs fit mobile and discard protection retains unsaved collection changes',async({page})=>{
  await fixture(page);await page.setViewportSize({width:390,height:844});await page.goto('/#/account');await page.getByRole('button',{name:'新建合集',exact:true}).click()
  const popup=page.getByRole('dialog',{name:'创建与管理合集'});await popup.getByLabel('合集名称').fill('尚未保存的合集')
  page.once('dialog',dialog=>dialog.dismiss());await popup.getByRole('button',{name:'关闭管理弹窗'}).click();await expect(popup.getByLabel('合集名称')).toHaveValue('尚未保存的合集')
  expect(await popup.evaluate(el=>el.scrollWidth<=el.clientWidth)).toBe(true);await page.screenshot({path:'../output/signature-management/collections-modal-mobile.png'})
  page.once('dialog',dialog=>dialog.accept());await page.keyboard.press('Escape');await expect(popup).toHaveCount(0);await expect(page).toHaveURL(/#\/account$/)
  await page.getByRole('button',{name:'管理绑定笔名',exact:true}).click();await expect(page.getByRole('dialog',{name:'管理绑定笔名'})).toBeVisible();await page.screenshot({path:'../output/signature-management/pens-modal-mobile.png'})
})
