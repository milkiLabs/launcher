<script setup>
import { onMounted, onUnmounted } from 'vue'
import HeroSection from './HeroSection.vue'
import TrustBar from './TrustBar.vue'
import FeatureShowcase from './FeatureShowcase.vue'
import ComparisonSection from './ComparisonSection.vue'
import FeatureGrid from './FeatureGrid.vue'
import CtaSection from './CtaSection.vue'

// Scroll-based entrance animations — shared across all child components
const observeElements = () => {
  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible')
      }
    })
  }, { threshold: 0.15 })

  document.querySelectorAll('.animate-in').forEach((el) => {
    observer.observe(el)
  })
}

onMounted(() => {
  setTimeout(observeElements, 100)
})
</script>

<template>
  <div class="milki-home">
    <!-- Organic background layers -->
    <div class="bg-layer">
      <div class="bg-noise"></div>
      <div class="bg-blob blob-1"></div>
      <div class="bg-blob blob-2"></div>
      <div class="bg-blob blob-3"></div>

      <!-- Floating leaves -->
      <div class="leaf leaf-1"><svg viewBox="0 0 32 32" fill="currentColor"><path d="M28.082 9.534c-0.058 0.174-0.117 0.349-0.176 0.525 0.674 3.296 0.425 6.877-1.276 10.787 0.247-2.511 0.206-4.913-0.182-7.215-0.458 0.891-1.042 1.755-1.64 2.624 0.085 2.497-0.381 5.132-1.603 7.944 0.196-1.997 0.16-3.922-0.036-5.794-0.801 0.911-1.695 1.786-2.697 2.587-0.237 1.584-0.684 3.223-1.421 4.92 0.132-1.348 0.154-2.68 0.109-3.972-2.221 1.51-4.858 2.718-8.053 3.389 2.691-1.51 4.838-3.068 6.596-4.665-1.156-0.241-2.346-0.399-3.535-0.51 1.572-0.397 3.124-0.552 4.628-0.51 1.075-1.099 1.973-2.205 2.697-3.353-2.005-0.361-4.034-0.465-6.086-0.328 2.355-1.14 4.702-1.538 7.033-1.385 0.602-1.24 1.014-2.523 1.312-3.826-1.773-0.168-3.704 0.253-5.904 0.802 1.986-1.82 4.133-2.61 6.268-2.842 0.111-0.903 0.169-1.808 0.18-2.741-9.848-7.007-7.239 16.56-22.665 20.346 12.693 7.863 37.271-3.539 26.451-16.782zM25.788 1.846c0.628-0.305 1.39-0.323 1.968 0.219 0.33 3.103-0.68 9.663-4.665 14.249 3.039-5.538 3.261-9.548 2.697-14.467v-0z"></path></svg></div>
      <div class="leaf leaf-2"><svg viewBox="0 0 32 32" fill="currentColor"><path d="M28.082 9.534c-0.058 0.174-0.117 0.349-0.176 0.525 0.674 3.296 0.425 6.877-1.276 10.787 0.247-2.511 0.206-4.913-0.182-7.215-0.458 0.891-1.042 1.755-1.64 2.624 0.085 2.497-0.381 5.132-1.603 7.944 0.196-1.997 0.16-3.922-0.036-5.794-0.801 0.911-1.695 1.786-2.697 2.587-0.237 1.584-0.684 3.223-1.421 4.92 0.132-1.348 0.154-2.68 0.109-3.972-2.221 1.51-4.858 2.718-8.053 3.389 2.691-1.51 4.838-3.068 6.596-4.665-1.156-0.241-2.346-0.399-3.535-0.51 1.572-0.397 3.124-0.552 4.628-0.51 1.075-1.099 1.973-2.205 2.697-3.353-2.005-0.361-4.034-0.465-6.086-0.328 2.355-1.14 4.702-1.538 7.033-1.385 0.602-1.24 1.014-2.523 1.312-3.826-1.773-0.168-3.704 0.253-5.904 0.802 1.986-1.82 4.133-2.61 6.268-2.842 0.111-0.903 0.169-1.808 0.18-2.741-9.848-7.007-7.239 16.56-22.665 20.346 12.693 7.863 37.271-3.539 26.451-16.782zM25.788 1.846c0.628-0.305 1.39-0.323 1.968 0.219 0.33 3.103-0.68 9.663-4.665 14.249 3.039-5.538 3.261-9.548 2.697-14.467v-0z"></path></svg></div>
      <div class="leaf leaf-3"><svg viewBox="0 0 32 32" fill="currentColor"><path d="M28.082 9.534c-0.058 0.174-0.117 0.349-0.176 0.525 0.674 3.296 0.425 6.877-1.276 10.787 0.247-2.511 0.206-4.913-0.182-7.215-0.458 0.891-1.042 1.755-1.64 2.624 0.085 2.497-0.381 5.132-1.603 7.944 0.196-1.997 0.16-3.922-0.036-5.794-0.801 0.911-1.695 1.786-2.697 2.587-0.237 1.584-0.684 3.223-1.421 4.92 0.132-1.348 0.154-2.68 0.109-3.972-2.221 1.51-4.858 2.718-8.053 3.389 2.691-1.51 4.838-3.068 6.596-4.665-1.156-0.241-2.346-0.399-3.535-0.51 1.572-0.397 3.124-0.552 4.628-0.51 1.075-1.099 1.973-2.205 2.697-3.353-2.005-0.361-4.034-0.465-6.086-0.328 2.355-1.14 4.702-1.538 7.033-1.385 0.602-1.24 1.014-2.523 1.312-3.826-1.773-0.168-3.704 0.253-5.904 0.802 1.986-1.82 4.133-2.61 6.268-2.842 0.111-0.903 0.169-1.808 0.18-2.741-9.848-7.007-7.239 16.56-22.665 20.346 12.693 7.863 37.271-3.539 26.451-16.782zM25.788 1.846c0.628-0.305 1.39-0.323 1.968 0.219 0.33 3.103-0.68 9.663-4.665 14.249 3.039-5.538 3.261-9.548 2.697-14.467v-0z"></path></svg></div>
      <div class="leaf leaf-4"><svg viewBox="0 0 32 32" fill="currentColor"><path d="M28.082 9.534c-0.058 0.174-0.117 0.349-0.176 0.525 0.674 3.296 0.425 6.877-1.276 10.787 0.247-2.511 0.206-4.913-0.182-7.215-0.458 0.891-1.042 1.755-1.64 2.624 0.085 2.497-0.381 5.132-1.603 7.944 0.196-1.997 0.16-3.922-0.036-5.794-0.801 0.911-1.695 1.786-2.697 2.587-0.237 1.584-0.684 3.223-1.421 4.92 0.132-1.348 0.154-2.68 0.109-3.972-2.221 1.51-4.858 2.718-8.053 3.389 2.691-1.51 4.838-3.068 6.596-4.665-1.156-0.241-2.346-0.399-3.535-0.51 1.572-0.397 3.124-0.552 4.628-0.51 1.075-1.099 1.973-2.205 2.697-3.353-2.005-0.361-4.034-0.465-6.086-0.328 2.355-1.14 4.702-1.538 7.033-1.385 0.602-1.24 1.014-2.523 1.312-3.826-1.773-0.168-3.704 0.253-5.904 0.802 1.986-1.82 4.133-2.61 6.268-2.842 0.111-0.903 0.169-1.808 0.18-2.741-9.848-7.007-7.239 16.56-22.665 20.346 12.693 7.863 37.271-3.539 26.451-16.782zM25.788 1.846c0.628-0.305 1.39-0.323 1.968 0.219 0.33 3.103-0.68 9.663-4.665 14.249 3.039-5.538 3.261-9.548 2.697-14.467v-0z"></path></svg></div>
      <div class="leaf leaf-5"><svg viewBox="0 0 32 32" fill="currentColor"><path d="M28.082 9.534c-0.058 0.174-0.117 0.349-0.176 0.525 0.674 3.296 0.425 6.877-1.276 10.787 0.247-2.511 0.206-4.913-0.182-7.215-0.458 0.891-1.042 1.755-1.64 2.624 0.085 2.497-0.381 5.132-1.603 7.944 0.196-1.997 0.16-3.922-0.036-5.794-0.801 0.911-1.695 1.786-2.697 2.587-0.237 1.584-0.684 3.223-1.421 4.92 0.132-1.348 0.154-2.68 0.109-3.972-2.221 1.51-4.858 2.718-8.053 3.389 2.691-1.51 4.838-3.068 6.596-4.665-1.156-0.241-2.346-0.399-3.535-0.51 1.572-0.397 3.124-0.552 4.628-0.51 1.075-1.099 1.973-2.205 2.697-3.353-2.005-0.361-4.034-0.465-6.086-0.328 2.355-1.14 4.702-1.538 7.033-1.385 0.602-1.24 1.014-2.523 1.312-3.826-1.773-0.168-3.704 0.253-5.904 0.802 1.986-1.82 4.133-2.61 6.268-2.842 0.111-0.903 0.169-1.808 0.18-2.741-9.848-7.007-7.239 16.56-22.665 20.346 12.693 7.863 37.271-3.539 26.451-16.782zM25.788 1.846c0.628-0.305 1.39-0.323 1.968 0.219 0.33 3.103-0.68 9.663-4.665 14.249 3.039-5.538 3.261-9.548 2.697-14.467v-0z"></path></svg></div>
    </div>

    <HeroSection />
    <TrustBar />
    <!-- <ComparisonSection /> -->
    <FeatureShowcase />
    <FeatureGrid />
    <CtaSection />
  </div>
