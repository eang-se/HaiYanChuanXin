// Offline, read-only converter. Input and output are JSON Lines on stdin/stdout.
// No database credentials or article text are written to logs.
import {createInterface} from 'node:readline'
import {createHash} from 'node:crypto'
import {JSDOM} from 'jsdom'

const dom = new JSDOM('<!doctype html><html><body></body></html>',{url:'http://localhost'})
for (const name of ['window','document','navigator','HTMLElement','Element','Node','DocumentFragment','MutationObserver','getComputedStyle']) {
  Object.defineProperty(globalThis,name,{value:dom.window[name],configurable:true})
}
const {sanitizeArticle} = await import('../src/article/html.ts')
const {articleExtensions} = await import('../src/article/editorExtensions.ts')
const {Editor} = await import('@tiptap/core')

// Compare parsed trees, not serializer spelling. These are the only accepted
// representation changes: a standalone old image becomes the editor's image
// figure, default attributes are made explicit, and an empty root is a blank
// paragraph. Former disclosure wrappers expand into ordinary paragraphs.
// Text, whitespace, marks, order and all other attributes matter.
function fingerprint(html) {
  const root=document.createElement('div');root.innerHTML=html
  for(const summary of root.querySelectorAll('summary'))if(!summary.attributes.length){const p=document.createElement('p');p.append(...summary.childNodes);summary.replaceWith(p)}
  for(const wrapper of root.querySelectorAll('details,div[data-disclosure-body]')){
    if((wrapper.localName==='details'&&!wrapper.attributes.length)||(wrapper.localName==='div'&&wrapper.attributes.length===1&&wrapper.getAttribute('data-disclosure-body')===''))wrapper.replaceWith(...wrapper.childNodes)
  }
  function attributes(el) {
    const result=Object.fromEntries([...el.attributes].map(a=>[a.name,a.value]))
    if(el.localName==='img'&&!Object.hasOwn(result,'alt'))result.alt=''
    if(el.localName==='figure'&&!Object.hasOwn(result,'data-size'))result['data-size']='full'
    if(el.localName==='ol'&&result.start==='1')delete result.start
    return Object.fromEntries(Object.entries(result).sort(([a],[b])=>a.localeCompare(b)))
  }
  function convert(node,inFigure=false) {
    if(node.nodeType===3)return ['text',node.nodeValue]
    if(node.nodeType!==1)return ['other',node.nodeType,node.nodeValue]
    const name=node.localName,attrs=attributes(node)
    if(name==='img'&&!inFigure)return ['figure',{'data-size':'full'},[['img',attrs,[]],['figcaption',{},[]]]]
    return [name,attrs,[...node.childNodes].map(child=>convert(child,name==='figure'))]
  }
  const result=[...root.childNodes].map(node=>convert(node))
  return JSON.stringify(result.length?result:[['p',{},[]]])
}

function convert(input) {
  if(!input || typeof input!=='object' || !Object.hasOwn(input,'id') || typeof input.body!=='string')return {id:input?.id??null,status:'skipped',reason:'invalid_input'}
  const {id,body}=input
  if(body.length>200000)return {id,status:'skipped',reason:'body_too_large'}
  const cleaned=sanitizeArticle(body)
  if(fingerprint(body)!==fingerprint(cleaned))return {id,status:'skipped',reason:'sanitization_changes_content'}
  let editor, restored
  try {
    editor=new Editor({extensions:articleExtensions(),content:cleaned,parseOptions:{preserveWhitespace:'full'}})
    const html=editor.getHTML(),doc=editor.getJSON()
    if(fingerprint(cleaned)!==fingerprint(html))return {id,status:'skipped',reason:'editor_roundtrip_changes_content'}
    restored=new Editor({extensions:articleExtensions(),content:doc})
    if(fingerprint(html)!==fingerprint(restored.getHTML()))return {id,status:'skipped',reason:'document_roundtrip_changes_content'}
    return {id,status:'converted',sha256:createHash('sha256').update(body,'utf8').digest('hex'),document:doc}
  } catch {
    return {id,status:'skipped',reason:'editor_cannot_parse'}
  } finally {editor?.destroy();restored?.destroy()}
}
const lines=createInterface({input:process.stdin,crlfDelay:Infinity})
for await (const line of lines) {
  if(!line.trim())continue
  try {process.stdout.write(JSON.stringify(convert(JSON.parse(line)))+'\n')}
  catch {process.stdout.write(JSON.stringify({id:null,status:'skipped',reason:'invalid_json'})+'\n')}
}
