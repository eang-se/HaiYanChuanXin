export interface ShareBlock { text: string; sentences: string[] }

// Keep punctuation with the preceding sentence and preserve line breaks.
export function splitShareSentences(text:string):string[] {
  return text.match(/[^。！？!?；;\n]+[。！？!?；;]+[”’」』】）)]*\n*|[^。！？!?；;\n]+(?:\n+|$)|[。！？!?；;]+[”’」』】）)]*|\n+/gu)?.filter(part=>part.trim()) || []
}
function blockText(node:Node):string {
  if (node.nodeType===Node.TEXT_NODE) return node.textContent || ''
  if (node instanceof Element && node.tagName==='BR') return '\n'
  return Array.from(node.childNodes,blockText).join('')
}
export function shareBlocks(fragment:DocumentFragment):ShareBlock[] {
  const selector='p,h2,h3,pre,li,blockquote,figcaption'
  return Array.from(fragment.querySelectorAll(selector))
    .filter(element=>!element.querySelector(selector))
    .map(element=>blockText(element).trim())
    .filter(Boolean)
    .map(text=>({text,sentences:splitShareSentences(text)}))
    .filter(block=>block.sentences.length>0)
}
export type ShareSelection = Record<number,number[]>
export function selectedShareBlocks(blocks:ShareBlock[],selection:ShareSelection):string[] {
  return blocks.flatMap((block,index)=>{
    const selected=new Set(selection[index] || [])
    const text=block.sentences.filter((_,i)=>selected.has(i)).join('').trim()
    return text?[text]:[]
  })
}
export function shareSelectionCount(blocks:ShareBlock[],selection:ShareSelection) {
  const selected=selectedShareBlocks(blocks,selection)
  return {blocks:selected.length,characters:selected.reduce((sum,text)=>sum+Array.from(text).length,0)}
}
