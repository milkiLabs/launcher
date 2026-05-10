<script setup>
import { withBase } from 'vitepress'
import PhoneFrame from './PhoneFrame.vue'
import ScreenPlaceholder from './ScreenPlaceholder.vue'

const features = [
  {
    tag: 'The Omnibar',
    title: 'One search bar to rule them all.',
    description: 'Tap the home button and a powerful search dialog slides up. Recent apps appear instantly. Start typing and results narrow in real time — apps, web, actions. No drawer-diving, no folder-hunting.',
    image: withBase('/images/search-dialog.jpg'),
    imageAlt: 'Milki Search Dialog',
    hasImage: true,
    reverse: false,
  },
  {
    tag: 'Prefixes',
    title: 'Type a letter. Search anywhere.',
    description: 'Prefixes are Milki\'s superpower. Type <kbd>f</kbd> to search files on your device. Type <kbd>c</kbd> to find and call contacts. Type <kbd>yt</kbd> to search YouTube. Create your own prefixes for Wikipedia, DuckDuckGo, or anything else. Each prefix gets its own color, so you always know where you\'re searching.',
    image: withBase('/images/contact-search.jpg'),
    imageAlt: 'File Search with prefix',
    hasImage: true,
    reverse: true,
  },
  // {
  //   tag: 'Smart Clipboard',
  //   title: 'It reads the room.',
  //   description: 'Copy a YouTube link and open Milki — it instantly suggests opening it in YouTube. Copy a search query and it suggests a web search. You can even define your own clipboard action rules.',
  //   image: null,
  //   imageAlt: 'Clipboard Actions',
  //   hasImage: false,
  //   placeholderLabel: 'GIF: Copy a YouTube URL, open search, tap "Open in YouTube"',
  //   placeholderIcon: '📋',
  //   reverse: false,
  // },
  {
    tag: 'Your Homescreen',
    title: 'Not just apps. Everything.',
    description: 'Pin apps, files, contacts, and widgets directly to your homescreen. Drag anything from search results onto the grid. Two widget modes — inline for always-visible, popup for space-efficient. Customize gestures for swipe and tap actions.',
    image: withBase('/images/home-screen.jpg'),
    imageAlt: 'Customized Home Screen',
    hasImage: true,
    reverse: false,
  },
]
</script>

<template>
  <section class="showcase">
    <div class="showcase-inner">
      <h2 class="section-title animate-in">Built around <span class="highlight">how you think</span>.</h2>
      <p class="section-subtitle animate-in">
        Every feature in Milki is designed to get you from thought to action in the fewest possible steps.
      </p>

      <div
        v-for="(feat, idx) in features"
        :key="idx"
        class="feature-row animate-in"
        :class="{ reverse: feat.reverse }"
      >
        <div class="feature-text">
          <span class="feature-tag">{{ feat.tag }}</span>
          <h3 v-html="feat.title"></h3>
          <p v-html="feat.description"></p>
        </div>

        <div class="feature-visual">
          <div class="phone-glow"></div>
          <PhoneFrame v-if="feat.hasImage">
            <img :src="feat.image" :alt="feat.imageAlt" class="feature-img" />
          </PhoneFrame>
          <ScreenPlaceholder
            v-else
            :label="feat.placeholderLabel"
            :icon="feat.placeholderIcon"
            aspect="phone"
          />
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
@import './home-vars.css';

.showcase {
  position: relative;
  z-index: 1;
  padding: var(--h-section-pad);
}

.showcase-inner {
  max-width: var(--h-max-width);
  margin: 0 auto;
}

.section-title {
  font-family: var(--h-font-body);
  font-size: 2.5rem;
  font-weight: 700;
  margin-bottom: 1rem;
  color: var(--vp-c-text-1);
  letter-spacing: -0.5px;
  text-align: center;
}

.highlight {
  font-family: var(--h-font-display);
  font-style: italic;
  background: linear-gradient(135deg, var(--h-green-500) 0%, var(--h-green-300) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.section-subtitle {
  text-align: center;
  color: var(--vp-c-text-2);
  font-size: 1.1rem;
  line-height: 1.6;
  max-width: 560px;
  margin: 0 auto 4rem;
  font-family: var(--h-font-body);
}

/* Feature rows */
.feature-row {
  display: flex;
  align-items: center;
  gap: 4rem;
  margin-bottom: 5rem;
}

.feature-row:last-child {
  margin-bottom: 0;
}

.feature-row.reverse {
  flex-direction: row-reverse;
}

.feature-text {
  flex: 1;
}

.feature-tag {
  display: inline-block;
  font-family: var(--h-font-body);
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 1.5px;
  color: var(--h-green-500);
  margin-bottom: 0.75rem;
  padding: 4px 12px;
  background: var(--h-primary-subtle);
  border-radius: 6px;
}

.feature-text h3 {
  font-family: var(--h-font-body);
  font-size: 1.85rem;
  font-weight: 700;
  color: var(--vp-c-text-1);
  margin-bottom: 1rem;
  line-height: 1.2;
  letter-spacing: -0.3px;
}

.feature-text p {
  font-family: var(--h-font-body);
  color: var(--vp-c-text-2);
  font-size: 1rem;
  line-height: 1.75;
}

.feature-text :deep(kbd) {
  background: var(--h-green-50);
  border-radius: 6px;
  padding: 2px 7px;
  font-family: var(--h-font-body);
  font-size: 0.85em;
  font-weight: 600;
  border: 1px solid var(--h-green-200);
  color: var(--h-green-600);
}

.feature-visual {
  flex: 0 0 auto;
  position: relative;
  display: flex;
  justify-content: center;
}

.phone-glow {
  position: absolute;
  width: 240px;
  height: 480px;
  border-radius: 40px;
  background: radial-gradient(circle, rgba(34,197,94,0.15), transparent 70%);
  filter: blur(40px);
  z-index: -1;
}

.feature-img {
  display: block;
  width: 260px;
}

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
  .feature-row,
  .feature-row.reverse {
    flex-direction: column;
    text-align: center;
    gap: 2.5rem;
    margin-bottom: 4rem;
  }
  .section-title { font-size: 2rem; }
  .feature-text h3 { font-size: 1.5rem; }
}
</style>
