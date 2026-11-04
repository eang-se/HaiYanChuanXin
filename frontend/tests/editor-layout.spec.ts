import {test, expect, type Page} from '@playwright/test'
import {mkdir} from 'node:fs/promises'

async function editorFixture(page: Page) {
  const saves: Record<string, any>[] = []
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    const json = (data: unknown) => route.fulfill({json: data})
    if (path === '/api/auth/me') return json({id: 29, username: 'writer', penName: '林间', role: 'MEMBER', profileCompleted: true, profilePromptSeen: true})
    if (path === '/api/auth/csrf') return json({token: 'fixture'})
    if (path === '/api/public/site') return json({name: '海燕传信'})
    if (path === '/api/public/traffic') return json({online: 12, visitors: 1280})
    if (path === '/api/public/tags') return json(['校园', '秋日来信', '生活随笔', '诗与远方', '青春', '阅读'].map((name, i) => ({id: i + 1, name})))
    if (path === '/api/public/activities') return json([{id:2,name:'秋日来信',kind:'TOPIC',isActivity:true,activityActive:true},{id:9,name:'结束的活动',kind:'TOPIC',isActivity:true,activityActive:false}])
    if (path === '/api/member/import') return json({text:'从文件导入的文字\n第二段'})
    if (path === '/api/member/pen-names') return json([{id: 3, name: '林间'}])
    if (path === '/api/member/collections') return json([{id: 7, title: '校园手记', published: false}])
    if (path === '/api/public/works/42' || path === '/api/member/works/42/draft') return json({id: 42, title: '晚风经过河岸', byline: '林间', genre: '散文', excerpt: '把日常的声音，写进自己的句子里。', cover: '', tags: [{id: 1, name: '校园'}], status: 'DRAFT', notes: '写于秋天的校园。', body: '<p>风从河岸慢慢吹过来，带着草木和泥土的气息。晚自习的铃声停了，校园里只留下脚步声。</p><p>我们把尚未写完的句子收进书页，等明天的光再照亮它。</p><h2>把晚风写进信里</h2><p>总有一些平凡的瞬间，值得被认真记住。一片落叶，一本翻开的书，还有并肩走过的那条小路。</p>'})
    if (path.startsWith('/api/member/works') && ['POST', 'PUT'].includes(route.request().method())) {
      saves.push(route.request().postDataJSON())
      return json({id: 42})
    }
    return json([])
  })
  return saves
}

test.beforeAll(async () => { await mkdir('../output/editor-redesign', {recursive: true}) })

test('focus and preview retain the manuscript and settings, with publication actions reachable while scrolling', async ({page}) => {
  const saves = await editorFixture(page)
  await page.setViewportSize({width: 1440, height: 1100})
  await page.goto('/#/write/42')
  await expect(page.getByLabel('作品标题')).toHaveValue('晚风经过河岸')
  await page.getByRole('group',{name:'作者署名'}).getByRole('button',{name:'林间',exact:true}).click()
  await page.getByLabel('所属合集', {exact: true}).selectOption('7')
  await page.getByRole('button', {name: '秋日来信', exact: true}).click()
  await expect(page.locator('.save-status')).toContainText('已保存')
  await page.screenshot({path: '../output/editor-redesign/desktop.png', fullPage: true})
  await page.getByRole('button', {name: '专注写作', exact: true}).click()
  await expect(page.getByRole('complementary', {name: '作品设置'})).toBeHidden()
  await page.getByLabel('作品正文').click()
  await page.keyboard.press('Control+End')
  await page.keyboard.insertText('专注时写下的新句子。')
  await page.getByRole('button', {name: '阅读预览', exact: true}).click()
  await expect(page.locator('.article-content')).toContainText('专注时写下的新句子。')
  await page.getByRole('button', {name: '继续编辑', exact: true}).click()
  await page.getByRole('button', {name: '显示作品设置', exact: true}).click()
  await expect(page.getByRole('group',{name:'作者署名'}).getByRole('button',{name:'林间',exact:true})).toHaveAttribute('aria-pressed','true')
  await expect(page.getByLabel('所属合集', {exact: true})).toHaveValue('7')
  await page.getByLabel('附注', {exact: true}).scrollIntoViewIfNeeded()
  const publish = await page.getByRole('button', {name: '检查并发布'}).boundingBox()
  expect(publish!.y).toBeGreaterThanOrEqual(0)
  expect(publish!.y + publish!.height).toBeLessThan(1100)
  await page.getByRole('button', {name: '保存为草稿'}).click()
  await expect(page.locator('.save-status')).toContainText('已保存')
  expect(saves.at(-1)).toMatchObject({penNameId: 3, collectionId: 7, activityIds: [2], contentTags: ['校园']})
  expect(saves.at(-1)!.body).toContain('专注时写下的新句子。')
})

