<script setup>
import { ref, onMounted, onUnmounted, computed } from "vue";
import { useRoute } from "vitepress";

const route = useRoute();
const isScrolled = ref(false);
const isMobileMenuOpen = ref(false);

const handleScroll = () => {
  isScrolled.value = window.scrollY > 20;
};

onMounted(() => {
  window.addEventListener("scroll", handleScroll, { passive: true });
});

onUnmounted(() => {
  window.removeEventListener("scroll", handleScroll);
});

const navLinks = [
  { text: "Home", link: "/launcher/" },
  { text: "Guide", link: "/launcher/guide/overview" },
];

const isActive = (link) => {
  if (link === "/launcher/") {
    return (
      route.path === "/" ||
      route.path === "/launcher/" ||
      route.path === "/launcher/index.html" ||
      route.path === "/index.html"
    );
  }
  return route.path.startsWith("/launcher/guide") || route.path.startsWith("/guide");
};

const toggleMobileMenu = () => {
  isMobileMenuOpen.value = !isMobileMenuOpen.value;
  // Prevent scrolling when menu is open
  if (isMobileMenuOpen.value) {
    document.body.style.overflow = "hidden";
  } else {
    document.body.style.overflow = "";
  }
};

const closeMenu = () => {
  isMobileMenuOpen.value = false;
  document.body.style.overflow = "";
};
</script>

<template>
  <header class="milki-nav" :class="{ 'is-scrolled': isScrolled }">
    <div class="nav-container">
      <a href="/launcher/" class="nav-brand" @click="closeMenu">
        <img src="/icon.svg" alt="Milki Logo" class="nav-logo" width="38" height="38" />
        <span class="nav-title">Milki Launcher</span>
      </a>

      <!-- Desktop Nav -->
      <nav class="nav-links desktop-only">
        <a
          v-for="link in navLinks"
          :key="link.text"
          :href="link.link"
          class="nav-link"
          :class="{ 'is-active': isActive(link.link) }"
        >
          {{ link.text }}
        </a>
      </nav>

      <div class="nav-actions desktop-only">
        <a
          href="https://github.com/milkilabs/launcher"
          target="_blank"
          rel="noopener"
          class="icon-link"
          aria-label="GitHub"
        >
          <svg
            width="22"
            height="22"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path
              d="M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 0 0-.94-2.61c3.14-.35 6.44-1.54 6.44-7A5.44 5.44 0 0 0 20 4.77 5.07 5.07 0 0 0 19.91 1S18.73.65 16 2.48a13.38 13.38 0 0 0-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 0 0 5 4.77a5.44 5.44 0 0 0-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 0 0 9 18.13V22"
            ></path>
          </svg>
        </a>
        <a
          target="_blank"
          href="https://github.com/milkilabs/launcher/releases/latest/download/milki_launcher_latest.apk"
          class="btn-download"
        >
          Download
        </a>
      </div>

      <!-- Mobile Menu Toggle -->
      <button
        class="mobile-toggle"
        @click="toggleMobileMenu"
        :aria-expanded="isMobileMenuOpen"
        aria-label="Toggle Menu"
      >
        <div class="hamburger" :class="{ 'is-active': isMobileMenuOpen }">
          <span class="line line-1"></span>
          <span class="line line-2"></span>
          <span class="line line-3"></span>
        </div>
      </button>
    </div>

    <!-- Mobile Nav Overlay -->
    <div class="mobile-menu" :class="{ 'is-open': isMobileMenuOpen }">
      <nav class="mobile-nav-links">
        <a
          v-for="link in navLinks"
          :key="link.text"
          :href="link.link"
          class="mobile-nav-link"
          :class="{ 'is-active': isActive(link.link) }"
          @click="closeMenu"
        >
          {{ link.text }}
        </a>
        <a href="https://github.com/milkilabs/launcher" class="mobile-nav-link" @click="closeMenu"
          >GitHub</a
        >
        <a
          href="https://github.com/milkilabs/launcher/releases/latest/download/milki_launcher_latest.apk"
          class="btn-download mobile-btn"
          @click="closeMenu"
          >Download APK</a
        >
      </nav>
    </div>
  </header>
</template>

