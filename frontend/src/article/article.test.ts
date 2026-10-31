import {test} from 'node:test'
import assert from 'node:assert/strict'
import {JSDOM} from 'jsdom'
import {createSaveQueue} from './saveQueue.ts'
import {defaultLayout,resolveLayout,matchingPreset} from './layout.ts'

const dom = new JSDOM('<!doctype html><html><body></body></html>',{url:'http://localhost'})
for (const name of ['window','document','navigator','HTMLElement','Element','Node','DocumentFragment','MutationObserver','getComputedStyle']) Object.defineProperty(globalThis,name,{value:(dom.window as any)[name],configurable:true})
const {sanitizeArticle,prepareArticle} = await import('./html.ts')
const {articleExtensions} = await import('./editorExtensions.ts')
const {Editor} = await import('@tiptap/core')
const src='/api/media/12345678-1234-1234-1234-123456789abc'
const sample=`<h2>河岸</h2><p>第一行<br>第二行</p><figure data-size="narrow"><img src="${src}" alt="河畔" width="1200" height="800"><figcaption>暮色中的<strong>河岸</strong></figcaption></figure><details><summary>后记</summary><div data-disclosure-body><p>折叠正文</p><h3>札记</h3><p>一行<br>又一行</p></div></details><p>尾声</p>`

test('untrusted HTML has a narrow vocabulary and cannot introduce executable attributes or remote images',()=>{
  const cleaned=sanitizeArticle(`<p style="position:fixed" onclick="alert(1)" id="app">诗</p><script>alert(1)</script><iframe src="https://evil.test"></iframe><svg onload="alert(1)"></svg><img src="data:image/png;base64,abc"><img src="https://evil.test/a.jpg"><img src="//evil.test/a.jpg"><figure data-size="giant"><img src="${src}" srcset="https://evil.test/a.jpg 2x" width="999999" height="2" onerror="alert(1)"><figcaption>说明</figcaption></figure>`)
  assert.doesNotMatch(cleaned,/script|iframe|svg|style=|onclick|onerror|srcset|base64|evil\.test|id=/)
  assert.match(cleaned,/data-size="full"/);assert.doesNotMatch(cleaned,/width=|height=/)
})
test('new and legacy article HTML survives two editor round trips with line breaks and captions',()=>{
  for (const html of [sample,`<p>诗行<br>下一行</p><blockquote><p>引文</p></blockquote><ol start="3"><li><p>第三项</p></li></ol><img src="${src}" alt="旧图"><p><u>下划线</u><s>划线</s><em>斜体</em></p>`]) {
    const editor=new Editor({extensions:articleExtensions(),content:sanitizeArticle(html)})
    const first=editor.getHTML(),text=editor.getText()
    editor.commands.setContent(sanitizeArticle(first))
    assert.equal(editor.getHTML(),first);assert.equal(editor.getText(),text);assert.match(first,/<br>/)
    if(html===sample){assert.match(first,/data-size="narrow"/);assert.match(first,/<figcaption>暮色中的<strong>河岸<\/strong><\/figcaption>/);assert.match(text,/折叠正文/);assert.match(first,/width="1200"/)}
    editor.destroy()
  }
})
test('nested disclosures flatten without losing title/body and stay stable',()=>{
  const first=sanitizeArticle('<details><summary>外层</summary><div data-disclosure-body><p>开头</p><details><summary>内层</summary><div data-disclosure-body><p>结尾</p></div></details></div></details>')
  assert.equal((first.match(/<details>/g)||[]).length,1);assert.match(first,/内层/);assert.match(first,/结尾/)
  assert.equal(sanitizeArticle(first),first)
  const editor=new Editor({extensions:articleExtensions(),content:first})
  assert.match(editor.getText(),/内层/);assert.match(editor.getText(),/结尾/);editor.destroy()
})
test('TOC IDs are unique, namespaced and stable when an unrelated heading is added',()=>{
  const result=prepareArticle('<h2 id="app">重复</h2><h3>重复</h3><h2>结尾</h2>','work-42')
  assert.equal(new Set(result.headings.map(h=>h.id)).size,3)
  assert.ok(result.headings.every(h=>h.id.startsWith('hy-work-42-section-')))
  assert.deepEqual(prepareArticle('<h2>新开头</h2><h2>重复</h2><h3>重复</h3><h2>结尾</h2>','work-42').headings.slice(1),result.headings)
  assert.equal(prepareArticle('<p>无标题</p>','work-42').headings.length,0)
  const image=prepareArticle(sample,'work-42').html;assert.match(image,/loading="lazy"/);assert.match(image,/width="1200"/)
})
test('queued saves serialize, capture fresh snapshots and continue after rejection',async()=>{
  const enqueue=createSaveQueue(),events:string[]=[]
  let release!:()=>void,body='旧文'
  const gate=new Promise<void>(resolve=>{release=resolve})
  const first=enqueue(async()=>{events.push(body);await gate;throw new Error('断网')})
  const rejected=assert.rejects(first,/断网/)
  await Promise.resolve();body='新文'
  const second=enqueue(async()=>{events.push(body)})
  assert.deepEqual(events,['旧文']);release();await rejected;await second
  assert.deepEqual(events,['旧文','新文'])
})

test('genre layouts have bounded defaults and whitespace-only stanzas are marked without removing content',()=>{
  const poetry=defaultLayout('诗歌');assert.equal(poetry.lineSpacing,'airy');assert.equal(poetry.paragraphSpacing,'compact')
  assert.equal(defaultLayout('小说').headingAlign,'center');assert.equal(defaultLayout('其他').indent,false)
  const centered={...poetry,bodyAlign:'center',headingAlign:'center'} as const
  assert.equal(matchingPreset(centered,'诗歌'),'poetry-center');assert.deepEqual(resolveLayout(centered,'诗歌'),centered)
  assert.equal(resolveLayout({bodyAlign:'url(evil)',lineSpacing:'999'},'其他').bodyAlign,'left')
  const html='<p>第一节</p><p>　 </p><p><br></p><p>第二节</p>'
  const rendered=prepareArticle(html,'poem').html;assert.equal((rendered.match(/article-empty-line/g)||[]).length,2)
  assert.match(rendered,/　 /);assert.doesNotMatch(sanitizeArticle(rendered),/class=/)
  const editor=new Editor({extensions:articleExtensions(),content:html})
  assert.doesNotMatch(editor.getHTML(),/class=/);assert.equal(editor.getJSON().content?.length,4);editor.destroy()
})