</template>

<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:ital,opsz,wght@0,9..40,300;0,9..40,400;0,9..40,500;0,9..40,600;0,9..40,700;1,9..40,400&family=Playfair+Display:wght@600;700;800&display=swap');
@import './home-vars.css';

.milki-home {
  font-family: var(--h-font-body);
  color: var(--vp-c-text-1);
  overflow-x: hidden;
  position: relative;
}

/* ——— Background layers ——— */
.bg-layer {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
  background: var(--vp-c-bg);
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
  width: 600px; height: 600px;
  top: -15%; left: -8%;
  background: radial-gradient(circle, rgba(34,197,94,0.14) 0%, transparent 60%);
  animation: blobDrift 22s ease-in-out infinite alternate;
}

.blob-2 {
  width: 500px; height: 500px;
  bottom: 0%; right: -5%;
  background: radial-gradient(circle, rgba(52,211,153,0.12) 0%, transparent 60%);
  animation: blobDrift 18s 4s ease-in-out infinite alternate-reverse;
}

.blob-3 {
  width: 450px; height: 450px;
  top: 40%; left: 30%;
  background: radial-gradient(circle, rgba(16,185,129,0.08) 0%, transparent 60%);
  animation: blobDrift 25s 8s ease-in-out infinite alternate;
}

