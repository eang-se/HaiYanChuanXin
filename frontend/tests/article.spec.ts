import {test,expect,type Page} from '@playwright/test'
import {mkdir} from 'node:fs/promises'

const src='/api/media/12345678-1234-1234-1234-123456789abc'
const passage='风从河岸慢慢吹过来，带着草木和泥土的气息。晚自习的铃声停了，校园里只留下脚步声。我们把尚未写完的句子收进书页，等明天的光再照亮它。'
const body=`<h2>沿着河岸</h2><p>${passage}</p><figure data-size="narrow"><img src="${src}" width="1200" height="800" alt="河畔暮色"><figcaption>暮色里的河岸 · 校园手记</figcaption></figure><h3>晚风</h3><p>风经过树梢<br>也经过我们的书页<br>一行字，留给明天</p><blockquote><p>把日常的声音，写进自己的句子里。</p></blockquote><ul><li><p>一片落叶</p></li><li><p>一本翻开的书</p></li></ul><hr><details><summary>写作后记</summary><div data-disclosure-body><p>这些文字来自一次放学后的散步。</p><h3>折叠章节</h3><p>展开后可以继续阅读。</p></div></details><h2>沿着河岸</h2>${Array.from({length:7},()=>`<p>${passage}</p>`).join('')}<h3>尾声</h3><p>https://example.test/${'long'.repeat(60)}</p>`
const imageSvg='<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="800" viewBox="0 0 1200 800"><rect width="1200" height="800" fill="#dbe5d1"/><circle cx="880" cy="210" r="70" fill="#f3e4b2"/><path d="M0 400Q260 220 520 430T1200 350V800H0" fill="#829b73"/><path d="M0 590Q340 380 720 580T1200 510V800H0" fill="#5a7554"/><path d="M480 450Q750 550 540 800H940Q600 580 580 450" fill="#c4d6ce"/></svg>'
async function fixture(page:Page,html=body) {
  const state={body:html,genre:'散文',customGenre:'',layout:undefined as unknown,saves:[] as any[],failSave:false,failUpload:false,uploadDelay:0,saveDelay:0,activeSaves:0,maxActiveSaves:0,uploads:0,uploaded:Buffer.alloc(0),deskIds:[42,43,44],deleted:[] as number[],deleteStatus:0,deleteDelay:0}
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname,method=route.request().method()
    const json=(value:unknown,status=200)=>route.fulfill({status,json:value})
    if(path==='/api/auth/me')return json({id:29,username:'fixture',penName:'测试作者',role:'MEMBER',profileCompleted:true,profilePromptSeen:true,permissions:{WRITE:true,UPLOAD:true}})
    if(path==='/api/auth/csrf')return json({token:'fixture-only'})
    if(path==='/api/public/site')return json({name:'海燕传信'})
    if(path==='/api/public/tags')return json([{id:1,name:'校园',kind:'TOPIC',isActivity:false}])
    if(path==='/api/member/collections')return json([{id:7,title:'校园手记',published:false}])
    if(path==='/api/member/pen-names'||path==='/api/member/bookmarks')return json([])
    if(path==='/api/member/works'&&method==='GET')return json(state.deskIds.map(id=>({id,title:id===42?'草稿手记':id===43?'公开的文章':'已撤回的文章',genre:'散文',status:id===42?'DRAFT':id===43?'PUBLISHED':'WITHDRAWN',body:state.body,excerpt:'一篇校园手记',tags:[],collection:null,authorId:29,authorName:'测试作者',displayByline:'林间',wordCount:100,updatedAt:'2026-10-30T09:00:00'})))
    if(path.startsWith('/api/member/works/')&&method==='DELETE'){
      const id=Number(path.split('/').at(-1));state.deleted.push(id)
      if(state.deleteDelay)await new Promise(r=>setTimeout(r,state.deleteDelay))
      if(state.deleteStatus)return json({message:state.deleteStatus===409?'请先撤回已发布作品，再删除稿件':'测试：删除失败，请重试'},state.deleteStatus)
      state.deskIds=state.deskIds.filter(value=>value!==id);return route.fulfill({status:204})
    }
    if(path==='/api/public/works/42')return json({id:42,title:'晚风经过河岸',byline:'林间',displayByline:'林间',authorName:'测试作者',authorId:29,collection:null,collectionId:null,penNameId:null,genre:state.genre,customGenre:state.customGenre,layout:state.layout,body:state.body,excerpt:'校园手记',cover:'',status:'DRAFT',wordCount:980,tags:[],notes:'写于秋天的校园。',sourceNote:'',updatedAt:'2026-10-29T09:00:00'})
    if(path.startsWith('/api/member/works')&&['POST','PUT'].includes(method)){
      state.activeSaves++;state.maxActiveSaves=Math.max(state.maxActiveSaves,state.activeSaves)
      const payload=route.request().postDataJSON();state.saves.push(payload)
      if(state.saveDelay)await new Promise(r=>setTimeout(r,state.saveDelay))
      state.activeSaves--
      if(state.failSave)return json({message:'测试：保存失败，请重试'},503)
      state.body=payload.body;state.genre=payload.genre;state.customGenre=payload.customGenre;state.layout=payload.layout;return json({id:42,status:payload.publish?'PUBLISHED':'DRAFT'})
    }
    if(path==='/api/member/images'){
      state.uploaded=route.request().postDataBuffer() || Buffer.alloc(0)
      state.uploads++;if(state.uploadDelay)await new Promise(r=>setTimeout(r,state.uploadDelay))
      return state.failUpload?json({message:'测试：图片上传失败'},503):json({url:src,width:'1200',height:'800'})
    }
    if(path==='/api/member/import')return json({text:'导入的第一行\n第二行\n\n最后一行'})
    if(path===src)return route.fulfill({contentType:'image/svg+xml',body:imageSvg})
    return json([],404)
  })
  return state
}
async function noOverflow(page:Page) {expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true)}
test.beforeAll(async()=>{await mkdir('../output/article-upgrade',{recursive:true})})

