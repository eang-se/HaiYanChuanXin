<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import {user} from '../api'
import Icon from '../components/Icon.vue'
import {canManage,personalRole,roleLabels,type AdminSection} from '../composables/admin'
import '../admin.css'
const route=useRoute()
const links=computed(()=>[
  {section:'overview',path:'/admin',label:'工作概览',icon:'LayoutDashboard',group:'工作空间'},
  {section:'accounts',path:'/admin/accounts',label:'账户管理',icon:'Users',group:'社务管理'},
  {section:'works',path:'/admin/works',label:'文章管理 / 审核',icon:'FileText',group:'社务管理'},
  {section:'tags',path:'/admin/tags',label:'活动与标签',icon:'Hash',group:'社务管理'},
  {section:'import',path:'/admin/import',label:'批量导入',icon:'Upload',group:'社务管理'},
  {section:'settings',path:'/admin/settings',label:'站点设置',icon:'Settings',group:'系统设置'},
].filter(link=>canManage(link.section as AdminSection)))
</script>
<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <RouterLink to="/admin" class="admin-brand"><span class="admin-brand-mark"><Icon name="Feather" :size="21"/></span><span>社务台<small>海燕文学社 · 管理空间</small></span></RouterLink>
      <nav class="admin-nav" aria-label="社务台导航">
        <template v-for="(link,index) in links" :key="link.path">
          <p v-if="!index||links[index-1]?.group!==link.group" class="admin-nav-group">{{link.group}}</p>
          <RouterLink :to="link.path" :class="{selected:route.path===link.path}" :aria-current="route.path===link.path?'page':undefined"><Icon :name="link.icon" :size="18"/><span>{{link.label}}</span><Icon v-if="route.path===link.path" name="ChevronRight" :size="14"/></RouterLink>
        </template>
      </nav>
      <div class="admin-sidebar-footer"><span class="admin-identity-dot"></span><div><strong>{{user?.penName}}</strong><small>{{roleLabels[personalRole()]||'公用身份'}} · {{user?.accountType==='PUBLIC'?'公用账户':'个人账户'}}</small></div></div>
    </aside>
    <div class="admin-main">
      <div class="admin-topline"><div><RouterLink to="/admin">社务台</RouterLink><span>/</span><span>{{route.meta.title}}</span></div><RouterLink to="/" class="admin-return">返回网站 <Icon name="ArrowUpRight" :size="14"/></RouterLink></div>
      <RouterView/>
    </div>
  </div>
</template>
