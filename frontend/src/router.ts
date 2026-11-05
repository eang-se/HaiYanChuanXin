import { createRouter, createWebHashHistory } from 'vue-router'
import { refreshAuth, user } from './api'
import {canManage,type AdminSection} from './composables/admin'
const router=createRouter({history:createWebHashHistory(),scrollBehavior:()=>({top:0}),routes:[
 {path:'/',component:()=>import('./pages/Home.vue')},{path:'/explore',component:()=>import('./pages/Explore.vue')},
 {path:'/search',component:()=>import('./pages/Search.vue')},
 {path:'/about',component:()=>import('./pages/About.vue')},
 {path:'/activities',component:()=>import('./pages/Activities.vue')},{path:'/work/:id',component:()=>import('./pages/WorkDetail.vue')},
 {path:'/collections',component:()=>import('./pages/Collections.vue')},
 {path:'/collection/:id',component:()=>import('./pages/CollectionDetail.vue')},
 {path:'/collection-edit/:id?',redirect:to=>({path:'/account',query:{manage:'collections',collectionId:to.params.id||undefined}})},
 {path:'/pen-name/:id',redirect:'/explore'},
 {path:'/author/:id',component:()=>import('./pages/Author.vue')},{path:'/login',component:()=>import('./pages/Login.vue')},
 {path:'/write/:id?',component:()=>import('./pages/Editor.vue'),meta:{auth:true}},
 {path:'/account',component:()=>import('./pages/Account.vue'),meta:{auth:true}},
 {path:'/profile',component:()=>import('./pages/Profile.vue'),meta:{auth:true}},
 {path:'/admin',component:()=>import('./pages/AdminLayout.vue'),meta:{auth:true},children:[
  {path:'',component:()=>import('./pages/Admin.vue'),meta:{adminSection:'overview',title:'工作概览'}},
  {path:'accounts',component:()=>import('./pages/AdminAccounts.vue'),meta:{adminSection:'accounts',title:'账户管理'}},
  {path:'works',component:()=>import('./pages/AdminWorks.vue'),meta:{adminSection:'works',title:'文章管理 / 审核'}},
  {path:'tags',component:()=>import('./pages/AdminTags.vue'),meta:{adminSection:'tags',title:'活动与标签'}},
  {path:'import',component:()=>import('./pages/AdminImport.vue'),meta:{adminSection:'import',title:'批量导入'}},
  {path:'settings',component:()=>import('./pages/AdminSettings.vue'),meta:{adminSection:'settings',title:'站点设置'}},
 ]},
 {path:'/:pathMatch(.*)*',redirect:'/'}]})
let ready=false
router.beforeEach(async to=>{if(!ready){try{await refreshAuth();ready=true}catch{}}if(to.meta.auth&&!user.value)return {path:'/login',query:{next:to.fullPath}};if(to.path==='/profile'&&user.value?.accountType==='PUBLIC')return '/account';if(to.meta.adminSection&&!canManage(to.meta.adminSection as AdminSection))return canManage('overview')?'/admin':'/account'})
export default router
