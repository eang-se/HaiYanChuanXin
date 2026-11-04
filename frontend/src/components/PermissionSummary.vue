<script setup lang="ts">
import {user} from '../api'
import {personalRole,roleLabels} from '../composables/admin'
const rows=[
 ['发布、编辑当前身份的文章','允许','允许','允许'],
 ['发布公告','不开放','允许','允许'],
 ['开展或结束活动','允许','允许','允许'],
 ['审核通过、下架、管理精选','允许','允许','允许'],
 ['删除文章、删除活动','不开放','允许','允许'],
 ['删除用户','不开放','不开放','允许'],
 ['配置普通成员角色及功能权限','不开放','允许','允许'],
 ['配置管理员等级、公用账号名单','不开放','不开放','允许'],
 ['站点名称与介绍','仅技术部','允许','允许'],
 ['注册策略与 QQ 注册名单','不开放','允许','允许'],
]
</script>
<template><section class="admin-section permission-summary"><div class="admin-section-title"><div><h2>当前权限体系</h2><p>个人等级：{{roleLabels[personalRole()]||'未登录'}} · 当前使用：{{user?.penName}}（{{user?.accountType==='PUBLIC'?'公用身份':'个人身份'}}）</p></div></div><p class="admin-info">公用身份提供日常发布、活动与审核能力。删除、账号配置等高级操作由个人账号等级决定，切换公用身份后保持一致。用户草稿仅在其书桌管理。</p><div class="admin-table-wrap"><table class="admin-table"><thead><tr><th>操作</th><th>公用身份的基础能力</th><th>个人管理员</th><th>个人超级管理员</th></tr></thead><tbody><tr v-for="row in rows" :key="row[0]"><td v-for="(cell,index) in row" :key="index">{{cell}}</td></tr></tbody></table></div></section></template>
<style scoped>.permission-summary .admin-info{font-size:13px;line-height:1.9}.permission-summary td{white-space:normal}.permission-summary table{min-width:590px}</style>
