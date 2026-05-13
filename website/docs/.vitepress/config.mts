import { defineConfig } from "vitepress";
import { ViteImageOptimizer } from "vite-plugin-image-optimizer";
import fs from "fs/promises";
import path from "path";
import sharp from "sharp";

// Auto-convert any legacy image formats to WebP in the final build
async function convertToWebP(dir: string) {
  const files = await fs.readdir(dir, { withFileTypes: true });
  for (const file of files) {
    const fullPath = path.join(dir, file.name);
    if (file.isDirectory()) {
      await convertToWebP(fullPath);
    } else {
      // Rewrite references in output files
      if (/\.(html|js|css)$/.test(file.name)) {
        const content = await fs.readFile(fullPath, "utf-8");
        const newContent = content.replace(/\.(png|jpe?g)(["')\s])/gi, ".webp$2");
        if (content !== newContent) {
          await fs.writeFile(fullPath, newContent, "utf-8");
        }
      }
      // Convert images and delete old ones
      else if (/\.(png|jpe?g)$/i.test(file.name)) {
        const webpPath = fullPath.replace(/\.(png|jpe?g)$/i, ".webp");
        await sharp(fullPath).webp({ quality: 80, effort: 6 }).toFile(webpPath);
        await fs.unlink(fullPath);
      }
    }
  }
}

// https://vitepress.dev/reference/site-config
export default defineConfig({
  vite: {
    plugins: [
      ViteImageOptimizer({
        // keep optimizer for other formats like svg and natively added webp
        svg: { multipass: true },
        webp: { quality: 80, effort: 6 },
      }),
    ],
  },
  base: "/launcher/",
  title: "Milki Launcher",
  description:
    "An open-source, privacy-respecting Android launcher focused on search-driven usage.",
  head: [["link", { rel: "icon", type: "image/svg+xml", href: "/icon.svg" }]],
  
  // This automatically runs after the Vite build completes
  async buildEnd(siteConfig) {
    console.log("🎨 Auto-converting all JPG/PNG images to WebP...");
    await convertToWebP(siteConfig.outDir);
    console.log("✅ Image optimization complete.");
  },

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