test('reading is safe, has unique TOC links, opens folded headings and a keyboard/touch lightbox',async({page})=>{
  await fixture(page,body+'<img src="https://invalid.test/tracker"><script>window.attacked=true</script><p onclick="window.attacked=true">安全正文</p>')
  await page.setViewportSize({width:1440,height:1000});await page.goto('/#/work/42')
  await expect(page.locator('.article-content h2')).toHaveCount(2)
  const ids=await page.locator('.article-content h2,.article-content h3').evaluateAll(nodes=>nodes.map(n=>n.id));expect(new Set(ids).size).toBe(ids.length)
  expect(await page.locator('.article-content img').count()).toBe(1)
  await expect(page.locator('.article-content img')).toHaveAttribute('loading','lazy')
  await noOverflow(page);await page.screenshot({path:'../output/article-upgrade/reading-desktop.png',fullPage:true})
  await page.locator('.article-toc-side').getByRole('button',{name:'折叠章节',exact:true}).click()
  await expect(page.locator('.article-content details')).toHaveAttribute('open','')
  expect(await page.locator('.article-content h3').filter({hasText:'折叠章节'}).evaluate(n=>n.getBoundingClientRect().top)).toBeGreaterThanOrEqual(90)
  await page.locator('.article-content img').click();await expect(page.getByRole('dialog',{name:'图片大图'})).toBeVisible()
  await page.keyboard.press('Escape');await expect(page.getByRole('dialog',{name:'图片大图'})).not.toBeVisible()
  await page.setViewportSize({width:390,height:844});await page.evaluate(()=>scrollTo(0,0));await noOverflow(page)
  await page.locator('.article-toc-mobile summary').click();await page.locator('.article-toc-mobile').getByRole('button',{name:'尾声',exact:true}).click()
  expect(await page.getByRole('heading',{name:'尾声',exact:true}).evaluate(n=>n.getBoundingClientRect().top)).toBeGreaterThanOrEqual(90)
  await page.locator('.article-content img').click();await page.getByRole('button',{name:'关闭大图'}).click()
  await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:'../output/article-upgrade/reading-mobile.png',fullPage:true})
})

