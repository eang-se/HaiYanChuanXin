export interface ArticleLayout {
  bodyAlign:'left'|'center'
  lineSpacing:'normal'|'relaxed'|'airy'
  paragraphSpacing:'compact'|'normal'|'loose'
  headingAlign:'left'|'center'
  indent:boolean
}
export const layoutPresets = [
  {id:'default',label:'默认版式',genre:'其他',layout:{bodyAlign:'left',lineSpacing:'relaxed',paragraphSpacing:'normal',headingAlign:'left',indent:false}},
  {id:'poetry-left',label:'诗歌 · 靠左',genre:'诗歌',layout:{bodyAlign:'left',lineSpacing:'airy',paragraphSpacing:'compact',headingAlign:'left',indent:false}},
  {id:'poetry-center',label:'诗歌 · 居中',genre:'诗歌',layout:{bodyAlign:'center',lineSpacing:'airy',paragraphSpacing:'compact',headingAlign:'center',indent:false}},
  {id:'novel',label:'小说 · 节标题居中',genre:'小说',layout:{bodyAlign:'left',lineSpacing:'relaxed',paragraphSpacing:'normal',headingAlign:'center',indent:true}},
  {id:'prose',label:'散文 · 舒展',genre:'散文',layout:{bodyAlign:'left',lineSpacing:'relaxed',paragraphSpacing:'loose',headingAlign:'left',indent:false}},
  {id:'essay',label:'随笔 · 紧凑',genre:'随笔',layout:{bodyAlign:'left',lineSpacing:'normal',paragraphSpacing:'normal',headingAlign:'left',indent:false}},
] satisfies {id:string;label:string;genre:string;layout:ArticleLayout}[]

export function defaultLayout(genre:string):ArticleLayout {
  return {...(layoutPresets.find(p=>p.genre===genre)||layoutPresets[0]!).layout}
}
export function resolveLayout(value:unknown,genre='其他'):ArticleLayout {
  const fallback=defaultLayout(genre), layout=(value&&typeof value==='object'?value:{}) as Partial<ArticleLayout>
  return {
    bodyAlign:layout.bodyAlign==='center'?'center':layout.bodyAlign==='left'?'left':fallback.bodyAlign,
    lineSpacing:['normal','relaxed','airy'].includes(layout.lineSpacing||'')?layout.lineSpacing!:fallback.lineSpacing,
    paragraphSpacing:['compact','normal','loose'].includes(layout.paragraphSpacing||'')?layout.paragraphSpacing!:fallback.paragraphSpacing,
    headingAlign:layout.headingAlign==='center'?'center':layout.headingAlign==='left'?'left':fallback.headingAlign,
    indent:typeof layout.indent==='boolean'?layout.indent:fallback.indent,
  }
}
export function layoutClasses(layout:ArticleLayout) {
  return ['article-layout',`layout-body-${layout.bodyAlign}`,`layout-lines-${layout.lineSpacing}`,`layout-gap-${layout.paragraphSpacing}`,`layout-heading-${layout.headingAlign}`,{'layout-indent':layout.indent&&layout.bodyAlign==='left'}]
}
export function matchingPreset(layout:ArticleLayout,genre:string) {
  const matches=(candidate:ArticleLayout)=>Object.entries(layout).every(([key,value])=>candidate[key as keyof ArticleLayout]===value)
  return layoutPresets.find(p=>p.genre===genre&&matches(p.layout))?.id||layoutPresets.find(p=>matches(p.layout))?.id||'custom'
}
