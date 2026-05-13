<script setup>
import { withBase } from 'vitepress'
import { onMounted, onUnmounted, ref } from 'vue'
import PhoneFrame from './PhoneFrame.vue'

// Typing animation for the search demo
const typingTexts = [
  { prefix: '', text: 'whats', label: 'Find any app instantly' },
  { prefix: 'f ', text: 'budget.pdf', label: 'Search files on your device' },
  { prefix: 'c ', text: 'sarah', label: 'Call a contact in two taps' },
  { prefix: 'yt ', text: 'lo-fi music', label: 'Search YouTube directly' },
]
const currentTypingIdx = ref(0)
const displayedText = ref('')
const isDeleting = ref(false)
let typingTimeout = null

const typeStep = () => {
  const current = typingTexts[currentTypingIdx.value]
  const fullText = current.prefix + current.text

  if (!isDeleting.value) {
    displayedText.value = fullText.slice(0, displayedText.value.length + 1)
    if (displayedText.value === fullText) {
      typingTimeout = setTimeout(() => {
        isDeleting.value = true
        typeStep()
      }, 2200)
      return
    }
    typingTimeout = setTimeout(typeStep, 80 + Math.random() * 40)
  } else {
    displayedText.value = fullText.slice(0, displayedText.value.length - 1)
    if (displayedText.value === '') {
      isDeleting.value = false
      currentTypingIdx.value = (currentTypingIdx.value + 1) % typingTexts.length
      typingTimeout = setTimeout(typeStep, 400)
      return
    }
    typingTimeout = setTimeout(typeStep, 35)
  }
}

onMounted(() => { typeStep() })
onUnmounted(() => { clearTimeout(typingTimeout) })
</script>

<template>
  <section class="hero">
    <div class="hero-content">

      <div class="title-row animate-in">
        <h1 class="hero-title">
          Your phone.<br><span>Your rules.</span>
        </h1>
        <span class="beta-badge">BETA</span>
      </div>

      <p class="hero-subtitle animate-in">
        <strong>Milki Launcher</strong> is a search-first Android launcher that replaces
        mindless scrolling with instant access. Find apps, files, contacts, and
        anything else — all from one search bar. No ads. No tracking. No paywalls.
      </p>

      <!-- Animated search bar demo -->
      <div class="search-demo animate-in">
        <div class="search-bar">
          <svg class="search-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"/>
            <path d="m21 21-4.3-4.3"/>
          </svg>
          <span class="search-text">
            <span class="search-prefix" v-if="typingTexts[currentTypingIdx].prefix">{{ typingTexts[currentTypingIdx].prefix }}</span>{{ displayedText.slice(typingTexts[currentTypingIdx].prefix.length) }}
          </span>
          <span class="cursor-blink">|</span>
        </div>
        <span class="search-hint">{{ typingTexts[currentTypingIdx].label }}</span>
      </div>

      <div class="hero-actions animate-in">
        <a href="https://github.com/milkilabs/launcher/releases/latest" class="btn btn-primary" id="hero-download">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
          Download APK
        </a>
        <a href="/launcher/guide/overview" class="btn btn-secondary" id="hero-guide">Read the Guide →</a>
      </div>
    </div>

    <div class="hero-visual animate-in">
      <div class="mockup-glow"></div>
      <PhoneFrame>
        <img :src="withBase('/images/home-screen.webp')" alt="Milki Launcher Home Screen" class="hero-image" />
      </PhoneFrame>
    </div>
  </section>
</template>

<style scoped>
@import './home-vars.css';

.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: var(--h-max-width);
  margin: 0 auto;
  padding: 7rem 2rem 5rem;
  gap: 4rem;
  min-height: calc(100vh - var(--vp-nav-height));
  position: relative;
  z-index: 1;
}

.hero-content {
  flex: 1;
  max-width: 580px;
}



@keyframes pulse-dot {
  0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgba(34,197,94,0.5); }
  50% { opacity: 0.8; box-shadow: 0 0 0 6px rgba(34,197,94,0); }
}

/* Title */
.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.beta-badge {
  display: inline-block;
  padding: 4px 10px;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 1.5px;
  border-radius: 6px;
  background: var(--h-green-50);
  color: var(--h-green-600);
  border: 1px solid var(--h-green-200);
}

