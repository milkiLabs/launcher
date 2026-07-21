<template>
  <section class="one-page">
    <div class="one-page-inner">
      <div class="one-page-copy animate-in">
        <span class="section-kicker">One home</span>
        <h2>One page is all you need.</h2>
        <p>
          Milki does not ask you to maintain a stack of launcher pages. Your home
          screen is a single intentional workspace: the things you open often,
          the groups you rely on, and the widgets that deserve space all live in
          one place.
        </p>
        <p>
          Keep the surface calm. Put related apps in folders, pin the essentials,
          and choose how widgets behave: inline when you want live information on
          the grid, or popup when you want the widget one tap away without giving
          it permanent room.
        </p>

        <div class="one-page-points">
          <div class="point">
            <span class="point-mark">01</span>
            <div>
              <h3>No page management</h3>
              <p>No left-right maze, no empty panels, no tuning page counts.</p>
            </div>
          </div>
          <div class="point">
            <span class="point-mark">02</span>
            <div>
              <h3>Folders for depth</h3>
              <p>Group apps and shortcuts without spreading your home across screens.</p>
            </div>
          </div>
          <div class="point">
            <span class="point-mark">03</span>
            <div>
              <h3>Two widget modes</h3>
              <p>Inline for glanceable widgets, popup for widgets you want close but quiet.</p>
            </div>
          </div>
        </div>
      </div>

      <div class="one-page-visual animate-in" aria-label="Milki one-page homescreen model">
        <div class="phone-panel">
          <div class="panel-status"></div>
          <div class="single-canvas-label">Single home page</div>

          <div class="canvas-grid">
            <div class="tile tile-app">Mail</div>
            <div class="tile tile-folder">
              <span>Work</span>
              <div class="folder-dots">
                <i></i><i></i><i></i><i></i>
              </div>
            </div>
            <div class="tile tile-app">Notes</div>
            <div class="tile tile-app">Maps</div>

            <div class="tile tile-widget inline-widget">
              <span class="widget-label">Inline widget</span>
              <strong>Next: Standup</strong>
              <small>10:30 AM</small>
            </div>

            <div class="tile tile-folder">
              <span>Media</span>
              <div class="folder-dots">
                <i></i><i></i><i></i><i></i>
              </div>
            </div>
            <div class="tile tile-app">Files</div>
            <div class="tile tile-app">Todo</div>

            <div class="tile tile-popup">
              <span class="widget-label">Popup widget</span>
              <strong>Weather</strong>
              <small>Opens on tap</small>
            </div>
          </div>

          <div class="search-strip">Search apps, files, contacts, web...</div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
@import './home-vars.css';

.one-page {
  position: relative;
  z-index: 1;
  padding: var(--h-section-pad);
}

.one-page-inner {
  max-width: var(--h-max-width);
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1.02fr) minmax(340px, 0.98fr);
  gap: 4rem;
  align-items: center;
}

.section-kicker {
  display: inline-block;
  margin-bottom: 0.85rem;
  padding: 4px 12px;
  border-radius: 6px;
  background: var(--h-primary-subtle);
  color: var(--h-green-600);
  font-family: var(--h-font-body);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 1.5px;
  text-transform: uppercase;
}

.one-page-copy h2 {
  max-width: 620px;
  margin: 0 0 1.1rem;
  color: var(--vp-c-text-1);
  font-family: var(--h-font-body);
  font-size: 2.7rem;
  line-height: 1.08;
  font-weight: 800;
}

.one-page-copy h2::first-letter {
  font-family: var(--h-font-display);
  font-style: italic;
  color: var(--h-green-500);
}

.one-page-copy > p {
  max-width: 650px;
  margin: 0 0 1rem;
  color: var(--vp-c-text-2);
  font-family: var(--h-font-body);
  font-size: 1rem;
  line-height: 1.75;
}

.one-page-points {
  display: grid;
  gap: 0.9rem;
  margin-top: 2rem;
}

.point {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
  padding: 1rem 0;
  border-top: 1px solid var(--h-border);
}

.point:last-child {
  border-bottom: 1px solid var(--h-border);
}

