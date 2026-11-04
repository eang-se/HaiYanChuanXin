import test from 'node:test'
import assert from 'node:assert/strict'
import {JSDOM} from 'jsdom'
import {splitShareSentences,shareBlocks,selectedShareBlocks,shareSelectionCount} from './shareSelection.ts'
const dom=new JSDOM('<!doctype html><html><body></body></html>')
for(const name of ['document','Element','Node'])Object.defineProperty(globalThis,name,{value:(dom.window as any)[name],configurable:true})
test('extracts nested text blocks without duplicates, retaining poem line breaks',()=>{
 const template=document.createElement('template');template.innerHTML='<h2>序</h2><blockquote><p>第一句。第二句！</p></blockquote><ul><li><p>列表<br>下一行</p></li></ul><figure><figcaption>图说</figcaption></figure>'
 const blocks=shareBlocks(template.content)
 assert.deepEqual(blocks.map(b=>b.text),['序','第一句。第二句！','列表\n下一行','图说'])
 assert.deepEqual(blocks[1]?.sentences,['第一句。','第二句！'])
 assert.deepEqual(splitShareSentences('诗句。\n下一行。'),['诗句。\n','下一行。'])
 assert.deepEqual(splitShareSentences('他说：“你好！”然后离开。'),['他说：“你好！”','然后离开。'])
})
test('selected sentences follow document order and count Unicode characters and distinct blocks',()=>{
 const blocks=[{text:'甲。乙。',sentences:['甲。','乙。']},{text:'🙂。',sentences:['🙂。']}]
 const selection={1:[0],0:[1,0]}
 assert.deepEqual(selectedShareBlocks(blocks,selection),['甲。乙。','🙂。'])
 assert.deepEqual(shareSelectionCount(blocks,selection),{blocks:2,characters:6})
})