test('legacy text, new formats, notes and tags survive save failure/retry/reload',async({page})=>{
  const state=await fixture(page);await page.setViewportSize({width:1440,height:1000});await page.goto('/#/write/42')
  await expect(page.getByLabel('作品正文')).toBeVisible()
  const caption=page.locator('.tiptap figcaption');await caption.fill('新的图片说明');await page.getByLabel('附注',{exact:true}).fill('新附注')
  await page.getByRole('button',{name:'# 校园',exact:true}).click()
  await page.getByRole('combobox',{name:'所属合集',exact:true}).selectOption('7')
  await page.locator('.tiptap figure img').click();await page.getByRole('button',{name:'正文宽度',exact:true}).click()
  await page.locator('.tiptap summary').fill('可编辑的标题')
  state.failSave=true;await page.getByRole('button',{name:'保存为草稿'}).click();await expect(page.getByRole('alert').first()).toContainText('保存失败')
  await expect(caption).toHaveText('新的图片说明');state.failSave=false
  await page.getByRole('button',{name:'保存为草稿'}).click();await expect(page.locator('.save-status')).toContainText('已保存')
  expect(state.saves.at(-1).notes).toBe('新附注');expect(state.saves.at(-1).tagIds).toEqual([1]);expect(state.saves.at(-1).collectionId).toBe(7);expect(state.body).toContain('data-size="full"');expect(state.body).toContain('<br>')
  await page.reload();await expect(page.locator('.tiptap figcaption')).toHaveText('新的图片说明');await expect(page.locator('.tiptap summary')).toHaveText('可编辑的标题')
  await page.screenshot({path:'../output/article-upgrade/editor-desktop.png',fullPage:true})
  await page.getByRole('button',{name:'阅读预览',exact:true}).click();await expect(page.locator('.preview-area .article-content figcaption')).toHaveText('新的图片说明')
  await page.getByRole('button',{name:'继续编辑',exact:true}).click();await page.setViewportSize({width:390,height:844});await noOverflow(page)
  await page.screenshot({path:'../output/article-upgrade/editor-mobile.png',fullPage:true})
})

test('slash keyboard menu, Escape, IME suppression and tap insertion',async({page})=>{
  await fixture(page,'<p></p>');await page.goto('/#/write/42');const edit=page.getByLabel('作品正文')
  await edit.click();await page.keyboard.type('/');await expect(page.getByRole('menu',{name:'插入内容'})).toBeVisible()
  await page.keyboard.press('ArrowDown');await page.keyboard.press('Enter');await page.keyboard.insertText('新的小标题')
  await expect(page.locator('.tiptap h2')).toHaveText('新的小标题');await page.keyboard.press('Enter');await page.keyboard.type('/')
  await page.keyboard.press('Escape');await expect(page.getByRole('menu')).not.toBeVisible();await page.keyboard.press('Backspace')
  await edit.dispatchEvent('compositionstart',{data:''});await page.keyboard.insertText('/')
  await expect(page.getByRole('menu')).not.toBeVisible();await edit.dispatchEvent('keydown',{key:'Enter',keyCode:229,isComposing:true})
  await edit.dispatchEvent('compositionend',{data:'/'});await expect(page.getByRole('menu')).not.toBeVisible()
  await page.keyboard.press('Backspace');await page.keyboard.insertText('中文写作');await page.keyboard.press('Enter')
  await page.setViewportSize({width:390,height:844});await page.getByRole('button',{name:'插入',exact:true}).click();await page.getByRole('menuitem',{name:'折叠内容',exact:true}).click()
  await expect(page.locator('.tiptap details')).toHaveCount(1);await page.locator('.tiptap summary').fill('手机上的后记')
  await page.getByRole('button',{name:'插入',exact:true}).click();await expect(page.getByRole('menuitem',{name:'折叠内容',exact:true})).toBeDisabled();await page.keyboard.press('Escape')
  await noOverflow(page)
})

