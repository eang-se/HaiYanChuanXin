import sharp from 'sharp'
import {fileURLToPath} from 'node:url'
import {stat} from 'node:fs/promises'

const brandRoot=new URL('../public/brand/',import.meta.url)
const targets=[
  {source:'tech-department.png',output:'tech-department.webp',size:256},
  {source:'haiyan-seal.jpg',output:'haiyan-seal.webp',size:192},
  {source:'activity-account.jpg',output:'activity-account.webp',size:256},
]

await Promise.all(targets.map(async target=>{
  const source=fileURLToPath(new URL(target.source,brandRoot))
  const output=fileURLToPath(new URL(target.output,brandRoot))
  await sharp(source).rotate().resize({width:target.size,height:target.size,fit:'inside',withoutEnlargement:true}).webp({quality:84,effort:4}).toFile(output)
  const [original,optimized]=await Promise.all([stat(source),stat(output)])
  console.log(`${target.output}: ${original.size} -> ${optimized.size} bytes`)
}))
