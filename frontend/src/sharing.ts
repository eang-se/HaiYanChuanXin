import QRCode from 'qrcode'
export interface ShareContent {kind:'article'|'collection';title:string;credit:string;publisher?:string;description:string;detail:string;path:string;body?:string;selectedBlocks?:string[]}
export function canonicalShareUrl(path:string,base:string){const url=new URL(base);if(!['http:','https:'].includes(url.protocol))throw new Error('请填写 http 或 https 分享地址');url.search='';url.hash=path;return url.href}
export function validShareUrl(value:string){const url=new URL(value);if(!['http:','https:'].includes(url.protocol)||value.length>2000)throw new Error('请填写有效的分享链接（最多 2000 字符）');return url}
export function textLines(context:CanvasRenderingContext2D,text:string,maxWidth:number,maxLines:number){const lines:string[]=[];for(const paragraph of text.split(/\r?\n/)){let line='';for(const char of Array.from(paragraph)){if(line&&context.measureText(line+char).width>maxWidth){lines.push(line);line=char}else line+=char}if(line)lines.push(line)}if(lines.length>maxLines){const last=lines[maxLines-1]||'';let shortened=last;while(shortened&&context.measureText(shortened+'…').width>maxWidth)shortened=Array.from(shortened).slice(0,-1).join('');return [...lines.slice(0,maxLines-1),shortened+'…']}return lines}
function drawLines(ctx:CanvasRenderingContext2D,lines:string[],x:number,y:number,lineHeight:number){for(const [i,line] of lines.entries())ctx.fillText(line,x,y+i*lineHeight);return lines.length*lineHeight}
export async function renderShareCard(content:ShareContent,url:string,brandName:string){
 validShareUrl(url);await document.fonts.ready
 const canvas=document.createElement('canvas');const ctx=canvas.getContext('2d');if(!ctx)throw new Error('浏览器暂不支持生成分享图片')
 canvas.width=1080
 ctx.font='60px "SimSun",serif';const title=textLines(ctx,content.title,900,4)
 ctx.font='31px "Microsoft YaHei",sans-serif';const credit=textLines(ctx,content.credit,890,2)
 const excerpts=content.kind==='article'?(content.selectedBlocks||[]):(content.description?[content.description]:[])
 ctx.font='32px "SimSun",serif';const lines=excerpts.map(text=>textLines(ctx,text,890,Number.MAX_SAFE_INTEGER))
 const textHeight=lines.reduce((sum,block)=>sum+block.length*54+26,0)
 const bodyTop=310+title.length*88+credit.length*46+54
 canvas.height=Math.max(1100,bodyTop+textHeight+350)
 const bottom=canvas.height
 ctx.fillStyle='#f5f4ec';ctx.fillRect(0,0,1080,bottom);ctx.strokeStyle='#cdd7c9';ctx.lineWidth=2;ctx.strokeRect(42,42,996,bottom-84)
 ctx.fillStyle='#215447';ctx.fillRect(82,96,8,64);ctx.font='34px "Microsoft YaHei",sans-serif';ctx.fillText(brandName,116,140)
 try{const img=new Image();img.src='/brand/haiyan-bird.svg';await img.decode();ctx.drawImage(img,878,80,106,106)}catch{/* Text branding remains available without the logo. */}
 ctx.fillStyle='#254a3e';ctx.font='60px "SimSun",serif';let y=280;y+=drawLines(ctx,title,84,y,88)+32
 ctx.fillStyle='#5d755d';ctx.font='31px "Microsoft YaHei",sans-serif';y+=drawLines(ctx,credit,88,y,46)+54
 ctx.fillStyle='#56664f';ctx.font='32px "SimSun",serif';for(const block of lines)y+=drawLines(ctx,block,88,y,54)+26
 ctx.strokeStyle='#cdd7c9';ctx.beginPath();ctx.moveTo(88,bottom-300);ctx.lineTo(990,bottom-300);ctx.stroke()
 const qr=document.createElement('canvas');await QRCode.toCanvas(qr,url,{width:200,margin:2,errorCorrectionLevel:'M',color:{dark:'#215447',light:'#f5f4ec'}});ctx.drawImage(qr,796,bottom-260,200,200)
 ctx.fillStyle='#788b74';ctx.font='24px "Microsoft YaHei",sans-serif';ctx.fillText('扫码阅读',88,bottom-190);ctx.font='20px Arial,sans-serif';drawLines(ctx,textLines(ctx,new URL(url).host,620,2),88,bottom-145,30)
 return canvas.toDataURL('image/png')
}