.hero-title {
  font-family: var(--h-font-body);
  font-size: 4rem;
  font-weight: 700;
  line-height: 1.08;
  letter-spacing: -1.5px;
  margin-bottom: 1.25rem;
  color: var(--vp-c-text-1);
}

.hero-title span {
  font-family: var(--h-font-display);
  font-weight: 800;
  font-style: italic;
  background: linear-gradient(135deg, var(--h-green-600) 20%, var(--h-green-400) 80%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-subtitle {
  font-family: var(--h-font-body);
  font-size: 1.12rem;
  line-height: 1.75;
  color: var(--vp-c-text-2);
  margin-bottom: 2rem;
}

.hero-subtitle strong {
  color: var(--h-green-600);
}

/* Search demo */
.search-demo { margin-bottom: 2.5rem; }

.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  border-radius: 16px;
  background: var(--vp-c-bg-soft);
  border: 1.5px solid var(--h-border);
  font-size: 1.05rem;
  font-family: var(--h-font-body);
  color: var(--vp-c-text-1);
  transition: border-color 0.3s ease, box-shadow 0.3s ease;
  max-width: 420px;
  backdrop-filter: blur(10px);
}

.search-bar:hover {
  border-color: var(--h-green-400);
  box-shadow: 0 0 0 4px rgba(34,197,94,0.08), 0 4px 20px rgba(34,197,94,0.06);
}

.search-icon { color: var(--vp-c-text-3); flex-shrink: 0; }
.search-text { white-space: nowrap; overflow: hidden; }
.search-prefix { color: var(--h-green-500); font-weight: 600; }

.cursor-blink {
  animation: blink 1s step-end infinite;
  color: var(--h-green-500);
  font-weight: 300;
  margin-left: -2px;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.search-hint {
  display: block;
  font-size: 0.8rem;
  color: var(--vp-c-text-3);
  margin-top: 8px;
  padding-left: 4px;
  font-family: var(--h-font-body);
  transition: color 0.2s ease;
}

/* Buttons */
.hero-actions { display: flex; gap: 1rem; }

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.85rem 2rem;
  font-size: 0.95rem;
  font-weight: 600;
  border-radius: 14px;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  text-decoration: none;
  font-family: var(--h-font-body);
  gap: 8px;
}

.btn-primary {
  background: linear-gradient(135deg, var(--h-green-500), var(--h-green-600));
  color: #fff;
  box-shadow: 0 4px 16px rgba(34,197,94,0.3), 0 1px 3px rgba(0,0,0,0.08);
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 28px rgba(34,197,94,0.35), 0 2px 6px rgba(0,0,0,0.1);
  background: linear-gradient(135deg, var(--h-green-400), var(--h-green-500));
}

.btn-secondary {
  background-color: var(--h-card-bg);
  color: var(--vp-c-text-1);
  border: 1.5px solid var(--h-border);
}

.btn-secondary:hover {
  border-color: var(--h-green-400);
  background: var(--h-primary-subtle);
  transform: translateY(-2px);
}

/* Phone mockup */
.hero-visual {
  position: relative;
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  animation: float 7s ease-in-out infinite;
}

.mockup-glow {
  position: absolute;
  width: 300px;
  height: 600px;
  border-radius: 40px;
  background: linear-gradient(180deg, var(--h-green-400), var(--h-green-300));
  filter: blur(70px);
  opacity: 0.2;
  z-index: -1;
  animation: breathe 6s ease-in-out infinite;
}

@keyframes float {
  0% { transform: translateY(0px); }
  50% { transform: translateY(-10px); }
  100% { transform: translateY(0px); }
}

@keyframes breathe {
  0%, 100% { opacity: 0.18; transform: scale(1); }
  50% { opacity: 0.28; transform: scale(1.04); }
}

.hero-image {
  display: block;
  max-width: 280px;
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
  .hero {
    flex-direction: column;
    text-align: center;
    padding: 5rem 1.25rem 3rem;
    gap: 3rem;
  }
  .hero-title { font-size: 2.8rem; }
  .hero-actions { justify-content: center; gap: 0.75rem; }
  .btn { padding: 0.65rem 1.25rem; font-size: 0.85rem; border-radius: 10px; }
  .search-bar { margin: 0 auto; }
  .search-hint { text-align: center; }
}
</style>