test('activities are selected while content tags are written, deduplicated and removable',async({page})=>{
  const saves=await editorFixture(page);await page.goto('/#/write?tag=2')
  await expect(page.getByLabel('作品标题')).toBeEnabled()
  await expect(page.getByRole('button',{name:'秋日来信',exact:true})).toHaveAttribute('aria-pressed','true')
  await expect(page.getByRole('button',{name:'结束的活动',exact:true})).toHaveCount(0)
  await page.getByLabel('作品标题').fill('关于成长的一封信')
  const input=page.getByRole('textbox',{name:'填写内容标签'})
  await input.fill('秋日来信,校园');await input.press('Enter')
  await expect(page.getByRole('group',{name:'已添加的内容标签'})).toContainText('#秋日来信')
  await input.fill('#校园');await input.press('Enter');await expect(page.getByRole('button',{name:'移除内容标签 校园',exact:true})).toHaveCount(1)
  await page.getByRole('button',{name:'移除内容标签 校园',exact:true}).click()
  await input.fill('成长');await page.getByRole('button',{name:'保存为草稿'}).click()
  await expect(page.locator('.save-status')).toContainText('已保存')
  expect(saves.at(-1)).toMatchObject({activityIds:[2],contentTags:['秋日来信','成长']});expect(saves.at(-1)).not.toHaveProperty('tagIds')
  await input.fill('海燕精选');await input.press('Enter');await expect(page.getByRole('alert')).toContainText('由编辑部设置')
  await input.fill('一,二,三,四');await input.press('Enter');await expect(page.getByRole('alert')).toContainText('最多添加 5 个')
  await input.fill('');await page.getByRole('button',{name:'秋日来信',exact:true}).click();await page.getByRole('button',{name:'保存为草稿'}).click()
  await expect.poll(()=>saves.at(-1)?.activityIds).toEqual([])
})

test('historical closed activity survives editing and content fields exclude editorial marks',async({page})=>{
  const saves=await editorFixture(page)
  await page.route('**/api/member/works/42/draft',route=>route.fulfill({json:{id:42,title:'旧活动来信',genre:'散文',body:'<p>一封旧来信。</p>',excerpt:'',cover:'',status:'DRAFT',tags:[{id:9,name:'旧征稿',isActivity:true,activityActive:false},{id:7,name:'海燕精选',isActivity:false},{id:8,name:'回忆',isActivity:false}]}}))
  await page.goto('/#/write/42');await expect(page.getByLabel('作品标题')).toHaveValue('旧活动来信')
  await expect(page.getByRole('button',{name:/旧征稿.*已结束/})).toHaveAttribute('aria-pressed','true')
  await expect(page.getByRole('group',{name:'已添加的内容标签'})).toContainText('回忆')
  await expect(page.getByRole('group',{name:'已添加的内容标签'})).not.toContainText('海燕精选')
  await page.getByRole('button',{name:'保存为草稿'}).click();await expect.poll(()=>saves.at(-1)?.activityIds).toEqual([9])
  await page.getByRole('button',{name:/旧征稿.*已结束/}).click();await expect(page.getByRole('button',{name:/旧征稿.*已结束/})).toHaveCount(0)
})

test('empty and populated editors fit mobile and tablet widths with usable sticky controls', async ({page}) => {
  await editorFixture(page)
  await page.setViewportSize({width: 390, height: 844})
  await page.goto('/#/write')
  await expect(page.getByLabel('作品标题')).toBeEnabled()
  await page.screenshot({path: '../output/editor-redesign/mobile-empty.png', fullPage: true})
  await page.goto('/#/write/42')
  await expect(page.getByLabel('作品标题')).toHaveValue('晚风经过河岸')
  for (const width of [320, 390, 768, 1024]) {
    await page.setViewportSize({width, height: 844})
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await page.evaluate(() => window.scrollTo(0, 470))
    const actions = await page.locator('.workspace-actions').boundingBox()
    const toolbar = await page.locator('.editor-controls').boundingBox()
    expect(actions!.y).toBe(0)
    expect(toolbar!.y).toBeGreaterThanOrEqual(actions!.height - 1)
    const editor = await page.getByLabel('作品正文').boundingBox()
    expect(editor!.width).toBeGreaterThan(220)
  }
  await page.setViewportSize({width: 390, height: 844})
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({path: '../output/editor-redesign/mobile.png', fullPage: true})
  await page.getByRole('button', {name: '阅读预览', exact: true}).click()
  await expect(page.locator('.article-content')).toBeVisible()
  await page.getByRole('button', {name: '继续编辑', exact: true}).click()
  await expect(page.getByLabel('体裁', {exact: true})).toBeVisible()
})