test('image failures retain text, retry once, and saves wait for the pending upload',async({page})=>{
  const state=await fixture(page,'<p>上传时保留的文字</p>');await page.goto('/#/write/42')
  const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=','base64')
  await page.getByLabel('作品正文').click();await page.keyboard.press('Control+End')
  state.failUpload=true
  await page.locator('input[type=file][accept="image/jpeg,image/png"]').setInputFiles({name:'river.png',mimeType:'image/png',buffer:png})
  await expect(page.getByRole('alert')).toContainText('图片上传失败');await expect(page.getByLabel('作品正文')).toContainText('上传时保留的文字')
  state.failUpload=false;state.uploadDelay=600;await page.getByRole('button',{name:'重试图片'}).click()
  await expect(page.getByLabel('插入图片',{exact:true})).toBeDisabled()
  await page.getByRole('button',{name:'保存为草稿'}).click();await expect(page.locator('.save-status')).toContainText('已保存')
  expect(state.uploads).toBe(2);expect(state.saves.at(-1).body).toContain(src);expect(state.maxActiveSaves).toBe(1)
})

test('large browser images are resized before upload and imports preserve blank lines',async({page})=>{
  const state=await fixture(page,'<p>正文</p>');await page.goto('/#/write/42');await expect(page.getByLabel('作品正文')).toBeVisible()
  const encoded=await page.evaluate(()=>{const canvas=document.createElement('canvas');canvas.width=2400;canvas.height=1200;const ctx=canvas.getContext('2d')!;ctx.fillStyle='#64815c';ctx.fillRect(0,0,2400,1200);return canvas.toDataURL('image/png').split(',')[1]!})
  await page.locator('input[type=file][accept="image/jpeg,image/png"]').setInputFiles({name:'large.png',mimeType:'image/png',buffer:Buffer.from(encoded,'base64')})
  await expect(page.locator('.tiptap img')).toHaveCount(1)
  const multipart=state.uploaded,start=multipart.indexOf('\r\n\r\n')+4,end=multipart.lastIndexOf('\r\n--')
  expect(multipart.subarray(0,start).toString()).toContain('image/jpeg')
  const dimensions=await page.evaluate(async data=>{const img=new Image();img.src='data:image/jpeg;base64,'+data;await img.decode();return [img.naturalWidth,img.naturalHeight]},multipart.subarray(start,end).toString('base64'))
  expect(dimensions).toEqual([1600,800])
  page.on('dialog',dialog=>void dialog.accept())
  await page.locator('input[type=file][accept=".docx,.pdf,.txt"]').setInputFiles({name:'导入.txt',mimeType:'text/plain',buffer:Buffer.from('导入的第一行\n第二行\n\n最后一行')})
  await expect(page.getByLabel('作品正文')).toContainText('最后一行');await expect(page.locator('.tiptap > p')).toHaveCount(4)
  await page.getByRole('button',{name:'保存为草稿'}).click();await expect(page.locator('.save-status')).toContainText('已保存');expect(state.saves.at(-1).body).toContain('<p><br></p>')
})

test('typing during an in-flight save is retained and followed by a newer snapshot',async({page})=>{
  const state=await fixture(page,'<p>旧内容</p>');state.saveDelay=900;await page.goto('/#/write/42')
  await page.getByRole('button',{name:'保存为草稿'}).click();await page.getByLabel('作品正文').click();await page.keyboard.press('Control+End');await page.keyboard.insertText('保存期间的新内容')
  await expect(page.locator('.save-status')).toContainText('已保存',{timeout:8000})
  expect(state.maxActiveSaves).toBe(1);expect(state.saves.at(-1).body).toContain('保存期间的新内容')
})

test('publishing an earlier snapshot never drops later edits or queues a draft downgrade',async({page})=>{
  const state=await fixture(page,'<p>发布的版本</p>');state.saveDelay=2400;await page.goto('/#/write/42')
  await page.getByRole('button',{name:'检查并发布'}).click();await page.getByLabel('作品正文').click();await page.keyboard.press('Control+End');await page.keyboard.insertText('稍后写下的文字')
  await expect(page.locator('.save-status')).toContainText('新修改尚未发布',{timeout:8000})
  await expect(page.getByLabel('作品正文')).toContainText('稍后写下的文字');expect(page.url()).toContain('/write/42')
  await page.waitForTimeout(500);expect(state.saves).toHaveLength(1);expect(state.saves[0].publish).toBe(true)
})

