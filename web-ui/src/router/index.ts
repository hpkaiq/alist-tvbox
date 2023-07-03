import {createRouter, createWebHashHistory} from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import SitesView from "@/views/SitesView.vue";
import SubscriptionsView from "@/views/SubscriptionsView.vue";
import ConfigView from "@/views/ConfigView.vue";
import SubView from "@/views/SubView.vue";
import AboutView from "@/views/AboutView.vue";
import LoginView from "@/views/LoginView.vue";
import UserView from "@/views/UserView.vue";
import SearchView from "@/views/SearchView.vue";
import VodView from "@/views/VodView.vue";
import SharesView from "@/views/SharesView.vue";
import AccountsView from "@/views/AccountsView.vue";
import FilesView from "@/views/FilesView.vue";
import WaitAList from "@/views/WaitAList.vue";
import AliasView from "@/views/AliasView.vue";
import PikPakView from "@/views/PikPakView.vue";

const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  linkActiveClass: 'is-active',
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: {auth: true}
    },
    {
      path: '/wait',
      name: 'wait',
      component: WaitAList,
      meta: {auth: true}
    },
    {
      path: '/accounts',
      name: 'accounts',
      component: AccountsView,
      meta: {auth: true}
    },
    {
      path: '/pikpak',
      name: 'pikpak',
      component: PikPakView,
      meta: {auth: true}
    },
    {
      path: '/sites',
      name: 'sites',
      component: SitesView,
      meta: {auth: true}
    },
    {
      path: '/files',
      name: 'files',
      component: FilesView,
      meta: {auth: true}
    },
    {
      path: '/alias',
      name: 'alias',
      component: AliasView,
      meta: {auth: true}
    },
    {
      path: '/subscriptions',
      name: 'subscriptions',
      component: SubscriptionsView,
      meta: {auth: true}
    },
    {
      path: '/shares',
      name: 'shares',
      component: SharesView,
      meta: {auth: true}
    },
    {
      path: '/user',
      name: 'user',
      component: UserView,
      meta: {auth: true}
    },
    {
      path: '/config',
      name: 'config',
      component: ConfigView,
      meta: {auth: true}
    },
    {
      path: '/sub/:id',
      name: 'sub',
      component: SubView,
      meta: {auth: true}
    },
    {
      path: '/vod',
      name: 'vod',
      component: VodView,
      meta: {auth: true}
    },
    {
      path: '/search',
      name: 'search',
      component: SearchView,
      meta: {auth: true}
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: {guest: true}
    },
    {
      path: '/about',
      name: 'about',
      component: AboutView,
      meta: {auth: true}
    }
  ]
})

export default router