test('mobile compose opens setup and imports into the editor',async({page})=>{
  const saves=await editorFixture(page);await page.setViewportSize({width:390,height:844});await page.goto('/#/about')
  await page.getByRole('button',{name:'写作',exact:true}).click()
  const dialog=page.getByRole('dialog',{name:'开始一篇新作品'});await expect(dialog).toBeVisible()
  await dialog.getByLabel('作品体裁').selectOption('诗歌')
  await dialog.locator('input[type=file]').setInputFiles({name:'文字.txt',mimeType:'text/plain',buffer:Buffer.from('从文件导入的文字')})
  await dialog.getByRole('button',{name:'进入编辑器'}).click()
  await expect(page.getByLabel('作品标题')).toHaveValue('未命名草稿');await expect(page.getByLabel('作品正文')).toContainText('从文件导入的文字')
  await expect(page.locator('.paper-kicker')).toContainText('诗歌')
  await page.setViewportSize({width:320,height:844});await expect(page.getByRole('button',{name:'插入内容'})).toBeVisible();await page.getByRole('button',{name:'插入内容'}).click();await expect(page.getByRole('menu',{name:'插入内容'})).toBeVisible();await page.keyboard.press('Escape');const toolsBox=await page.locator('.editor-toolbar').boundingBox(),expandBox=await page.getByRole('button',{name:'展开更多工具'}).boundingBox();expect(expandBox!.x+expandBox!.width).toBeLessThanOrEqual(toolsBox!.x+toolsBox!.width+1);await page.screenshot({path:'../output/editor-redesign/mobile-new.png'});await expect(page.getByRole('button',{name:'加粗'})).toBeVisible();await expect(page.getByRole('button',{name:'撤销'})).toBeVisible();await expect(page.getByRole('button',{name:'重做'})).toBeVisible();await expect(page.getByRole('button',{name:'引用'})).toBeHidden();await expect(page.getByRole('button',{name:'无序列表'})).toBeHidden();await page.getByRole('button',{name:'展开更多工具'}).click();await expect(page.getByRole('button',{name:'无序列表'})).toBeVisible();await expect(page.getByRole('button',{name:'引用'})).toBeVisible();await expect(page.getByRole('button',{name:'小标题'})).toBeVisible();const firstRow=await page.getByRole('button',{name:'加粗'}).boundingBox(),secondRow=await page.getByRole('button',{name:'无序列表'}).boundingBox();expect(secondRow!.y).toBeGreaterThanOrEqual(firstRow!.y+firstRow!.height)
  await page.getByRole('button',{name:'加粗'}).click();await page.getByLabel('作品正文').click();await page.keyboard.press('End');await page.keyboard.insertText('新文字')
  await page.getByRole('button',{name:'保存为草稿'}).click();await expect.poll(()=>saves.length).toBeGreaterThan(0);expect(saves.at(-1)).toMatchObject({title:'未命名草稿',genre:'诗歌'});expect(saves.at(-1)!.body).toContain('从文件导入的文字')
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})

for(const scenario of [{role:'MEMBER',type:'PUBLIC',allowed:false},{role:'ADMIN',type:'PUBLIC',allowed:true},{role:'SUPER_ADMIN',type:'PERSONAL',allowed:true}]){
  test(`announcement option respects actor role ${scenario.type} ${scenario.role}`,async({page})=>{
    await editorFixture(page);await page.route('**/api/auth/me',route=>route.fulfill({json:{id:29,username:'writer',penName:'林间',accountType:scenario.type,role:scenario.role,actorRole:scenario.role,profileCompleted:true,profilePromptSeen:true}}))
    await page.setViewportSize({width:390,height:844});await page.goto('/#/about');await page.getByRole('button',{name:'写作',exact:true}).click()
    await expect(page.locator('#new-genre option').filter({hasText:'公告'})).toHaveCount(scenario.allowed?1:0)
  })
}


