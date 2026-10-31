/** One caller at a time; the backend still verifies format, pixels and size independently. */
export async function prepareImage(file: File): Promise<File> {
  if (!['image/jpeg','image/png'].includes(file.type)) throw new Error('请选择 PNG 或 JPEG 图片')
  if (file.size > 20 * 1024 * 1024) throw new Error('图片超过 20 MB，请先缩小后选择')
  const url = URL.createObjectURL(file)
  try {
    const image = new Image(); image.src = url; await image.decode()
    if (image.naturalWidth > 8000 || image.naturalHeight > 8000 || image.naturalWidth * image.naturalHeight > 20000000) throw new Error('图片超过 2,000 万像素或边长 8,000，请先缩小')
    if (Math.max(image.naturalWidth,image.naturalHeight) <= 1600 && file.size <= 5 * 1024 * 1024) return file
    const scale = Math.min(1,1600 / Math.max(image.naturalWidth,image.naturalHeight)), canvas = document.createElement('canvas')
    canvas.width = Math.max(1,Math.floor(image.naturalWidth * scale)); canvas.height = Math.max(1,Math.floor(image.naturalHeight * scale))
    const context = canvas.getContext('2d')
    if (!context) throw new Error('无法处理图片，请缩小后重试')
    context.fillStyle = '#fff'; context.fillRect(0,0,canvas.width,canvas.height); context.drawImage(image,0,0,canvas.width,canvas.height)
    const blob = await new Promise<Blob>((resolve,reject) => canvas.toBlob(b => b ? resolve(b) : reject(new Error('图片缩放失败，请重试')), 'image/jpeg', .86))
    canvas.width = canvas.height = 0
    if (blob.size > 5 * 1024 * 1024) throw new Error('压缩后仍超过 5 MB，请缩小后重试')
    return new File([blob], file.name.replace(/\.[^.]+$/,'') + '.jpg', {type:'image/jpeg'})
  } finally { URL.revokeObjectURL(url) }
}
