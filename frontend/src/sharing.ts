import QRCode from 'qrcode'
export interface ShareContent {kind:'article'|'collection';title:string;credit:string;publisher?:string;description:string;detail:string;path:string}
export function canonicalShareUrl(path:string,base:string){const url=new URL(base);if(!['http:','https:'].includes(url.protocol))throw new Error('请填写 http 或 https 分享地址');url.search='';url.hash=path;return url.href}
export function validShareUrl(value:string){const url=new URL(value);if(!['http:','https:'].includes(url.protocol)||value.length>2000)throw new Error('请填写有效的分享链接（最多 2000 字符）');return url}
export function textLines(context:CanvasRenderingContext2D,text:string,maxWidth:number,maxLines:number){const lines:string[]=[];for(const paragraph of text.split(/\r?\n/)){let line='';for(const char of Array.from(paragraph)){if(line&&context.measureText(line+char).width>maxWidth){lines.push(line);line=char}else line+=char}if(line)lines.push(line)}if(lines.length>maxLines){const last=lines[maxLines-1]||'';let shortened=last;while(shortened&&context.measureText(shortened+'…').width>maxWidth)shortened=Array.from(shortened).slice(0,-1).join('');return [...lines.slice(0,maxLines-1),shortened+'…']}return lines}
function drawLines(ctx:CanvasRenderingContext2D,text:string,x:number,y:number,width:number,max:number,lineHeight:number){const lines=textLines(ctx,text,width,max);for(const [i,line] of lines.entries())ctx.fillText(line,x,y+i*lineHeight);return lines.length*lineHeight}
export async function renderShareCard(content:ShareContent,url:string,brandName:string){
 validShareUrl(url);await document.fonts.ready
 const canvas=document.createElement('canvas');canvas.width=1080;canvas.height=1440
 const ctx=canvas.getContext('2d');if(!ctx)throw new Error('浏览器暂不支持生成分享图片')
 ctx.fillStyle='#f5f4ec';ctx.fillRect(0,0,1080,1440);ctx.strokeStyle='#cdd7c9';ctx.lineWidth=2;ctx.strokeRect(42,42,996,1356)
 ctx.fillStyle='#215447';ctx.fillRect(82,96,8,86);ctx.font='36px "Microsoft YaHei",sans-serif';ctx.fillText(brandName,116,132);ctx.font='16px Arial,sans-serif';ctx.fillStyle='#788b74';ctx.fillText('HAIYAN · LITERARY SOCIETY',116,170)
 try{const img=new Image();img.src='/brand/haiyan-bird.svg';await img.decode();ctx.drawImage(img,878,93,106,106)}catch{/* Text branding remains available without the logo. */}
 ctx.fillStyle='#788b74';ctx.font='25px "Microsoft YaHei",sans-serif';ctx.fillText(content.kind==='collection'?'一起翻阅 / 文学合集':'读到这一页 / 作品分享',88,250)
 ctx.fillStyle='#254a3e';ctx.font='60px "SimSun",serif';let y=320;y+=drawLines(ctx,content.title,84,y,900,4,88)+38
 ctx.fillStyle='#5d755d';ctx.font='31px "Microsoft YaHei",sans-serif';y+=drawLines(ctx,content.credit,88,y,890,2,46)+16
 if(content.publisher&&content.publisher!==content.credit){ctx.font='25px "Microsoft YaHei",sans-serif';y+=drawLines(ctx,'发布：'+content.publisher,88,y,890,1,38)+8}
 ctx.font='24px "Microsoft YaHei",sans-serif';ctx.fillStyle='#899680';ctx.fillText(content.detail,88,y);y+=70
 ctx.fillStyle='#56664f';ctx.font='32px "SimSun",serif';const available=Math.max(1,Math.min(6,Math.floor((1060-y)/54)));drawLines(ctx,(content.description||'让每一段文字，都有自己的书页。').trim(),88,y,890,available,54)
 ctx.strokeStyle='#cdd7c9';ctx.beginPath();ctx.moveTo(88,1110);ctx.lineTo(990,1110);ctx.stroke()
 const qr=document.createElement('canvas');await QRCode.toCanvas(qr,url,{width:232,margin:2,errorCorrectionLevel:'M',color:{dark:'#215447',light:'#f5f4ec'}});ctx.drawImage(qr,764,1150,232,232)
 ctx.fillStyle='#254a3e';ctx.font='34px "SimSun",serif';ctx.fillText('风有来处，字有回声。',88,1203);ctx.font='23px "Microsoft YaHei",sans-serif';ctx.fillStyle='#788b74';ctx.fillText('扫码阅读 · 分享一页文字',88,1253)
 ctx.font='20px Arial,sans-serif';drawLines(ctx,new URL(url).host,88,1310,620,2,30)
 return canvas.toDataURL('image/png')
}