<style scoped>
.milki-nav {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 72px;
  z-index: 100;
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
  font-family: "DM Sans", sans-serif;
  background: transparent;
  border-bottom: 1px solid transparent;
}

.milki-nav.is-scrolled {
  background: var(--vp-c-bg);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--vp-c-divider);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
}

.nav-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 2rem;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.nav-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  text-decoration: none;
  z-index: 101;
}

.nav-logo {
  height: 38px;
  width: auto;
  transition: transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.nav-brand:hover .nav-logo {
  transform: scale(1.1) rotate(-8deg);
}

.nav-title {
  font-family: "Playfair Display", serif;
  font-weight: 800;
  font-size: 1.5rem;
  font-style: italic;
  color: var(--vp-c-text-1);
  letter-spacing: -0.5px;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 2.5rem;
}

.nav-link {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--vp-c-text-2);
  text-decoration: none;
  transition: color 0.3s ease;
  position: relative;
  padding: 0.5rem 0;
}

.nav-link:hover {
  color: #22c55e;
}

.nav-link::after {
  content: "";
  position: absolute;
  bottom: 0;
  left: 0;
  width: 0;
  height: 2px;
  background: #22c55e;
  transition: width 0.3s ease;
  border-radius: 2px;
}

.nav-link:hover::after {
  width: 100%;
}

.nav-link.is-active {
  color: #22c55e;
}

.nav-link.is-active::after {
  width: 100%;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.icon-link {
  color: var(--vp-c-text-2);
  transition:
    color 0.3s ease,
    transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  display: flex;
  align-items: center;
  justify-content: center;
}

.icon-link:hover {
  color: var(--vp-c-text-1);
  transform: translateY(-3px);
}

.btn-download {
  background: linear-gradient(135deg, #22c55e, #16a34a);
  color: #fff;
  padding: 0.55rem 1.4rem;
  border-radius: 12px;
  font-size: 0.95rem;
  font-weight: 700;
  text-decoration: none;
  transition:
    transform 0.3s ease,
    box-shadow 0.3s ease;
  box-shadow: 0 4px 14px rgba(34, 197, 94, 0.25);
}

.btn-download:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(34, 197, 94, 0.35);
}

/* Mobile Toggle Hamburger */
.mobile-toggle {
  display: none;
  background: transparent;
  border: none;
  cursor: pointer;
  z-index: 101;
  padding: 8px;
}

.hamburger {
  width: 24px;
  height: 18px;
  position: relative;
}

.hamburger .line {
  display: block;
  position: absolute;
  height: 2px;
  width: 100%;
  background: var(--vp-c-text-1);
  border-radius: 2px;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

.hamburger .line-1 {
  top: 0;
}
.hamburger .line-2 {
  top: 8px;
  width: 70%;
  right: 0;
}
.hamburger .line-3 {
  top: 16px;
}

.hamburger.is-active .line-1 {
  transform: translateY(8px) rotate(45deg);
}

.hamburger.is-active .line-2 {
  opacity: 0;
  transform: translateX(10px);
}

.hamburger.is-active .line-3 {
  transform: translateY(-8px) rotate(-45deg);
}

/* Mobile Menu Overlay */
.mobile-menu {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100vh;
  background: var(--vp-c-bg);
  z-index: 100;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.4s ease;
  backdrop-filter: blur(10px);
}

.mobile-menu.is-open {
  opacity: 1;
  pointer-events: auto;
}

.mobile-nav-links {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2rem;
  transform: translateY(20px);
  transition: transform 0.5s cubic-bezier(0.16, 1, 0.3, 1);
}

.mobile-menu.is-open .mobile-nav-links {
  transform: translateY(0);
}

.mobile-nav-link {
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--vp-c-text-1);
  text-decoration: none;
  transition:
    color 0.2s ease,
    transform 0.2s ease;
}

.mobile-nav-link:hover {
  color: #22c55e;
  transform: scale(1.05);
}

.mobile-nav-link.is-active {
  color: #22c55e;
}

.mobile-btn {
  margin-top: 1rem;
  font-size: 1.2rem;
  padding: 0.8rem 2rem;
}

@media (max-width: 768px) {
  .desktop-only {
    display: none !important;
  }
  .mobile-toggle {
    display: block;
  }
  .nav-container {
    padding: 0 1.25rem;
  }
}
</style>
