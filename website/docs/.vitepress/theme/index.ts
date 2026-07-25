// https://vitepress.dev/guide/custom-theme
import { h } from 'vue'
import type { Theme } from 'vitepress'
import DefaultTheme from 'vitepress/theme'
import NavBar from '../../components/NavBar.vue'
import DocVideo from '../../components/DocVideo.vue'
import './style.css'

export default {
  extends: DefaultTheme,
  Layout: () => {
    return h(DefaultTheme.Layout, null, {
      // https://vitepress.dev/guide/extending-default-theme#layout-slots
      'layout-top': () => h(NavBar)
    })
  },
  enhanceApp({ app, router, siteData }) {
    app.component('DocVideo', DocVideo)
  }
} satisfies Theme
