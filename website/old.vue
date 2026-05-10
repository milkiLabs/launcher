<script setup>
import { onMounted, onUnmounted, ref } from 'vue';
import PhoneFrame from './PhoneFrame.vue';

const images = [
  { src: '/images/search-dialog.jpg', title: 'Smart Search', desc: 'Tap home → find anything instantly', icon: '🔍' },
  { src: '/images/file search.jpg', title: 'File Search', desc: 'Type "f" to deep-search files', icon: '📁' },
  { src: '/images/contact search.jpg', title: 'Contacts', desc: 'Type "c" to find contacts', icon: '👤' },
  { src: '/images/widgets selection.jpg', title: 'Widgets', desc: 'Customize your homescreen', icon: '🧩' }
]

const currentImage = ref(0);
let intervalId = null;

const startInterval = () => {
  if (intervalId !== null) return;
  intervalId = setInterval(() => {
    currentImage.value = (currentImage.value + 1) % images.length;
  }, 4000);
};

const stopInterval = () => {
  if (intervalId !== null) {
    clearInterval(intervalId);
    intervalId = null;
  }
};

const handleHover = (idx) => {
  currentImage.value = idx;
};

// Typing animation for the search demo
const typingTexts = [
  { prefix: '', text: 'whats', label: 'Search apps' },
  { prefix: 'f ', text: 'budget.pdf', label: 'Search files' },
  { prefix: 'c ', text: 'mom', label: 'Search contacts' },
  { prefix: 'yt ', text: 'lo-fi music', label: 'Search YouTube' },
];
const currentTypingIdx = ref(0);
const displayedText = ref('');
const isDeleting = ref(false);
let typingTimeout = null;

const typeStep = () => {
  const current = typingTexts[currentTypingIdx.value];
  const fullText = current.prefix + current.text;

  if (!isDeleting.value) {
    displayedText.value = fullText.slice(0, displayedText.value.length + 1);
    if (displayedText.value === fullText) {
      typingTimeout = setTimeout(() => {
        isDeleting.value = true;
        typeStep();
      }, 2000);
      return;
    }
    typingTimeout = setTimeout(typeStep, 80 + Math.random() * 40);
  } else {
    displayedText.value = fullText.slice(0, displayedText.value.length - 1);
    if (displayedText.value === '') {
      isDeleting.value = false;
      currentTypingIdx.value = (currentTypingIdx.value + 1) % typingTexts.length;
      typingTimeout = setTimeout(typeStep, 400);
      return;
    }
    typingTimeout = setTimeout(typeStep, 35);
  }
};

// Scroll-based entrance animations
const observeElements = () => {
  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
      }
    });
  }, { threshold: 0.15 });

  document.querySelectorAll('.animate-in').forEach((el) => {
    observer.observe(el);
  });
};

onMounted(() => {
  startInterval();
  typeStep();
  setTimeout(observeElements, 100);
});

onUnmounted(() => {
  stopInterval();
  clearTimeout(typingTimeout);
});
</script>