test('touch controls work on a phone, and no-heading poetry has no TOC',async({browser})=>{
  const context=await browser.newContext({viewport:{width:390,height:844},hasTouch:true,isMobile:true})
  const page=await context.newPage();await fixture(page,'<p>第一行<br>第二行<br>第三行</p>')
  await page.goto('http://127.0.0.1:5173/#/work/42');await expect(page.locator('.article-toc-mobile')).toHaveCount(0);await expect(page.locator('.article-content br')).toHaveCount(2)
  await page.goto('http://127.0.0.1:5173/#/write/42');await page.getByRole('button',{name:'插入',exact:true}).tap();await page.getByRole('menuitem',{name:'折叠内容',exact:true}).tap()
  await expect(page.locator('.tiptap details')).toHaveCount(1);await noOverflow(page)
  await page.getByRole('button',{name:'设置版式',exact:true}).tap();await expect(page.getByRole('dialog',{name:'版式',exact:true})).toBeVisible()
  await page.getByRole('dialog',{name:'版式',exact:true}).getByRole('button',{name:'取消',exact:true}).tap();await expect(page.getByRole('dialog',{name:'版式',exact:true})).not.toBeVisible()
  await context.close()
})

test('layout dialog stages presets and adjustments, supports cancellation, and persists applied choices',async({page})=>{
  const state=await fixture(page,'<h2>第一节</h2><p>风经过书页<br>也经过窗前</p><p>　 </p><p><br></p><p>第二节的诗行</p>')
  await page.setViewportSize({width:1440,height:1000});await page.goto('/#/write/42')
  await page.getByLabel('体裁',{exact:true}).selectOption('诗歌');await expect(page.locator('.layout-entry')).toContainText('诗歌 · 靠左')
  const prose=page.locator('.tiptap'),popup=page.getByRole('dialog',{name:'版式',exact:true})
  expect(await prose.evaluate(el=>parseFloat(getComputedStyle(el).lineHeight)/parseFloat(getComputedStyle(el).fontSize))).toBeCloseTo(2.7)
  expect(await page.locator('.tiptap p.article-empty-line').first().evaluate(el=>el.getBoundingClientRect().height)).toBeLessThan(22)
  await page.getByRole('button',{name:'设置版式',exact:true}).click();await popup.getByRole('button',{name:'诗歌 · 居中',exact:true}).click()
  await expect(popup.getByLabel('版式预览')).toHaveCSS('text-align','center');await expect(prose).toHaveCSS('text-align','left')
  await page.screenshot({path:'../output/article-upgrade/layout-dialog-desktop.png',fullPage:false})
  await popup.getByRole('button',{name:'取消',exact:true}).click();await expect(prose).toHaveCSS('text-align','left')
  await expect(page.getByRole('button',{name:'设置版式',exact:true})).toBeFocused()
  await page.getByRole('button',{name:'设置版式',exact:true}).click();await expect(popup.getByRole('button',{name:'诗歌 · 靠左',exact:true})).toHaveAttribute('aria-pressed','true')
  await popup.getByRole('button',{name:'诗歌 · 居中',exact:true}).click();await page.keyboard.press('Escape');await expect(prose).toHaveCSS('text-align','left')
  await page.getByRole('button',{name:'设置版式',exact:true}).click();await popup.getByRole('button',{name:'诗歌 · 居中',exact:true}).click();await popup.getByRole('button',{name:'应用版式',exact:true}).click()
  await expect(prose).toHaveCSS('text-align','center');await page.getByRole('button',{name:'保存为草稿'}).click();await expect(page.locator('.save-status')).toContainText('已保存');await page.reload()
  await expect(page.locator('.layout-entry')).toContainText('诗歌 · 居中');await expect(page.locator('.tiptap')).toHaveCSS('text-align','center')
  await page.getByRole('button',{name:'阅读预览',exact:true}).click();await expect(page.locator('.article-content')).toHaveCSS('text-align','center')
  await page.getByRole('button',{name:'继续编辑',exact:true}).click();await page.getByLabel('体裁',{exact:true}).selectOption('小说')
  await expect(page.locator('.tiptap h2')).toHaveCSS('text-align','center');await expect(page.locator('.tiptap > p').first()).toHaveCSS('text-indent','36px')
  await page.getByRole('button',{name:'设置版式',exact:true}).click();await popup.locator('.layout-adjustments summary').click();await popup.getByLabel('节标题对齐',{exact:true}).selectOption('left')
  await expect(popup.locator('.layout-adjustments summary')).toContainText('已自定义');await popup.getByRole('button',{name:'恢复默认',exact:true}).click();await popup.getByRole('button',{name:'应用版式',exact:true}).click()
  await expect(page.locator('.layout-entry')).toContainText('小说 · 节标题居中')
  await page.getByLabel('体裁',{exact:true}).selectOption('其他');await page.getByLabel('具体体裁',{exact:true}).fill('书信')
  await page.setViewportSize({width:390,height:844});await page.getByRole('button',{name:'设置版式',exact:true}).click();await popup.locator('.layout-adjustments summary').click()
  await popup.getByLabel('正文对齐',{exact:true}).selectOption('center');await popup.getByLabel('行距',{exact:true}).selectOption('normal');await popup.getByLabel('段距 / 节距',{exact:true}).selectOption('loose')
  const box=await popup.boundingBox();expect(box!.width).toBeLessThanOrEqual(390);expect(box!.height).toBeLessThanOrEqual(844)
  await page.screenshot({path:'../output/article-upgrade/layout-dialog-mobile.png',fullPage:false});await popup.getByRole('button',{name:'应用版式',exact:true}).click()
  await expect(page.locator('.layout-entry')).toContainText('自定义版式');await page.getByRole('button',{name:'保存为草稿'}).click();await expect(page.locator('.save-status')).toContainText('已保存');expect(state.customGenre).toBe('书信')
  await page.reload();await expect(page.getByLabel('具体体裁',{exact:true})).toHaveValue('书信');await expect(page.locator('.layout-entry')).toContainText('自定义版式')
  await page.goto('/#/work/42');await expect(page.locator('.genre-label')).toHaveText('书信');await expect(page.locator('.article-content')).toHaveCSS('text-align','center');await noOverflow(page)
})

