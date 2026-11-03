export const ARTICLE_IMAGE_LIMIT=5*1024*1024
/** Re-encode every upload and adapt quality/dimensions to the remaining article budget. */
export async function prepareImage(file: File,remaining=ARTICLE_IMAGE_LIMIT): Promise<File> {
  if (!['image/jpeg','image/png'].includes(file.type)) throw new Error('请选择 PNG 或 JPEG 图片')
  if (file.size > 20 * 1024 * 1024) throw new Error('图片超过 20 MB，请先缩小后选择')
  const url = URL.createObjectURL(file)
  try {
    const image = new Image(); image.src = url; await image.decode()
    if (image.naturalWidth > 8000 || image.naturalHeight > 8000 || image.naturalWidth * image.naturalHeight > 20000000) throw new Error('图片超过 2,000 万像素或边长 8,000，请先缩小')
    if(remaining<=0)throw new Error('本篇图片总大小已达到 5 MB，请先删除部分图片')
    const scale = Math.min(1,1600 / Math.max(image.naturalWidth,image.naturalHeight)), canvas = document.createElement('canvas')
    canvas.width = Math.max(1,Math.floor(image.naturalWidth * scale)); canvas.height = Math.max(1,Math.floor(image.naturalHeight * scale))
    const context = canvas.getContext('2d')
    if (!context) throw new Error('无法处理图片，请缩小后重试')
    context.fillStyle = '#fff'; context.fillRect(0,0,canvas.width,canvas.height); context.drawImage(image,0,0,canvas.width,canvas.height)
    try {
      const target=Math.min(768*1024,remaining)
      for(let attempt=0;attempt<10;attempt++){
        const quality=Math.max(.5,.84-attempt*.07)
        const blob=await new Promise<Blob>((resolve,reject)=>canvas.toBlob(b=>b?resolve(b):reject(new Error('图片压缩失败，请重试')),'image/jpeg',quality))
        if(blob.size<=target)return new File([blob],file.name.replace(/\.[^.]+$/,'')+'.jpg',{type:'image/jpeg'})
        if(attempt>=3){const copy=document.createElement('canvas');copy.width=canvas.width;copy.height=canvas.height;copy.getContext('2d')!.drawImage(canvas,0,0);canvas.width=Math.max(1,Math.floor(canvas.width*.75));canvas.height=Math.max(1,Math.floor(canvas.height*.75));context.drawImage(copy,0,0,canvas.width,canvas.height);copy.width=copy.height=0}
      }
      throw new Error('压缩后仍超出本篇剩余图片空间，请先删除部分图片')
    } finally {canvas.width=canvas.height=0}
  } finally { URL.revokeObjectURL(url) }
}