<template>
  <div class="milki-home">
    <!-- Organic background layers -->
    <div class="bg-layer">
      <div class="bg-noise"></div>
      <div class="bg-blob blob-1"></div>
      <div class="bg-blob blob-2"></div>
      <div class="bg-blob blob-3"></div>
      <div class="bg-blob blob-4"></div>
      
      <!-- Better Animated Leaves -->
      <div class="leaf leaf-1"><svg viewBox="0 0 32 32" fill="currentColor"><path d="M28.082 9.534c-0.058 0.174-0.117 0.349-0.176 0.525 0.674 3.296 0.425 6.877-1.276 10.787 0.247-2.511 0.206-4.913-0.182-7.215-0.458 0.891-1.042 1.755-1.64 2.624 0.085 2.497-0.381 5.132-1.603 7.944 0.196-1.997 0.16-3.922-0.036-5.794-0.801 0.911-1.695 1.786-2.697 2.587-0.237 1.584-0.684 3.223-1.421 4.92 0.132-1.348 0.154-2.68 0.109-3.972-2.221 1.51-4.858 2.718-8.053 3.389 2.691-1.51 4.838-3.068 6.596-4.665-1.156-0.241-2.346-0.399-3.535-0.51 1.572-0.397 3.124-0.552 4.628-0.51 1.075-1.099 1.973-2.205 2.697-3.353-2.005-0.361-4.034-0.465-6.086-0.328 2.355-1.14 4.702-1.538 7.033-1.385 0.602-1.24 1.014-2.523 1.312-3.826-1.773-0.168-3.704 0.253-5.904 0.802 1.986-1.82 4.133-2.61 6.268-2.842 0.111-0.903 0.169-1.808 0.18-2.741-9.848-7.007-7.239 16.56-22.665 20.346 12.693 7.863 37.271-3.539 26.451-16.782zM25.788 1.846c0.628-0.305 1.39-0.323 1.968 0.219 0.33 3.103-0.68 9.663-4.665 14.249 3.039-5.538 3.261-9.548 2.697-14.467v-0z"></path></svg></div>
      <div class="leaf leaf-2"><svg viewBox="0 0 32 32" fill="currentColor"><path d="M28.082 9.534c-0.058 0.174-0.117 0.349-0.176 0.525 0.674 3.296 0.425 6.877-1.276 10.787 0.247-2.511 0.206-4.913-0.182-7.215-0.458 0.891-1.042 1.755-1.64 2.624 0.085 2.497-0.381 5.132-1.603 7.944 0.196-1.997 0.16-3.922-0.036-5.794-0.801 0.911-1.695 1.786-2.697 2.587-0.237 1.584-0.684 3.223-1.421 4.92 0.132-1.348 0.154-2.68 0.109-3.972-2.221 1.51-4.858 2.718-8.053 3.389 2.691-1.51 4.838-3.068 6.596-4.665-1.156-0.241-2.346-0.399-3.535-0.51 1.572-0.397 3.124-0.552 4.628-0.51 1.075-1.099 1.973-2.205 2.697-3.353-2.005-0.361-4.034-0.465-6.086-0.328 2.355-1.14 4.702-1.538 7.033-1.385 0.602-1.24 1.014-2.523 1.312-3.826-1.773-0.168-3.704 0.253-5.904 0.802 1.986-1.82 4.133-2.61 6.268-2.842 0.111-0.903 0.169-1.808 0.18-2.741-9.848-7.007-7.239 16.56-22.665 20.346 12.693 7.863 37.271-3.539 26.451-16.782zM25.788 1.846c0.628-0.305 1.39-0.323 1.968 0.219 0.33 3.103-0.68 9.663-4.665 14.249 3.039-5.538 3.261-9.548 2.697-14.467v-0z"></path></svg></div>
      <div class="leaf leaf-3"><svg viewBox="0 0 32 32" fill="currentColor"><path d="M28.082 9.534c-0.058 0.174-0.117 0.349-0.176 0.525 0.674 3.296 0.425 6.877-1.276 10.787 0.247-2.511 0.206-4.913-0.182-7.215-0.458 0.891-1.042 1.755-1.64 2.624 0.085 2.497-0.381 5.132-1.603 7.944 0.196-1.997 0.16-3.922-0.036-5.794-0.801 0.911-1.695 1.786-2.697 2.587-0.237 1.584-0.684 3.223-1.421 4.92 0.132-1.348 0.154-2.68 0.109-3.972-2.221 1.51-4.858 2.718-8.053 3.389 2.691-1.51 4.838-3.068 6.596-4.665-1.156-0.241-2.346-0.399-3.535-0.51 1.572-0.397 3.124-0.552 4.628-0.51 1.075-1.099 1.973-2.205 2.697-3.353-2.005-0.361-4.034-0.465-6.086-0.328 2.355-1.14 4.702-1.538 7.033-1.385 0.602-1.24 1.014-2.523 1.312-3.826-1.773-0.168-3.704 0.253-5.904 0.802 1.986-1.82 4.133-2.61 6.268-2.842 0.111-0.903 0.169-1.808 0.18-2.741-9.848-7.007-7.239 16.56-22.665 20.346 12.693 7.863 37.271-3.539 26.451-16.782zM25.788 1.846c0.628-0.305 1.39-0.323 1.968 0.219 0.33 3.103-0.68 9.663-4.665 14.249 3.039-5.538 3.261-9.548 2.697-14.467v-0z"></path></svg></div>
      <div class="leaf leaf-4"><svg viewBox="0 0 32 32" fill="currentColor"><path d="M28.082 9.534c-0.058 0.174-0.117 0.349-0.176 0.525 0.674 3.296 0.425 6.877-1.276 10.787 0.247-2.511 0.206-4.913-0.182-7.215-0.458 0.891-1.042 1.755-1.64 2.624 0.085 2.497-0.381 5.132-1.603 7.944 0.196-1.997 0.16-3.922-0.036-5.794-0.801 0.911-1.695 1.786-2.697 2.587-0.237 1.584-0.684 3.223-1.421 4.92 0.132-1.348 0.154-2.68 0.109-3.972-2.221 1.51-4.858 2.718-8.053 3.389 2.691-1.51 4.838-3.068 6.596-4.665-1.156-0.241-2.346-0.399-3.535-0.51 1.572-0.397 3.124-0.552 4.628-0.51 1.075-1.099 1.973-2.205 2.697-3.353-2.005-0.361-4.034-0.465-6.086-0.328 2.355-1.14 4.702-1.538 7.033-1.385 0.602-1.24 1.014-2.523 1.312-3.826-1.773-0.168-3.704 0.253-5.904 0.802 1.986-1.82 4.133-2.61 6.268-2.842 0.111-0.903 0.169-1.808 0.18-2.741-9.848-7.007-7.239 16.56-22.665 20.346 12.693 7.863 37.271-3.539 26.451-16.782zM25.788 1.846c0.628-0.305 1.39-0.323 1.968 0.219 0.33 3.103-0.68 9.663-4.665 14.249 3.039-5.538 3.261-9.548 2.697-14.467v-0z"></path></svg></div>
      <div class="leaf leaf-5"><svg viewBox="0 0 32 32" fill="currentColor"><path d="M28.082 9.534c-0.058 0.174-0.117 0.349-0.176 0.525 0.674 3.296 0.425 6.877-1.276 10.787 0.247-2.511 0.206-4.913-0.182-7.215-0.458 0.891-1.042 1.755-1.64 2.624 0.085 2.497-0.381 5.132-1.603 7.944 0.196-1.997 0.16-3.922-0.036-5.794-0.801 0.911-1.695 1.786-2.697 2.587-0.237 1.584-0.684 3.223-1.421 4.92 0.132-1.348 0.154-2.68 0.109-3.972-2.221 1.51-4.858 2.718-8.053 3.389 2.691-1.51 4.838-3.068 6.596-4.665-1.156-0.241-2.346-0.399-3.535-0.51 1.572-0.397 3.124-0.552 4.628-0.51 1.075-1.099 1.973-2.205 2.697-3.353-2.005-0.361-4.034-0.465-6.086-0.328 2.355-1.14 4.702-1.538 7.033-1.385 0.602-1.24 1.014-2.523 1.312-3.826-1.773-0.168-3.704 0.253-5.904 0.802 1.986-1.82 4.133-2.61 6.268-2.842 0.111-0.903 0.169-1.808 0.18-2.741-9.848-7.007-7.239 16.56-22.665 20.346 12.693 7.863 37.271-3.539 26.451-16.782zM25.788 1.846c0.628-0.305 1.39-0.323 1.968 0.219 0.33 3.103-0.68 9.663-4.665 14.249 3.039-5.538 3.261-9.548 2.697-14.467v-0z"></path></svg></div>
      <div class="leaf leaf-6"><svg viewBox="0 0 32 32" fill="currentColor"><path d="M28.082 9.534c-0.058 0.174-0.117 0.349-0.176 0.525 0.674 3.296 0.425 6.877-1.276 10.787 0.247-2.511 0.206-4.913-0.182-7.215-0.458 0.891-1.042 1.755-1.64 2.624 0.085 2.497-0.381 5.132-1.603 7.944 0.196-1.997 0.16-3.922-0.036-5.794-0.801 0.911-1.695 1.786-2.697 2.587-0.237 1.584-0.684 3.223-1.421 4.92 0.132-1.348 0.154-2.68 0.109-3.972-2.221 1.51-4.858 2.718-8.053 3.389 2.691-1.51 4.838-3.068 6.596-4.665-1.156-0.241-2.346-0.399-3.535-0.51 1.572-0.397 3.124-0.552 4.628-0.51 1.075-1.099 1.973-2.205 2.697-3.353-2.005-0.361-4.034-0.465-6.086-0.328 2.355-1.14 4.702-1.538 7.033-1.385 0.602-1.24 1.014-2.523 1.312-3.826-1.773-0.168-3.704 0.253-5.904 0.802 1.986-1.82 4.133-2.61 6.268-2.842 0.111-0.903 0.169-1.808 0.18-2.741-9.848-7.007-7.239 16.56-22.665 20.346 12.693 7.863 37.271-3.539 26.451-16.782zM25.788 1.846c0.628-0.305 1.39-0.323 1.968 0.219 0.33 3.103-0.68 9.663-4.665 14.249 3.039-5.538 3.261-9.548 2.697-14.467v-0z"></path></svg></div>
      <div class="leaf leaf-7"><svg viewBox="0 0 32 32" fill="currentColor"><path d="M28.082 9.534c-0.058 0.174-0.117 0.349-0.176 0.525 0.674 3.296 0.425 6.877-1.276 10.787 0.247-2.511 0.206-4.913-0.182-7.215-0.458 0.891-1.042 1.755-1.64 2.624 0.085 2.497-0.381 5.132-1.603 7.944 0.196-1.997 0.16-3.922-0.036-5.794-0.801 0.911-1.695 1.786-2.697 2.587-0.237 1.584-0.684 3.223-1.421 4.92 0.132-1.348 0.154-2.68 0.109-3.972-2.221 1.51-4.858 2.718-8.053 3.389 2.691-1.51 4.838-3.068 6.596-4.665-1.156-0.241-2.346-0.399-3.535-0.51 1.572-0.397 3.124-0.552 4.628-0.51 1.075-1.099 1.973-2.205 2.697-3.353-2.005-0.361-4.034-0.465-6.086-0.328 2.355-1.14 4.702-1.538 7.033-1.385 0.602-1.24 1.014-2.523 1.312-3.826-1.773-0.168-3.704 0.253-5.904 0.802 1.986-1.82 4.133-2.61 6.268-2.842 0.111-0.903 0.169-1.808 0.18-2.741-9.848-7.007-7.239 16.56-22.665 20.346 12.693 7.863 37.271-3.539 26.451-16.782zM25.788 1.846c0.628-0.305 1.39-0.323 1.968 0.219 0.33 3.103-0.68 9.663-4.665 14.249 3.039-5.538 3.261-9.548 2.697-14.467v-0z"></path></svg></div>
    </div>

    <div class="hero">
      <div class="hero-content">
        <h1 class="hero-title animate-in">Search.<br><span>Don't scroll.</span></h1>
        <p class="hero-subtitle animate-in">
          <strong>Milki</strong> — Arabic for <em>"Mine"</em>. A search-first Android launcher puts you in control. No ads. No tracking. No paywalls.
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
          <a href="https://github.com/milkilabs/launcher/releases/latest" class="btn btn-primary">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="margin-right:8px"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
            Install APK
          </a>
          <a href="/launcher/guide/overview" class="btn btn-secondary">Get Started →</a>
        </div>
      </div>
      <div class="hero-image-wrapper animate-in">
        <div class="mockup-glow"></div>
        <PhoneFrame>
          <img src="/images/home screen.jpg" alt="Milki Launcher Home Screen" class="hero-image" />
        </PhoneFrame>
      </div>
    </div>

    <!-- Why Switch Section -->
    <section class="why-switch">
      <div class="why-switch-inner">
        <h2 class="section-title animate-in">Why switch to <span class="highlight">Milki</span>?</h2>
        <div class="switch-columns">
          <div class="switch-column old-way animate-in">
            <h3>The old way</h3>
            <ul>
              <li>Scroll through apps</li>
              <li>Open folders</li>
              <li>Forget where things are</li>
            </ul>
          </div>
          <div class="switch-column milki-way animate-in">
            <h3>With Milki</h3>
            <ul>
              <li>Tap → type → done</li>
              <li>Everything searchable</li>
              <li>Muscle memory via prefixes</li>
            </ul>
          </div>
        </div>
      </div>
    </section>

    <!-- Features Section -->
    <section class="features">
      <div class="features-inner">
        <h2 class="section-title animate-in">Designed for <span class="highlight">flow</span>.</h2>
        <div class="features-grid">
          <div class="animate-in">
            <div class="feature-card">
              <div class="icon-box">🔍</div>
              <h3>Search Driven</h3>
              <p>Tap the home button to summon a powerful search dialog. Find recent apps, search the web, or locate anything without scrolling through drawers.</p>
            </div>
          </div>
          <div class="animate-in">
            <div class="feature-card">
              <div class="icon-box">⚡</div>
              <h3>Action Prefixes</h3>
              <p>Type <kbd>f</kbd> for files, <kbd>c</kbd> for contacts, <kbd>yt</kbd> for YouTube — or define your own custom prefixes for any action.</p>
            </div>
          </div>
          <div class="animate-in">
            <div class="feature-card">
              <div class="icon-box">🎨</div>
              <h3>Tailored Homescreen</h3>
              <p>Widgets, app shortcuts, files, or contacts as icons. Build a homescreen that matches how <em>you</em> actually use your phone.</p>
            </div>
          </div>
          <div class="animate-in">
            <div class="feature-card">
              <div class="icon-box">🛡️</div>
              <h3>100% Free & Private</h3>
              <p>Fully open source. Zero ads, zero telemetry, every feature completely free. No paywalls, ever. Your data stays on your device.</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Showcase Section -->
    <section class="showcase">
      <div class="showcase-content">
        <h2 class="section-title animate-in">See it in <span class="highlight">action</span>.</h2>
        <div class="showcase-slider animate-in" @mouseenter="stopInterval" @mouseleave="startInterval">
          <PhoneFrame class="showcase-phone">
            <transition name="fade">
              <img :key="currentImage" :src="images[currentImage].src" :alt="images[currentImage].title" class="slider-image" />
            </transition>
          </PhoneFrame>
          <div class="slider-info">
            <div 
              v-for="(img, idx) in images" 
              :key="idx" 
              class="slider-bullet"
              :class="{ active: currentImage === idx }"
              @mouseenter="handleHover(idx)"
            >
              <div class="bullet-icon">{{ img.icon }}</div>
              <div class="bullet-text">
                <h4>{{ img.title }}</h4>
                <p>{{ img.desc }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:ital,opsz,wght@0,9..40,300;0,9..40,400;0,9..40,500;0,9..40,600;0,9..40,700;1,9..40,400&family=Playfair+Display:wght@600;700;800&display=swap');

.milki-home {
  font-family: 'DM Sans', sans-serif;
  color: var(--vp-c-text-1);
  overflow-x: hidden;
  position: relative;

  /* — Fresh green palette — */
  --green-50: #f0fdf4;
  --green-100: #dcfce7;
  --green-200: #bbf7d0;
  --green-300: #86efac;
  --green-400: #4ade80;
  --green-500: #22c55e;
  --green-600: #16a34a;
  --green-700: #15803d;
  --green-800: #166534;

  --primary: #22c55e;
  --primary-light: #4ade80;
  --primary-dark: #16a34a;
  --primary-glow: rgba(34, 197, 94, 0.35);
  --primary-subtle: rgba(34, 197, 94, 0.08);

  --cream: #fefdf8;
  --cream-warm: #faf7f0;
  --sand: #f5f0e8;

  --card-bg: var(--vp-c-bg-soft);
  --border: var(--vp-c-divider);
  --leaf-color: var(--green-300);
}

/* ——— Background layers ——— */
.bg-layer {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
  background: var(--vp-c-bg); /* Enforces background depth for noise filter overlay */
}

.bg-noise {
  position: absolute;
  inset: 0;
  opacity: 0.04;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E");
  pointer-events: none;
}

.bg-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
}

