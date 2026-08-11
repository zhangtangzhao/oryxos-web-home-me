import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'OryxOS',
  description: 'Enterprise Agent OS — Java Native, Private, Auditable',
  base: '/oryxos/',
  lang: 'en-US',
  head: [
    ['link', { rel: 'icon', href: '/oryxos/logo.svg' }],
  ],

  themeConfig: {
    logo: '/logo.svg',
    siteTitle: 'OryxOS',

    nav: [
      { text: 'Home', link: '/' },
      { text: 'Docs', link: '/en/introduction' },
      { text: '中文', link: '/zh/' },
      {
        text: 'GitHub',
        link: 'https://github.com/oryx-labs/oryxos',
      },
    ],

    footer: {
      message: 'Released under the Apache License 2.0.',
      copyright: 'Copyright © 2025–2026 oryx-labs',
    },
  },

  locales: {
    root: {
      label: 'English',
      lang: 'en-US',
      themeConfig: {
        nav: [
          { text: 'Home', link: '/' },
          { text: 'Introduction', link: '/en/introduction' },
          { text: '中文', link: '/zh/' },
          {
            text: 'GitHub',
            link: 'https://github.com/oryx-labs/oryxos',
          },
        ],
      },
    },
    zh: {
      label: '简体中文',
      lang: 'zh-CN',
      link: '/zh/',
      themeConfig: {
        nav: [
          { text: '首页', link: '/zh/' },
          { text: '介绍', link: '/zh/introduction' },
          { text: 'English', link: '/' },
          {
            text: 'GitHub',
            link: 'https://github.com/oryx-labs/oryxos',
          },
        ],
      },
    },
  },
})
