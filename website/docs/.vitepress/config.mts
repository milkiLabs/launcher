import { defineConfig } from "vitepress";

// https://vitepress.dev/reference/site-config
export default defineConfig({
  base: "/launcher/",
  title: "Milki Launcher",
  description:
    "An open-source, privacy-respecting Android launcher focused on search-driven usage.",
  head: [["link", { rel: "icon", type: "image/svg+xml", href: "/icon.svg" }]],
  themeConfig: {
    logo: "/icon.svg",
    // https://vitepress.dev/reference/default-theme-config
    nav: [
      { text: "Home", link: "/" },
      { text: "Guide", link: "/guide/overview" },
    ],

    sidebar: [
      {
        text: "Getting Started",
        items: [
          { text: "Overview", link: "/guide/overview" },
          { text: "Home Screen", link: "/guide/homescreen" },
          { text: "Search Dialog", link: "/guide/search-dialog" },
        ],
      },
    ],

    socialLinks: [
      { icon: "github", link: "https://github.com/milkilabs/launcher" },
    ],
  },
});
