import type {InjectionKey} from 'vue'
export interface NewDraftSetup {title:string;genre:string;file:File|null}
export const openDraftKey:InjectionKey<(tag?:number)=>void>=Symbol('open-draft')
let pending:NewDraftSetup|null=null
export function setNewDraftSetup(value:NewDraftSetup){pending=value}
export function takeNewDraftSetup(){const value=pending;pending=null;return value}