.blob-1 {
  width: 600px;
  height: 600px;
  top: -15%;
  left: -8%;
  background: radial-gradient(circle, rgba(34,197,94,0.14) 0%, transparent 60%);
  animation: blobDrift 22s ease-in-out infinite alternate;
}

.blob-2 {
  width: 500px;
  height: 500px;
  bottom: 0%;
  right: -5%;
  background: radial-gradient(circle, rgba(52,211,153,0.12) 0%, transparent 60%);
  animation: blobDrift 18s 4s ease-in-out infinite alternate-reverse;
}

.blob-3 {
  width: 450px;
  height: 450px;
  top: 40%;
  left: 30%;
  background: radial-gradient(circle, rgba(16,185,129,0.08) 0%, transparent 60%);
  animation: blobDrift 25s 8s ease-in-out infinite alternate;
}

.blob-4 {
  width: 550px;
  height: 550px;
  bottom: -20%;
  left: -10%;
  background: radial-gradient(circle, rgba(132,204,22,0.1) 0%, transparent 60%);
  animation: blobDrift 20s 2s ease-in-out infinite alternate;
}

@keyframes blobDrift {
  0% { transform: translate(0, 0) scale(1) rotate(0deg); }
  50% { transform: translate(40px, -30px) scale(1.05) rotate(10deg); }
  100% { transform: translate(-20px, 20px) scale(0.95) rotate(-10deg); }
}

