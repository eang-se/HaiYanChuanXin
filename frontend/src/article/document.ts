import type {JSONContent} from '@tiptap/core'

/** Upgrade stored disclosure nodes without dropping their title or body. */
export function expandArticleDocument(document:JSONContent):JSONContent {
  function expand(node:JSONContent):JSONContent[] {
    const content=node.content?.flatMap(expand)
    if(node.type==='disclosure'||node.type==='disclosureBody')return content||[]
    if(node.type==='disclosureTitle')return [{type:'paragraph',content:content||[]}]
    return [{...node,...(content?{content}:{})}]
  }
  return expand(document)[0]||{type:'doc',content:[{type:'paragraph'}]}
}