test('draft deletion is available in both layouts, confirms, prevents repeats and handles failure',async({page})=>{
  const state=await fixture(page);await page.setViewportSize({width:1440,height:1000});await page.goto('/#/account')
  const draft=page.locator('.desk-work-item').filter({hasText:'草稿手记'}),published=page.locator('.desk-work-item').filter({hasText:'公开的文章'})
  await expect(draft.getByRole('button',{name:'删除草稿：草稿手记'})).toBeVisible();await expect(published.getByRole('button',{name:/删除/})).toHaveCount(0)
  await page.screenshot({path:'../output/article-upgrade/draft-delete-desktop.png',fullPage:true})
  page.once('dialog',dialog=>void dialog.dismiss());await draft.getByRole('button',{name:'删除草稿：草稿手记'}).click();expect(state.deleted).toHaveLength(0)
  await page.getByRole('button',{name:'草稿箱',exact:true}).click();await page.getByRole('button',{name:'横条',exact:true}).click()
  state.deleteStatus=503;page.once('dialog',dialog=>void dialog.accept());await draft.getByRole('button',{name:'删除草稿：草稿手记'}).click()
  await expect(page.getByRole('alert')).toContainText('删除失败');await expect(draft).toBeVisible()
  await page.setViewportSize({width:390,height:844});await noOverflow(page);await page.screenshot({path:'../output/article-upgrade/draft-delete-mobile.png',fullPage:true})
  state.deleteStatus=0;state.deleteDelay=600;page.once('dialog',dialog=>void dialog.accept());await draft.getByRole('button',{name:'删除草稿：草稿手记'}).click()
  await expect(draft.getByRole('button',{name:'删除草稿：草稿手记'})).toBeDisabled();await expect(draft).toHaveCount(0);expect(state.deleted).toEqual([42,42])
  await page.reload();await expect(page.locator('.desk-work-item').filter({hasText:'草稿手记'})).toHaveCount(0);await expect(page.locator('.desk-work-item').filter({hasText:'公开的文章'})).toBeVisible()
})