.leaf {
  position: absolute;
  filter: drop-shadow(0 8px 12px rgba(34,197,94,0.25));
  will-change: transform;
}

.leaf svg {
  width: 100%;
  height: 100%;
}

/* Individual leaf placements and properties */
.leaf-1 { width: 55px; height: 55px; top: 12%; left: 8%; animation: float1 16s ease-in-out infinite alternate; opacity: 0.4; color: var(--green-500); }
.leaf-2 { width: 90px; height: 90px; top: 20%; right: 10%; animation: float2 20s ease-in-out infinite alternate; opacity: 0.25; color: var(--green-400); }
.leaf-3 { width: 40px; height: 40px; top: 75%; left: 16%; animation: float3 14s ease-in-out infinite alternate; opacity: 0.45; color: var(--green-600); }
.leaf-4 { width: 65px; height: 65px; top: 55%; right: 6%; animation: float1 18s ease-in-out infinite alternate-reverse; opacity: 0.2; color: var(--green-500); }
.leaf-5 { width: 45px; height: 45px; top: 40%; left: 45%; animation: float2 24s ease-in-out infinite alternate; opacity: 0.3; color: var(--green-600); filter: blur(2px) drop-shadow(0 8px 12px rgba(34,197,94,0.15)); }
.leaf-6 { width: 75px; height: 75px; top: 80%; right: 42%; animation: float3 17s ease-in-out infinite alternate-reverse; opacity: 0.3; color: var(--green-400); }
.leaf-7 { width: 35px; height: 35px; top: 5%; left: 60%; animation: float1 15s ease-in-out infinite alternate; opacity: 0.35; color: var(--green-500); }

