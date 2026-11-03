import {computed} from 'vue'
import {user} from '../api'

export type AdminSection = 'overview'|'accounts'|'works'|'tags'|'import'|'settings'
export function personalRole(){return user.value?.accountType==='PUBLIC'?(user.value.actorRole||'MEMBER'):(user.value?.actorRole||user.value?.role||'')}
export function isPersonalAdmin(){return ['ADMIN','SUPER_ADMIN'].includes(personalRole())}
export function isPublicStaff(){return user.value?.accountType==='PUBLIC'&&personalRole()!=='RESTRICTED'}
export function canManage(section:AdminSection) {
  const admin=isPersonalAdmin(),staff=isPublicStaff()
  if(section==='accounts')return admin
  if(section==='settings')return admin||(staff&&user.value?.username==='haiyan_tech')
  return admin||staff
}
export function canAnnounce(){return isPersonalAdmin()||isPublicStaff()}
export function useAdminAccess(){return {
  isAdmin:computed(()=>canManage('accounts')),
  isSuper:computed(()=>personalRole()==='SUPER_ADMIN'),
  canReview:computed(()=>canManage('works')),
  canTag:computed(()=>canManage('tags')),
  canImport:computed(()=>canManage('import')),
  canConfigure:computed(()=>canManage('settings')),
}}
export const roleLabels:Record<string,string>={SUPER_ADMIN:'超级管理员',ADMIN:'管理员',MEMBER:'社员',RESTRICTED:'受限社员'}
export const workStatusLabels:Record<string,string>={PUBLISHED:'已公开',DRAFT:'草稿',WITHDRAWN:'已下架 / 撤回'}
export function adminDate(value?:string){return value?new Date(value).toLocaleString('zh-CN',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}):'—'}
