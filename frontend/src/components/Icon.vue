<script setup lang="ts">
import { ArrowRight,ArrowUpRight,ArrowDown,Search,Feather,BookOpen,Heart,Bookmark,MessageCircle,Menu,X,Plus,Check,Upload,Quote,Image,Bold,Italic,List,Heading2,Undo,Redo,LogOut,Send,FileText,Settings,ChevronLeft,Sparkles,Shuffle,RefreshCw,Hash,Users,LayoutDashboard,ChevronRight,ShieldCheck,House,UserRound,Sun,Moon } from '@lucide/vue'
defineProps<{name:string;size?:number}>()
const icons:Record<string,any>={ArrowRight,ArrowUpRight,ArrowDown,Search,Feather,BookOpen,Heart,Bookmark,MessageCircle,Menu,X,Plus,Check,Upload,Quote,Image,Bold,Italic,List,Heading2,Undo,Redo,LogOut,Send,FileText,Settings,ChevronLeft,Sparkles,Shuffle,RefreshCw,Hash,Users,LayoutDashboard,ChevronRight,ShieldCheck,House,UserRound,Sun,Moon}
</script>
<template><component :is="icons[name]||Feather" :size="size||18" :stroke-width="1.65" aria-hidden="true" /></template>
