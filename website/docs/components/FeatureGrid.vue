<template>
  <section class="features">
    <div class="features-inner">
      <h2 class="section-title animate-in">Everything else you'd <span class="highlight">expect</span>.</h2>

      <div class="features-grid">
        <div class="animate-in" v-for="(feat, idx) in feats" :key="idx">
          <div class="feature-card">
            <div class="icon-box">{{ feat.icon }}</div>
            <h3>{{ feat.title }}</h3>
            <p>{{ feat.body }}</p>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
const feats = [
  {
    icon: '🧩',
    title: 'Two Widget Modes',
    body: 'Inline widgets live on your grid. Popup widgets hide behind an icon and expand on tap — space-efficient and always accessible.',
  },
  {
    icon: '👆',
    title: 'Custom Gestures',
    body: 'Map swipe up, swipe down, single tap, or double tap to any action: open an app, lock the screen, show notifications, and more.',
  },
  {
    icon: '🌐',
    title: 'Bilingual Prefixes',
    body: 'Create multiple prefixes for the same service in different languages. Search Wikipedia in English with "w" and in Arabic with "و".',
  },
  {
    icon: '📌',
    title: 'Drag Anything to Home',
    body: 'Long-press any search result — app, file, contact, shortcut — and drag it straight to your homescreen.',
  },
]
</script>

<style scoped>
@import './home-vars.css';

.features {
  position: relative;
  z-index: 1;
  padding: var(--h-section-pad);
  text-align: center;
}

.features-inner {
  position: relative;
  max-width: var(--h-max-width);
  margin: 0 auto;
}

.section-title {
  font-family: var(--h-font-body);
  font-size: 2.5rem;
  font-weight: 700;
  margin-bottom: 3rem;
  color: var(--vp-c-text-1);
  letter-spacing: -0.5px;
}

.highlight {
  font-family: var(--h-font-display);
  font-style: italic;
  background: linear-gradient(135deg, var(--h-green-500) 0%, var(--h-green-300) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1.25rem;
}

.feature-card {
  height: 100%;
  background: var(--h-card-bg);
  padding: 2rem 1.5rem;
  border-radius: 20px;
  border: 1px solid var(--h-border);
  text-align: left;
  transition: transform 0.15s ease-out, box-shadow 0.15s ease-out, border-color 0.15s ease-out;
  position: relative;
  overflow: hidden;
}

.feature-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.04);
  border-color: var(--h-green-200);
}

.icon-box {
  font-size: 2rem;
  margin-bottom: 1rem;
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: var(--h-green-50);
  border: 1px solid var(--h-green-100);
}

.feature-card h3 {
  font-family: var(--h-font-body);
  font-size: 1.15rem;
  font-weight: 700;
  margin-bottom: 0.7rem;
  color: var(--vp-c-text-1);
}

.feature-card p {
  font-family: var(--h-font-body);
  color: var(--vp-c-text-2);
  line-height: 1.6;
  font-size: 0.92rem;
}

/* Staggered entrance */
.features-grid .animate-in:nth-child(1) { transition-delay: 0ms; }
.features-grid .animate-in:nth-child(2) { transition-delay: 80ms; }
.features-grid .animate-in:nth-child(3) { transition-delay: 160ms; }
.features-grid .animate-in:nth-child(4) { transition-delay: 240ms; }
.features-grid .animate-in:nth-child(5) { transition-delay: 320ms; }
.features-grid .animate-in:nth-child(6) { transition-delay: 400ms; }

/* Entrance animation */
.animate-in {
  opacity: 0;
  transform: translateY(28px);
  transition: opacity 0.7s cubic-bezier(0.16, 1, 0.3, 1), transform 0.7s cubic-bezier(0.16, 1, 0.3, 1);
}

.animate-in.visible {
  opacity: 1;
  transform: translateY(0);
}

@media (max-width: 960px) {
  .section-title { font-size: 2rem; }
  .feature-card { text-align: center; }
  .icon-box { margin-left: auto; margin-right: auto; }
}
</style>