/* Animation Keyframes for independent leaf movement */
@keyframes float1 {
  0%, 100% { transform: translate(0, 0) rotate(0deg); }
  50% { transform: translate(40px, -50px) rotate(45deg); }
}

@keyframes float2 {
  0%, 100% { transform: translate(0, 0) rotate(15deg) scale(1); }
  50% { transform: translate(-50px, 60px) rotate(-25deg) scale(1.1); }
}

@keyframes float3 {
  0%, 100% { transform: translate(0, 0) rotate(-15deg); }
  50% { transform: translate(60px, 40px) rotate(35deg); }
}
/* ——— Entrance animations ——— */
.animate-in {
  opacity: 0;
  transform: translateY(28px);
  transition: opacity 0.7s cubic-bezier(0.16, 1, 0.3, 1), transform 0.7s cubic-bezier(0.16, 1, 0.3, 1);
}

.animate-in.visible {
  opacity: 1;
  transform: translateY(0);
}

.features-grid .animate-in:nth-child(1) { transition-delay: 0ms; }
.features-grid .animate-in:nth-child(2) { transition-delay: 100ms; }
.features-grid .animate-in:nth-child(3) { transition-delay: 200ms; }
.features-grid .animate-in:nth-child(4) { transition-delay: 300ms; }

/* ——— Highlight text ——— */
.highlight {
  font-family: 'Playfair Display', serif;
  font-style: italic;
  background: linear-gradient(135deg, var(--green-500) 0%, var(--green-300) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

/* ——— Keyframes ——— */
@keyframes float {
  0% { transform: translateY(0px); }
  50% { transform: translateY(-10px); }
  100% { transform: translateY(0px); }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

@keyframes shimmer {
  0% { background-position: -200% center; }
  100% { background-position: 200% center; }
}


@keyframes pulse-dot {
  0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgba(34,197,94,0.5); }
  50% { opacity: 0.8; box-shadow: 0 0 0 6px rgba(34,197,94,0); }
}

/* ——— Hero Section ——— */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1200px;
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

.hero-title {
  font-family: 'DM Sans', sans-serif;
  font-size: 4rem;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -1.5px;
  margin-bottom: 1.25rem;
  color: var(--vp-c-text-1);
}

.hero-title span {
  font-family: 'Playfair Display', serif;
  font-weight: 800;
  font-style: italic;
  background: linear-gradient(135deg, var(--green-600) 20%, var(--green-400) 80%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-subtitle {
  font-size: 1.12rem;
  line-height: 1.75;
  color: var(--vp-c-text-2);
  margin-bottom: 2rem;
}

.hero-subtitle strong {
  color: var(--green-600);
}

/* ——— Search bar demo ——— */
.search-demo {
  margin-bottom: 2.5rem;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  border-radius: 16px;
  background: var(--vp-c-bg-soft);
  border: 1.5px solid var(--border);
  font-size: 1.05rem;
  color: var(--vp-c-text-1);
  transition: border-color 0.3s ease, box-shadow 0.3s ease;
  max-width: 420px;
  backdrop-filter: blur(10px);
}

.search-bar:hover {
  border-color: var(--green-400);
  box-shadow: 0 0 0 4px rgba(34,197,94,0.08), 0 4px 20px rgba(34,197,94,0.06);
}

.search-icon {
  color: var(--vp-c-text-3);
  flex-shrink: 0;
}

.search-text {
  white-space: nowrap;
  overflow: hidden;
}

.search-prefix {
  color: var(--green-500);
  font-weight: 600;
}

.cursor-blink {
  animation: blink 1s step-end infinite;
  color: var(--green-500);
  font-weight: 300;
  margin-left: -2px;
}

.search-hint {
  display: block;
  font-size: 0.8rem;
  color: var(--vp-c-text-3);
  margin-top: 8px;
  padding-left: 4px;
  transition: color 0.2s ease;
}

/* ——— Buttons ——— */
.hero-actions {
  display: flex;
  gap: 1rem;
}

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
  font-family: 'DM Sans', sans-serif;
}

.btn-primary {
  background: linear-gradient(135deg, var(--green-500), var(--green-600));
  color: #fff;
  box-shadow: 0 4px 16px rgba(34,197,94,0.3), 0 1px 3px rgba(0,0,0,0.08);
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 28px rgba(34,197,94,0.35), 0 2px 6px rgba(0,0,0,0.1);
  background: linear-gradient(135deg, var(--green-400), var(--green-500));
}

.btn-secondary {
  background-color: var(--card-bg);
  color: var(--vp-c-text-1);
  border: 1.5px solid var(--border);
}

.btn-secondary:hover {
  border-color: var(--green-400);
  background: var(--primary-subtle);
  transform: translateY(-2px);
}

/* ——— Phone mockup ——— */
.hero-image-wrapper {
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
  background: linear-gradient(180deg, var(--green-400), var(--green-300));
  filter: blur(70px);
  opacity: 0.2;
  z-index: -1;
  animation: breathe 6s ease-in-out infinite;
}

@keyframes breathe {
  0%, 100% { opacity: 0.18; transform: scale(1); }
  50% { opacity: 0.28; transform: scale(1.04); }
}

.hero-image {
  display: block;
  max-width: 280px;
}

/* ——— Features Section ——— */
.features {
  position: relative;
  z-index: 1;
  padding: 6rem 2rem;
  text-align: center;
}

.features::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, 
    transparent 0%, 
    rgba(34,197,94,0.03) 30%, 
    rgba(34,197,94,0.06) 60%, 
    transparent 100%
  );
  pointer-events: none;
}

.features-inner {
  position: relative;
}

.section-title {
  font-family: 'DM Sans', sans-serif;
  font-size: 2.5rem;
  font-weight: 700;
  margin-bottom: 3rem;
  color: var(--vp-c-text-1);
  letter-spacing: -0.5px;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 1.25rem;
  max-width: 1100px;
  margin: 0 auto;
}

.feature-card {
  height: 100%;
  background: var(--card-bg);
  padding: 2rem 1.5rem;
  border-radius: 20px;
  border: 1px solid var(--border);
  text-align: left;
  transition: transform 0.15s ease-out, box-shadow 0.15s ease-out, border-color 0.15s ease-out;
  position: relative;
  overflow: hidden;
}

.feature-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.04);
  border-color: var(--green-200);
}

