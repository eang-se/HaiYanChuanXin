import DOMPurify from 'dompurify'

export const mediaPath = /^\/api\/media\/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/
const tags = ['p','br','strong','em','u','s','h2','h3','blockquote','ul','ol','li','hr','img','figure','figcaption','details','summary','div','pre','code']
const attributes: Record<string, string[]> = {img:['src','alt','width','height'],figure:['data-size'],div:['data-disclosure-body'],ol:['start']}
const inlineTags = new Set(['BR','STRONG','EM','U','S','CODE'])
function inlineOnly(element: Element) {
  for (const child of element.querySelectorAll('*')) if (!inlineTags.has(child.tagName)) child.replaceWith(...child.childNodes)
}

/** Sanitize preview, stored HTML and clipboard independently of the server boundary. */
export function articleFragment(html: string): DocumentFragment {
  const fragment = DOMPurify.sanitize(html, {
    ALLOWED_TAGS: tags, ALLOWED_ATTR: Object.values(attributes).flat(),
    ALLOW_DATA_ATTR: false, ALLOW_ARIA_ATTR: false, RETURN_DOM_FRAGMENT: true,
  })
  for (const element of fragment.querySelectorAll('*')) {
    for (const attr of [...element.attributes]) if (!(attributes[element.localName] || []).includes(attr.name)) element.removeAttribute(attr.name)
  }
  for (const image of fragment.querySelectorAll('img')) {
    if (!mediaPath.test(image.getAttribute('src') || '')) { image.remove(); continue }
    for (const attr of ['width','height']) {
      const value = image.getAttribute(attr) || ''
      if (!/^[1-9][0-9]{0,3}$/.test(value) || Number(value) > 8000) image.removeAttribute(attr)
    }
    if (!image.hasAttribute('width') || !image.hasAttribute('height')) { image.removeAttribute('width'); image.removeAttribute('height') }
  }
  for (const nested of fragment.querySelectorAll('details details')) {
    for (const title of nested.querySelectorAll(':scope > summary')) {
      const p = document.createElement('p'); p.append(...title.childNodes); title.replaceWith(p)
    }
    nested.replaceWith(...nested.childNodes)
  }
  for (const details of fragment.querySelectorAll('details')) {
    let title = details.querySelector(':scope > summary')
    if (!title) { title = document.createElement('summary'); title.textContent = '折叠内容' }
    title.remove(); inlineOnly(title)
    const body = document.createElement('div'); body.setAttribute('data-disclosure-body','')
    body.append(...details.childNodes); details.replaceChildren(title, body)
  }
  for (const div of fragment.querySelectorAll('div')) {
    if (div.parentElement?.tagName === 'DETAILS') div.setAttribute('data-disclosure-body','')
    else div.replaceWith(...div.childNodes)
  }
  for (const summary of fragment.querySelectorAll('summary')) {
    if (summary.parentElement?.tagName !== 'DETAILS') { const p = document.createElement('p'); p.append(...summary.childNodes); summary.replaceWith(p) }
  }
  for (const figure of fragment.querySelectorAll('figure')) {
    if (figure.querySelectorAll('img').length !== 1) { figure.replaceWith(...figure.childNodes); continue }
    figure.setAttribute('data-size', figure.getAttribute('data-size') === 'narrow' ? 'narrow' : 'full')
    const image = figure.querySelector('img')!; image.remove()
    const caption = document.createElement('figcaption'); caption.append(...figure.childNodes); inlineOnly(caption)
    figure.replaceChildren(image,caption)
  }
  for (const caption of fragment.querySelectorAll('figcaption')) {
    if (caption.parentElement?.tagName !== 'FIGURE') { const p = document.createElement('p'); p.append(...caption.childNodes); caption.replaceWith(p) }
    else inlineOnly(caption)
  }
  for (const ol of fragment.querySelectorAll('ol[start]')) if (!/^[1-9][0-9]{0,5}$/.test(ol.getAttribute('start') || '')) ol.removeAttribute('start')
  return fragment
}
function serialize(fragment: DocumentFragment) {
  const container = document.createElement('div'); container.append(fragment); return container.innerHTML
}
export function sanitizeArticle(html: string) { return serialize(articleFragment(html)) }
export interface ArticleHeading { id: string; title: string; level: number }
function hash(value: string) {
  let n = 2166136261
  for (let i = 0; i < value.length; i++) n = Math.imul(n ^ value.charCodeAt(i), 16777619)
  return (n >>> 0).toString(36)
}
export function prepareArticle(html: string, namespace: string) {
  const fragment = articleFragment(html), headings: ArticleHeading[] = [], seen = new Map<string,number>()
  const prefix = `hy-${namespace.replace(/[^a-zA-Z0-9_-]/g,'')}-section-`
  for (const heading of fragment.querySelectorAll('h2,h3')) {
    const title = heading.textContent?.trim() || '未命名章节', key = hash(title), occurrence = (seen.get(key) || 0) + 1
    seen.set(key, occurrence); heading.id = `${prefix}${key}-${occurrence}`
    headings.push({id: heading.id, title, level: Number(heading.tagName.slice(1))})
    heading.setAttribute('tabindex','-1')
  }
  for (const image of fragment.querySelectorAll('img')) {
    image.setAttribute('loading','lazy'); image.setAttribute('decoding','async'); image.tabIndex = 0
    image.setAttribute('role','button'); image.setAttribute('aria-label',`查看大图${image.alt ? '：' + image.alt : ''}`)
  }
  for (const paragraph of fragment.querySelectorAll('p')) {
    if (!paragraph.textContent?.trim()&&!paragraph.querySelector('img')) paragraph.classList.add('article-empty-line')
  }
  return {html: serialize(fragment), headings}
}
