import { createRouter, createWebHashHistory } from 'vue-router'
import { refreshAuth, user } from './api'
const router=createRouter({history:createWebHashHistory(),scrollBehavior:()=>({top:0}),routes:[
 {path:'/',component:()=>import('./pages/Explore.vue')},{path:'/explore',component:()=>import('./pages/Explore.vue')},
 {path:'/about',component:()=>import('./pages/About.vue')},
 {path:'/activities',component:()=>import('./pages/Activities.vue')},{path:'/work/:id',component:()=>import('./pages/WorkDetail.vue')},
 {path:'/collections',component:()=>import('./pages/Collections.vue')},
 {path:'/collection/:id',component:()=>import('./pages/CollectionDetail.vue')},
 {path:'/collection-edit/:id?',component:()=>import('./pages/CollectionEditor.vue'),meta:{auth:true}},
 {path:'/pen-name/:id',component:()=>import('./pages/PenName.vue')},
 {path:'/author/:id',component:()=>import('./pages/Author.vue')},{path:'/login',component:()=>import('./pages/Login.vue')},
 {path:'/write/:id?',component:()=>import('./pages/Editor.vue'),meta:{auth:true}},
 {path:'/account',component:()=>import('./pages/Account.vue'),meta:{auth:true}},
 {path:'/profile',component:()=>import('./pages/Profile.vue'),meta:{auth:true}},
 {path:'/admin/settings',component:()=>import('./pages/AdminSettings.vue'),meta:{auth:true,admin:true}},
 {path:'/admin',component:()=>import('./pages/Admin.vue'),meta:{auth:true,admin:true}},
 {path:'/:pathMatch(.*)*',redirect:'/'}]})
let ready=false
router.beforeEach(async to=>{if(!ready){try{await refreshAuth();ready=true}catch{}}if(to.meta.auth&&!user.value)return {path:'/login',query:{next:to.fullPath}};if(to.path==='/profile'&&user.value?.accountType==='PUBLIC')return '/account';if(to.meta.admin&&!['ADMIN','SUPER_ADMIN'].includes(user.value?.actorRole||user.value?.role||'')&&!(to.path==='/admin'&&user.value?.accountType==='PUBLIC'&&['haiyan_editor','haiyan_activity','haiyan_tech'].includes(user.value?.username||'')&&user.value?.actorRole!=='RESTRICTED'))return '/account'})
export default router
