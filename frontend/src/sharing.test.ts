import test from 'node:test'
import assert from 'node:assert/strict'
import {canonicalShareUrl,validShareUrl,textLines} from './sharing.ts'
test('share links retain the resource route and drop unrelated base queries',()=>{
 const link=canonicalShareUrl('/work/22?collection=9','https://example.org/?session=private#/old')
 assert.equal(link,'https://example.org/#/work/22?collection=9')
 assert.equal(canonicalShareUrl('/collection/9','https://example.org/library/'),'https://example.org/library/#/collection/9')
})
test('non-web and overlong QR destinations are rejected',()=>{
 assert.throws(()=>canonicalShareUrl('/work/22','javascript:alert(1)'))
 assert.throws(()=>validShareUrl('data:text/html,hello'))
 assert.throws(()=>validShareUrl('https://example.org/'+ 'a'.repeat(2000)))
 assert.equal(validShareUrl('https://example.org/#/collection/9').hash,'#/collection/9')
})
const context={measureText:(s:string)=>({width:Array.from(s).length})} as unknown as CanvasRenderingContext2D
test('Chinese and emoji wrap without breaking code points',()=>{
 assert.deepEqual(textLines(context,'海燕🙂传信',3,4),['海燕🙂','传信'])
 assert.deepEqual(textLines(context,'第一行\n第二行',5,4),['第一行','第二行'])
})
test('long titles fit the allotted lines and end with an ellipsis',()=>{
 const lines=textLines(context,'海燕文学社盐与糖年刊'.repeat(8),8,3)
 assert.equal(lines.length,3);assert.ok(lines.every(s=>Array.from(s).length<=8));assert.ok(lines[2]?.endsWith('…'))
})
