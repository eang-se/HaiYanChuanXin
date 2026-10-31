import {Node,Extension} from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import {Plugin} from '@tiptap/pm/state'
import {Decoration,DecorationSet} from '@tiptap/pm/view'
import type {Node as ProseMirrorNode} from '@tiptap/pm/model'
import {mediaPath} from './html.ts'

const imageAttributes = (element:HTMLElement) => {
  const image = element.tagName === 'IMG' ? element : element.querySelector('img')
  if (!image || !mediaPath.test(image.getAttribute('src') || '')) return false
  return {src:image.getAttribute('src'),alt:image.getAttribute('alt') || '',width:image.getAttribute('width'),height:image.getAttribute('height'),size:element.getAttribute('data-size') === 'narrow' ? 'narrow' : 'full'}
}
export const CaptionImage = Node.create({
  name:'image', group:'block', content:'inline*', marks:'bold italic underline strike code', isolating:true,
  addAttributes() { return {src:{default:null},alt:{default:''},width:{default:null},height:{default:null},size:{default:'full'}} },
  parseHTML() { return [{tag:'figure',getAttrs:imageAttributes,contentElement:'figcaption'},{tag:'img[src]',getAttrs:imageAttributes}] },
  renderHTML({node}) {
    const {src,alt,width,height,size} = node.attrs
    return ['figure',{'data-size':size},['img',{src,alt,...(width&&height?{width,height}:{})}],['figcaption',{},0]]
  },
  addNodeView() {
    return ({node,editor,getPos}) => {
      const dom = document.createElement('figure'), img = document.createElement('img'), caption = document.createElement('figcaption')
      img.contentEditable = 'false'; img.draggable = false; caption.setAttribute('data-placeholder','添加图片说明（选填）'); caption.setAttribute('aria-label','图片说明')
      dom.append(img,caption)
      const sync = (attrs:typeof node.attrs) => {
        dom.dataset.size = attrs.size
        for (const name of ['src','alt','width','height']) { if (attrs[name] != null) img.setAttribute(name,String(attrs[name])); else img.removeAttribute(name) }
      }
      sync(node.attrs)
      img.addEventListener('click',() => { const pos = getPos(); if (typeof pos === 'number') editor.chain().focus().setNodeSelection(pos).run() })
      return {dom,contentDOM:caption,update(updated) { if(updated.type!==node.type)return false; sync(updated.attrs); return true }}
    }
  },
})
export const DisclosureTitle = Node.create({
  name:'disclosureTitle',content:'inline*',marks:'bold italic underline strike code',defining:true,
  parseHTML:() => [{tag:'summary'}],renderHTML:() => ['summary',{},0],
  addKeyboardShortcuts() { return {Enter:() => {
    const {$from} = this.editor.state.selection
    if ($from.parent.type.name !== this.name) return false
    return this.editor.commands.setTextSelection($from.after() + 2)
  }} },
})
export const DisclosureBody = Node.create({
  name:'disclosureBody',content:'block+',defining:true,
  parseHTML:() => [{tag:'div[data-disclosure-body]'}],renderHTML:() => ['div',{'data-disclosure-body':''},0],
})
export const Disclosure = Node.create({
  name:'disclosure',group:'block',content:'disclosureTitle disclosureBody',defining:true,isolating:true,
  parseHTML:() => [{tag:'details'}],renderHTML:() => ['details',{},0],
  addNodeView() { return () => {
    const dom = document.createElement('details'); dom.open = true
    // In editing mode both title and body remain reachable, including on touch screens.
    dom.addEventListener('click',event => { if ((event.target as HTMLElement).closest('summary')) event.preventDefault() })
    return {dom,contentDOM:dom,ignoreMutation:mutation => mutation.type==='attributes'&&mutation.attributeName==='open'}
  } },
})
const SingleLevelDisclosure = Extension.create({
  name:'singleLevelDisclosure',
  addProseMirrorPlugins() { return [new Plugin({filterTransaction(transaction) {
    if (!transaction.docChanged) return true
    let valid = true
    transaction.doc.descendants(node => {
      if (node.type.name !== 'disclosure') return true
      node.descendants(child => { if(child.type.name==='disclosure')valid=false })
      return false
    })
    return valid
  }})] },
})
function emptyLineDecorations(doc:ProseMirrorNode) {
  const decorations:Decoration[]=[]
  doc.descendants((node,pos)=>{
    if(node.type.name==='paragraph'&&!node.textContent.trim())decorations.push(Decoration.node(pos,pos+node.nodeSize,{class:'article-empty-line'}))
  })
  return DecorationSet.create(doc,decorations)
}
const EmptyLines=Extension.create({
  name:'articleEmptyLines',
  addProseMirrorPlugins(){return [new Plugin({
    state:{init:(_,state)=>emptyLineDecorations(state.doc),apply:(transaction,previous)=>transaction.docChanged?emptyLineDecorations(transaction.doc):previous},
    props:{decorations(state){return this.getState(state)}},
  })]},
})
export function articleExtensions() {
  return [StarterKit.configure({heading:{levels:[2,3]},link:false}),CaptionImage,Disclosure,DisclosureTitle,DisclosureBody,SingleLevelDisclosure,EmptyLines]
}
