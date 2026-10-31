import type {ArticleLayout} from './article/layout'
import axios from 'axios'
import { ref } from 'vue'
export interface Member { id:number; username:string; penName:string; bio:string; role:string; actorRole?:string; actorId?:number; accountType?:string; avatarUrl:string; signature:string; qqNumber:string; profileCompleted:boolean; profilePromptSeen:boolean; permissions:Record<string,boolean> }
export interface PenName {id:number;name:string;workCount:number}
export interface CollectionSummary {id:number;title:string;kind:string;published:boolean}
export interface Tag { id:number; name:string; description:string; isActivity:boolean; workCount:number; kind:'TOPIC'|'COLLECTION'; collectionId?:number }
export interface Work { id:number; title:string; genre:string; customGenre?:string; layout?:ArticleLayout; excerpt:string; body:string; cover:string; status:string; penNameId:number|null; penNameWorkCount:number; collectionId:number|null; collectionPosition:number|null; collection:CollectionSummary|null; byline:string; displayByline:string; sourceNote:string; notes:string; authorId:number; authorName:string; authorAvatar:string; authorAccountType?:string; authorVerified?:boolean; tags:Tag[]; wordCount:number; likes:number; commentCount:number; publishedAt:string; updatedAt:string; isDemo:boolean; liked:boolean; bookmarked:boolean }
export const user=ref<Member|null>(null),notification=ref('')
export const siteName=ref('海燕传信'),identityPrompt=ref(false)
let noticeTimer:ReturnType<typeof setTimeout>
export function notify(message:string){notification.value=message;clearTimeout(noticeTimer);noticeTimer=setTimeout(()=>notification.value='',4000)}
export const api=axios.create({baseURL:'/api',timeout:30000})
let csrf=''
api.interceptors.request.use(async config=>{if(!['get','head','options'].includes(config.method||'get')){if(!csrf)csrf=(await api.get('/auth/csrf')).data.token;config.headers['X-CSRF-TOKEN']=csrf}return config})
export async function refreshAuth(){csrf='';user.value=(await api.get('/auth/me')).data;if(!user.value?.id)user.value=null}
export function message(error:unknown){return axios.isAxiosError(error)?error.response?.data?.message || (error.response?.status===403?'操作未获允许，请刷新后重试':'连接失败，请稍后再试'):error instanceof Error?error.message:'操作失败'}
export function date(value:string){return value?new Date(value).toLocaleDateString('zh-CN',{month:'2-digit',day:'2-digit'}):''}
export const genres=['全部','诗歌','散文','小说','随笔','其他']