test('activity writing opens the shared setup and preserves the activity',async({page})=>{
  const saves=await editorFixture(page);await page.setViewportSize({width:320,height:844});await page.goto('/#/activities')
  await expect(page.locator('.activity-card')).toHaveCount(1);await expect(page.locator('.activities-note')).toHaveCount(0);await expect(page.locator('.activities-count')).toHaveText('1 个主题');await page.screenshot({path:'../output/editor-redesign/activities-mobile.png'})
  const link=page.locator('.activity-card').getByRole('link',{name:'参与写作'});await expect(link).toHaveClass(/button/);await link.click()
  const dialog=page.getByRole('dialog',{name:'开始一篇新作品'});await expect(dialog).toBeVisible();await expect(page).toHaveURL(/activities/)
  await dialog.getByLabel('作品标题').fill('活动来信');await dialog.getByRole('button',{name:'进入编辑器'}).click()
  await expect(page).toHaveURL(/write\?tag=2/);await expect(page.getByLabel('作品标题')).toHaveValue('活动来信');await expect(page.getByRole('button',{name:'秋日来信',exact:true})).toHaveAttribute('aria-pressed','true')
  await page.getByRole('button',{name:'保存为草稿'}).click();await expect.poll(()=>saves.length).toBeGreaterThan(0);expect(saves.at(-1)).toMatchObject({title:'活动来信',activityIds:[2]})
})

test('desk and empty library writing entries use the same dialog on mobile',async({page})=>{
  await editorFixture(page);await page.setViewportSize({width:390,height:844});await page.goto('/#/account')
  await page.getByRole('link',{name:'新建作品',exact:true}).click();await expect(page.getByRole('dialog',{name:'开始一篇新作品'})).toBeVisible();await page.getByRole('button',{name:'关闭',exact:true}).click()
  await page.route('**/api/public/works**',route=>route.fulfill({json:{items:[],total:0}}));await page.goto('/#/explore');await page.getByRole('link',{name:'投稿',exact:true}).click();await expect(page.getByRole('dialog',{name:'开始一篇新作品'})).toBeVisible()
})


test('activity draft setup survives login with its selected file',async({page})=>{
  await editorFixture(page);let loggedIn=false
  await page.route('**/api/auth/me',route=>route.fulfill({json:loggedIn?{id:29,username:'writer',penName:'林间',role:'MEMBER',profileCompleted:true,profilePromptSeen:true}:null}))
  await page.route('**/api/auth/login',route=>{loggedIn=true;return route.fulfill({json:{}})})
  await page.setViewportSize({width:390,height:844});await page.goto('/#/activities');await page.locator('.activity-card').getByRole('link',{name:'参与写作'}).click()
  const dialog=page.getByRole('dialog',{name:'开始一篇新作品'});await dialog.getByLabel('作品标题').fill('登录后的来信');await dialog.locator('input[type=file]').setInputFiles({name:'来信.txt',mimeType:'text/plain',buffer:Buffer.from('文字')});await dialog.getByRole('button',{name:'进入编辑器'}).click()
  await expect(page).toHaveURL(/login/);await page.getByLabel('账号',{exact:true}).fill('writer');await page.getByLabel('密码',{exact:true}).fill('password');await page.locator('form').getByRole('button',{name:'登录',exact:true}).click()
  await expect(page).toHaveURL(/write\?tag=2/);await expect(page.getByLabel('作品标题')).toHaveValue('登录后的来信');await expect(page.getByLabel('作品正文')).toContainText('从文件导入的文字');await expect(page.getByRole('button',{name:'秋日来信',exact:true})).toHaveAttribute('aria-pressed','true')
})

test('activity page shows an honest empty state and retries errors',async({page})=>{
  await editorFixture(page);let fail=true
  await page.route('**/api/public/activities',route=>fail?route.fulfill({status:503,json:{message:'暂不可用'}}):route.fulfill({json:[]}))
  await page.setViewportSize({width:320,height:844});await page.goto('/#/activities');await expect(page.getByRole('alert')).toContainText('暂不可用')
  fail=false;await page.getByRole('button',{name:'重新加载'}).click();await expect(page.locator('.activities-state')).toContainText('下一场相遇，正在酝酿');await expect(page.locator('.activities-count')).toHaveText('0 个主题')
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})