@keyframes blobDrift {
  0% { transform: translate(0, 0) scale(1) rotate(0deg); }
  50% { transform: translate(40px, -30px) scale(1.05) rotate(10deg); }
  100% { transform: translate(-20px, 20px) scale(0.95) rotate(-10deg); }
}

/* ——— Floating leaves ——— */
.leaf {
  position: absolute;
  filter: drop-shadow(0 8px 12px rgba(34,197,94,0.25));
  will-change: transform;
}

.leaf svg { width: 100%; height: 100%; }

.leaf-1 { width: 55px; height: 55px; top: 12%; left: 8%; animation: float1 16s ease-in-out infinite alternate; opacity: 0.4; color: #22c55e; }
.leaf-2 { width: 80px; height: 80px; top: 22%; right: 10%; animation: float2 20s ease-in-out infinite alternate; opacity: 0.2; color: #4ade80; }
.leaf-3 { width: 40px; height: 40px; top: 70%; left: 16%; animation: float3 14s ease-in-out infinite alternate; opacity: 0.4; color: #16a34a; }
.leaf-4 { width: 60px; height: 60px; top: 55%; right: 6%; animation: float1 18s ease-in-out infinite alternate-reverse; opacity: 0.2; color: #22c55e; }
.leaf-5 { width: 35px; height: 35px; top: 5%; left: 55%; animation: float2 15s ease-in-out infinite alternate; opacity: 0.3; color: #22c55e; }

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
</style>
