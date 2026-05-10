<template>
  <div class="trust-bar animate-in">
    <div class="trust-inner">
      <a href="https://github.com/milkilabs/launcher" target="_blank" rel="noopener" class="trust-item" id="trust-github">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 0 0-.94-2.61c3.14-.35 6.44-1.54 6.44-7A5.44 5.44 0 0 0 20 4.77 5.07 5.07 0 0 0 19.91 1S18.73.65 16 2.48a13.38 13.38 0 0 0-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 0 0 5 4.77a5.44 5.44 0 0 0-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 0 0 9 18.13V22"></path></svg>
        <span>Open Source on GitHub</span>
      </a>
      <div class="trust-divider"></div>
      <div class="trust-item">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
        <span>Zero tracking</span>
      </div>
      <div class="trust-divider"></div>
      <div class="trust-item">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="m9 12 2 2 4-4"/></svg>
        <span>100% free, forever</span>
      </div>
      <div class="trust-divider"></div>
      <div class="trust-item">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
        <span>Data stays on device</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
@import './home-vars.css';

.trust-bar {
  position: relative;
  z-index: 1;
  padding: 0 2rem;
}

.trust-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1.5rem;
  max-width: var(--h-max-width);
  margin: 0 auto;
  padding: 1.5rem 2rem;
  border-radius: 20px;
  background: var(--h-card-bg);
  border: 1px solid var(--h-border);
  flex-wrap: wrap;
}

.trust-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--h-font-body);
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--vp-c-text-2);
  text-decoration: none;
  transition: color 0.2s ease;
  white-space: nowrap;
}

a.trust-item:hover {
  color: var(--h-green-500);
}

.trust-item svg {
  color: var(--h-green-500);
  flex-shrink: 0;
}

.trust-divider {
  width: 1px;
  height: 20px;
  background: var(--h-border);
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

@media (max-width: 768px) {
  .trust-inner { gap: 1rem; padding: 1.25rem 1.25rem; }
  .trust-divider { display: none; }
  .trust-item { font-size: 0.8rem; }
}
</style>
