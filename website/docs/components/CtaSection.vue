<template>
  <section class="cta">
    <div class="cta-inner animate-in">
      <div class="cta-glow"></div>
      <h2>Ready to take control?</h2>
      <p>
        Milki is free, open source, and built for people who value their time and privacy.
        Try it — you'll wonder how you ever used anything else.
      </p>
      <div class="cta-actions">
        <a href="https://github.com/milkilabs/launcher/releases/latest/download/app-release.apk" class="btn btn-primary" id="cta-download">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
          Download APK
        </a>
        <a href="https://github.com/milkilabs/launcher" target="_blank" rel="noopener" class="btn btn-ghost" id="cta-github">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 0 0-.94-2.61c3.14-.35 6.44-1.54 6.44-7A5.44 5.44 0 0 0 20 4.77 5.07 5.07 0 0 0 19.91 1S18.73.65 16 2.48a13.38 13.38 0 0 0-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 0 0 5 4.77a5.44 5.44 0 0 0-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 0 0 9 18.13V22"></path></svg>
          View on GitHub
        </a>
      </div>
      <p class="cta-note">Available as APK · F-Droid coming soon</p>
    </div>
  </section>
</template>

<style scoped>
@import './home-vars.css';

.cta {
  position: relative;
  z-index: 1;
  padding: var(--h-section-pad);
  padding-bottom: 8rem;
}

.cta-inner {
  position: relative;
  max-width: 680px;
  margin: 0 auto;
  text-align: center;
  padding: 4rem 3rem;
  border-radius: 28px;
  background: var(--h-card-bg);
  border: 1px solid var(--h-border);
  overflow: hidden;
}

.cta-glow {
  position: absolute;
  top: -60%;
  left: 50%;
  transform: translateX(-50%);
  width: 400px;
  height: 400px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(34,197,94,0.12) 0%, transparent 70%);
  pointer-events: none;
}

.cta-inner h2 {
  font-family: var(--h-font-body);
  font-size: 2.2rem;
  font-weight: 700;
  color: var(--vp-c-text-1);
  margin-bottom: 1rem;
  letter-spacing: -0.5px;
  position: relative;
}

.cta-inner > p {
  font-family: var(--h-font-body);
  color: var(--vp-c-text-2);
  font-size: 1.05rem;
  line-height: 1.7;
  margin-bottom: 2rem;
  position: relative;
}

.cta-actions {
  display: flex;
  gap: 1rem;
  justify-content: center;
  position: relative;
  flex-wrap: wrap;
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

.btn-ghost {
  color: var(--vp-c-text-1);
  border: 1.5px solid var(--h-border);
  background: transparent;
}

.btn-ghost:hover {
  border-color: var(--h-green-400);
  background: var(--h-primary-subtle);
  transform: translateY(-2px);
}

.cta-note {
  font-family: var(--h-font-body);
  font-size: 0.8rem;
  color: var(--vp-c-text-3);
  margin-top: 1.25rem;
  position: relative;
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
  .cta-inner { padding: 3rem 1.5rem; }
  .cta-inner h2 { font-size: 1.75rem; }
}
</style>