.point-mark {
  color: var(--h-green-500);
  font-family: var(--h-font-display);
  font-size: 1.2rem;
  font-weight: 800;
  line-height: 1;
}

.point h3 {
  margin: 0 0 0.25rem;
  color: var(--vp-c-text-1);
  font-family: var(--h-font-body);
  font-size: 1rem;
  font-weight: 700;
}

.point p {
  margin: 0;
  color: var(--vp-c-text-2);
  font-family: var(--h-font-body);
  font-size: 0.92rem;
  line-height: 1.55;
}

.one-page-visual {
  display: flex;
  justify-content: center;
}

.phone-panel {
  width: min(100%, 430px);
  padding: 1.1rem;
  border-radius: 34px;
  border: 1px solid var(--h-border);
  background:
    linear-gradient(180deg, rgba(34, 197, 94, 0.08), transparent 35%),
    var(--vp-c-bg-soft);
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.08);
}

.panel-status {
  width: 72px;
  height: 5px;
  margin: 0 auto 1rem;
  border-radius: 999px;
  background: var(--vp-c-divider);
}

.single-canvas-label {
  margin-bottom: 0.85rem;
  color: var(--h-green-600);
  font-family: var(--h-font-body);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 1.3px;
  text-align: center;
  text-transform: uppercase;
}

.canvas-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  grid-auto-rows: 72px;
  gap: 0.65rem;
}

.tile {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: center;
  border-radius: 18px;
  border: 1px solid var(--h-border);
  background: var(--vp-c-bg);
  color: var(--vp-c-text-1);
  font-family: var(--h-font-body);
  font-size: 0.76rem;
  font-weight: 700;
  text-align: center;
}

.tile-app {
  background: rgba(255, 255, 255, 0.58);
}

.tile-folder {
  flex-direction: column;
  gap: 0.45rem;
  background: rgba(34, 197, 94, 0.09);
}

.folder-dots {
  display: grid;
  grid-template-columns: repeat(2, 8px);
  gap: 4px;
}

.folder-dots i {
  width: 8px;
  height: 8px;
  border-radius: 3px;
  background: var(--h-green-400);
}

.tile-widget {
  grid-column: span 2;
  flex-direction: column;
  align-items: flex-start;
  padding: 0.9rem;
  background: linear-gradient(135deg, rgba(22, 163, 74, 0.16), rgba(74, 222, 128, 0.08));
}

.tile-popup {
  grid-column: span 2;
  flex-direction: column;
  align-items: flex-start;
  padding: 0.9rem;
  border-style: dashed;
  background: rgba(255, 255, 255, 0.48);
}

.widget-label {
  margin-bottom: 0.25rem;
  color: var(--h-green-600);
  font-size: 0.66rem;
  font-weight: 800;
  letter-spacing: 1px;
  text-transform: uppercase;
}

.tile strong {
  font-size: 0.9rem;
  line-height: 1.2;
}

.tile small {
  margin-top: 0.18rem;
  color: var(--vp-c-text-2);
  font-size: 0.72rem;
}

.search-strip {
  margin-top: 0.9rem;
  padding: 0.9rem 1rem;
  border-radius: 18px;
  border: 1px solid var(--h-border);
  background: var(--vp-c-bg);
  color: var(--vp-c-text-3);
  font-family: var(--h-font-body);
  font-size: 0.82rem;
}

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
  .one-page-inner {
    grid-template-columns: 1fr;
    gap: 3rem;
  }

  .one-page-copy {
    text-align: center;
  }

  .one-page-copy h2,
  .one-page-copy > p {
    margin-left: auto;
    margin-right: auto;
  }

  .point {
    text-align: left;
  }
}

@media (max-width: 520px) {
  .one-page-copy h2 {
    font-size: 2.1rem;
  }

  .phone-panel {
    padding: 0.85rem;
    border-radius: 26px;
  }

  .canvas-grid {
    grid-auto-rows: 64px;
    gap: 0.5rem;
  }

  .tile {
    border-radius: 14px;
    font-size: 0.68rem;
  }

  .tile-widget,
  .tile-popup {
    padding: 0.7rem;
  }
}
</style>
