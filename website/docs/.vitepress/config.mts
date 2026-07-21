import { defineConfig } from "vitepress";
import { ViteImageOptimizer } from "vite-plugin-image-optimizer";
import fs from "fs/promises";
import path from "path";
import sharp from "sharp";

// Write _headers file for GitHub Pages (cache control + security)
async function writeHeaders(outDir: string) {
  const headersContent = [
    "# Cache-Control for hashed assets (immutable)",
    "/launcher/assets/*",
    "  Cache-Control: public, max-age=31536000, immutable",
    "",
    "# Cache-Control for images",
    "/launcher/images/*",
    "  Cache-Control: public, max-age=31536000, immutable",
    "",
    "# Cache-Control for the favicon",
    "/launcher/icon.svg",
    "  Cache-Control: public, max-age=31536000, immutable",
    "",
    "# Security + cache for HTML pages",
    "/launcher/*.html",
    "  X-Frame-Options: DENY",
    "  X-Content-Type-Options: nosniff",
    "  Referrer-Policy: no-referrer-when-downgrade",
    "  Cache-Control: public, max-age=0, must-revalidate",
    "",
    "# Security + cache for root path",
    "/launcher/",
    "  X-Frame-Options: DENY",
    "  X-Content-Type-Options: nosniff",
    "  Referrer-Policy: no-referrer-when-downgrade",
    "  Cache-Control: public, max-age=0, must-revalidate",
    "",
  ].join("\n");
  const headersPath = path.join(outDir, "_headers");
  await fs.writeFile(headersPath, headersContent, "utf-8");
  console.log("✅ _headers file written to", headersPath);
}

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
  srcExclude: ["guide/**"],
  title: "Milki Launcher",
  description:
    "An open-source, privacy-respecting Android launcher focused on search-driven usage.",
  head: [
    ["link", { rel: "icon", type: "image/svg+xml", href: "/launcher/icon.svg" }],
    ["link", { rel: "preconnect", href: "https://fonts.googleapis.com" }],
    ["link", { rel: "preconnect", href: "https://fonts.gstatic.com", crossorigin: "" }],
    [
      "link",
      {
        rel: "stylesheet",
        media: "print",
        onload: "this.media='all'",
        href: "https://fonts.googleapis.com/css2?family=DM+Sans:ital,opsz,wght@0,9..40,300;0,9..40,400;0,9..40,500;0,9..40,600;0,9..40,700;1,9..40,400&family=Playfair+Display:wght@600;700;800&display=swap",
      },
    ],
    [
      "noscript",
      {},
      '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=DM+Sans:ital,opsz,wght@0,9..40,300;0,9..40,400;0,9..40,500;0,9..40,600;0,9..40,700;1,9..40,400&family=Playfair+Display:wght@600;700;800&display=swap">',
    ],
  ],
  
  // This automatically runs after the Vite build completes
  async buildEnd(siteConfig) {
    console.log("🎨 Auto-converting all JPG/PNG images to WebP...");
    await convertToWebP(siteConfig.outDir);
    console.log("✅ Image optimization complete.");
    await writeHeaders(siteConfig.outDir);
  },

  themeConfig: {
    logo: "/icon.svg",
    // https://vitepress.dev/reference/default-theme-config
    nav: [
      { text: "Home", link: "/" },
      // Temporarily hidden while the guide route is paused.
      // { text: "Guide", link: "/guide/overview" },
    ],

    // Temporarily hidden while the guide route is paused.
    // sidebar: [
    //   {
    //     text: "Getting Started",
    //     items: [
    //       { text: "Overview", link: "/guide/overview" },
    //       { text: "Home Screen", link: "/guide/homescreen" },
    //       { text: "Search Dialog", link: "/guide/search-dialog" },
    //     ],
    //   },
    // ],

    socialLinks: [
      { icon: "github", link: "https://github.com/milkilabs/launcher" },
    ],
  },
});
