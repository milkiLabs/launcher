<template>
  <section class="comparison">
    <div class="comparison-inner">
      <h2 class="section-title animate-in">Stop <span class="highlight">scrolling</span>. Start finding.</h2>

      <div class="compare-grid animate-in">
        <div class="compare-col old-way">
          <div class="compare-header">
            <span class="compare-icon">😩</span>
            <h3>Other launchers</h3>
          </div>
          <ul>
            <li>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              Scroll through pages of apps
            </li>
            <li>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              Dig into nested folders
            </li>
            <li>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              Switch apps to search files or contacts
            </li>
            <li>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              Ads and telemetry baked in
            </li>
            <li>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              Premium features behind paywalls
            </li>
          </ul>
        </div>

        <div class="compare-col milki-way">
          <div class="compare-header">
            <span class="compare-icon">✨</span>
            <h3>With Milki</h3>
          </div>
          <ul>
            <li>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
              Tap → type → launch in under a second
            </li>
            <li>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
              Prefixes build muscle memory fast
            </li>
            <li>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
              Apps, files, contacts — one search bar
            </li>
            <li>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
              Zero tracking, zero ads, zero telemetry
            </li>
            <li>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
              Every feature free — open source forever
            </li>
          </ul>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
@import './home-vars.css';

.comparison {
  position: relative;
  z-index: 1;
  padding: var(--h-section-pad);
}

.comparison::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent 0%, rgba(34,197,94,0.03) 40%, rgba(34,197,94,0.05) 60%, transparent 100%);
  pointer-events: none;
}

.comparison-inner {
  position: relative;
  max-width: 900px;
  margin: 0 auto;
}

.section-title {
  font-family: var(--h-font-body);
  font-size: 2.5rem;
  font-weight: 700;
  margin-bottom: 3rem;
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

.compare-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.5rem;
}

.compare-col {
  padding: 2rem 1.75rem;
  border-radius: 20px;
  border: 1px solid var(--h-border);
  background: var(--h-card-bg);
}

.compare-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 1.5rem;
}

.compare-icon {
  font-size: 1.5rem;
}

.compare-header h3 {
  font-family: var(--h-font-body);
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--vp-c-text-1);
}

.compare-col ul {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.compare-col li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-family: var(--h-font-body);
  font-size: 0.92rem;
  line-height: 1.5;
  color: var(--vp-c-text-2);
}

.compare-col li svg {
  flex-shrink: 0;
  margin-top: 2px;
}

/* Old way styling */
.old-way {
  opacity: 0.75;
}

.old-way li svg {
  color: #ef4444;
}

/* Milki way styling */
.milki-way {
  border-color: rgba(34,197,94,0.2);
  background: linear-gradient(135deg, rgba(34,197,94,0.04) 0%, rgba(34,197,94,0.01) 100%);
}

.milki-way li svg {
  color: var(--h-green-500);
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
  .compare-grid { grid-template-columns: 1fr; }
  .section-title { font-size: 2rem; }
}
</style>
