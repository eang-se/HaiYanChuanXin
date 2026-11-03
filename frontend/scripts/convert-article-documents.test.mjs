import {test} from 'node:test'
import assert from 'node:assert/strict'
import {spawnSync} from 'node:child_process'
import {createHash} from 'node:crypto'
import {fileURLToPath} from 'node:url'

test('converts lossless documents and rejects changes and unsafe input',()=>{
  const image='/api/media/12345678-1234-1234-1234-123456789abc'
  const cases=[
    {id:1,body:'<p>一行<br>二行</p><p></p>'},
    {id:2,body:`<img src="${image}" alt="旧图">`},
    {id:3,body:`<figure data-size="narrow"><img src="${image}" alt="风" width="800" height="600"><figcaption>说明<strong>文字</strong></figcaption></figure><details><summary>折叠</summary><div data-disclosure-body=""><p>内容</p></div></details>`},
    {id:4,body:'<p><strong>粗体<em>粗斜体</em>粗体</strong></p><ol start="3"><li><p>第三项</p></li></ol>'},
    {id:5,body:'<p onclick="alert(1)">不能丢失的属性</p>'},
    {id:6,body:'<p>保留文字</p><script>notAllowed()</script>'},
    {id:7,body:'<p>  leading spaces are significant</p>'},
    {id:8,body:''},
    {id:'w:9',body:'<p>甲 &amp; 乙 &lt; 丙 &gt; 丁&nbsp;终<br>下一行</p>'},
    {id:'w:10',body:'<p>行一\n  行二<br> \n<br>行三</p>'},
  ]
  const script=fileURLToPath(new URL('./convert-article-documents.mjs',import.meta.url))
  const run=spawnSync(process.execPath,['--experimental-strip-types',script],{input:cases.map(value=>JSON.stringify(value)).join('\n')+'\n',encoding:'utf8'})
  assert.equal(run.status,0,run.stderr)
  const result=run.stdout.trim().split('\n').map(value=>JSON.parse(value))
  assert.equal(result.length,cases.length)
  for(const id of [1,2,3,4,7,8,'w:9','w:10'])assert.equal(result.find(item=>item.id===id)?.status,'converted',`case ${id}`)
  for(const id of [5,6])assert.equal(result.find(item=>item.id===id)?.status,'skipped',`case ${id}`)
  assert.equal(result[1].document.content[0].type,'image')
  assert.equal(result[1].document.content[0].attrs.src,image)
  assert.equal(result[2].document.content[1].type,'paragraph');assert.equal(result[2].document.content[2].type,'paragraph')
  assert.equal(result[8].sha256,createHash('sha256').update(cases[8].body,'utf8').digest('hex'))
  assert.equal(result[8].document.content[0].content[0].text,'甲 & 乙 < 丙 > 丁\u00a0终')
  assert.equal(result[8].document.content[0].content[1].type,'hardBreak')
  assert.equal(result[9].document.content[0].content[0].text,'行一\n  行二')
})