.icon-box {
  font-size: 2.2rem;
  margin-bottom: 1rem;
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: var(--green-50);
  border: 1px solid var(--green-100);
}

.feature-card h3 {
  font-size: 1.2rem;
  font-weight: 700;
  margin-bottom: 0.7rem;
  color: var(--vp-c-text-1);
}

.feature-card p {
  color: var(--vp-c-text-2);
  line-height: 1.6;
  font-size: 0.92rem;
}

kbd {
  background: var(--green-50);
  border-radius: 6px;
  padding: 2px 7px;
  font-family: 'DM Sans', monospace;
  font-size: 0.85em;
  font-weight: 600;
  border: 1px solid var(--green-200);
  color: var(--green-600);
}

/* ——— Showcase Section ——— */
.showcase {
  padding: 6rem 2rem 8rem;
  max-width: 1200px;
  margin: 0 auto;
  text-align: center;
  position: relative;
  z-index: 1;
}

.showcase-slider {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 3rem;
  margin-top: 2.5rem;
}

.showcase-phone {
  width: 280px;
  height: 580px;
}

.slider-image {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* Vue Transitions */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease-in-out;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slider-info {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  text-align: left;
}

.slider-bullet {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.15rem 1.5rem;
  border-radius: 16px;
  cursor: pointer;
  border: 1.5px solid transparent;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  background: var(--card-bg);
}

.slider-bullet:hover {
  background: var(--vp-c-bg-mute);
}

.slider-bullet.active {
  border-color: var(--green-400);
  background: rgba(34,197,94,0.06);
  box-shadow: 0 0 0 3px rgba(34,197,94,0.06);
}

.bullet-icon {
  font-size: 1.5rem;
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: var(--green-50);
  border: 1px solid var(--green-100);
}

.bullet-text h4 {
  font-size: 1.05rem;
  font-weight: 600;
  color: var(--vp-c-text-1);
  margin-bottom: 2px;
}

.bullet-text p {
  color: var(--vp-c-text-2);
  font-size: 0.86rem;
}

/* ——— Responsive ——— */
@media (max-width: 960px) {
  .hero {
    flex-direction: column;
    text-align: center;
    padding: 5rem 1.25rem 3rem;
    gap: 3rem;
  }
  .hero-title {
    font-size: 2.8rem;
  }
  .hero-actions {
    justify-content: center;
    gap: 0.75rem;
  }
  .btn {
    padding: 0.65rem 1.25rem;
    font-size: 0.85rem;
    border-radius: 10px;
  }
  .btn-primary svg {
    width: 16px;
    height: 16px;
  }
  .search-bar {
    margin: 0 auto;
  }
  .search-hint {
    text-align: center;
  }
  .showcase-slider {
    flex-direction: column;
  }
  .feature-card {
    text-align: center;
  }
  .icon-box {
    margin-left: auto;
    margin-right: auto;
  }
  .leaf-pattern {
    opacity: 0.3;
  }
}
</style>
