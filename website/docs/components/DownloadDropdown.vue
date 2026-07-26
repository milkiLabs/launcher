<script setup>
import { ref, onMounted, onUnmounted, nextTick } from "vue";

const props = defineProps({
  variant: {
    type: String,
    default: "primary",
    validator: (v) => ["primary", "nav", "ghost"].includes(v),
  },
  direction: {
    type: String,
    default: "down",
    validator: (v) => ["up", "down"].includes(v),
  },
  align: {
    type: String,
    default: "left",
    validator: (v) => ["left", "center", "right"].includes(v),
  },
  analyticsPrefix: { type: String, default: "download" },
});

const emit = defineEmits(["select"]);

const triggerRef = ref(null);
const menuRef = ref(null);
const isOpen = ref(false);
const layerStyle = ref({});

const updatePosition = () => {
  if (!isOpen.value || !triggerRef.value) return;
  const rect = triggerRef.value.getBoundingClientRect();
  const gap = 8;

  let x = rect.left;
  let translateX = "0";
  if (props.align === "right") {
    x = rect.right;
    translateX = "-100%";
  } else if (props.align === "center") {
    x = rect.left + rect.width / 2;
    translateX = "-50%";
  }

  let y = rect.bottom + gap;
  let translateY = "0";
  if (props.direction === "up") {
    y = rect.top - gap;
    translateY = "-100%";
  }

  let transform = "";
  if (translateX !== "0" && translateY !== "0") {
    transform = `translate(${translateX}, ${translateY})`;
  } else if (translateX !== "0") {
    transform = `translateX(${translateX})`;
  } else if (translateY !== "0") {
    transform = `translateY(${translateY})`;
  } else {
    transform = "none";
  }

  layerStyle.value = {
    position: "fixed",
    zIndex: "99999",
    top: `${y}px`,
    left: `${x}px`,
    transform,
  };
};

const toggle = async () => {
  isOpen.value = !isOpen.value;
  if (isOpen.value) {
    await nextTick();
    updatePosition();
  }
};

const close = () => {
  isOpen.value = false;
};

const handleSelect = (method) => {
  emit("select", method);
  close();
};

const handleClickOutside = (event) => {
  if (!isOpen.value) return;
  if (triggerRef.value?.contains(event.target)) return;
  if (menuRef.value?.contains(event.target)) return;
  close();
};

const handleEscape = (event) => {
  if (event.key === "Escape" && isOpen.value) close();
};

const handleScrollOrResize = () => {
  if (isOpen.value) {
    updatePosition();
  }
};

onMounted(() => {
  document.addEventListener("click", handleClickOutside);
  document.addEventListener("keydown", handleEscape);
  window.addEventListener("scroll", handleScrollOrResize, { capture: true, passive: true });
  window.addEventListener("resize", handleScrollOrResize, { passive: true });
});

onUnmounted(() => {
  document.removeEventListener("click", handleClickOutside);
  document.removeEventListener("keydown", handleEscape);
  window.removeEventListener("scroll", handleScrollOrResize, { capture: true });
  window.removeEventListener("resize", handleScrollOrResize);
});
</script>

<template>
  <div class="dd-wrapper">
    <button
      ref="triggerRef"
      type="button"
      class="dd-trigger"
      :class="[`dd-trigger--${variant}`]"
      :data-umami-event="analyticsPrefix"
      :aria-expanded="isOpen"
      @click="toggle"
    >
      <slot name="trigger-icon">
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2.5"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
          <polyline points="7 10 12 15 17 10" />
          <line x1="12" y1="15" x2="12" y2="3" />
        </svg>
      </slot>
      <slot name="trigger-text">Download</slot>
      <svg
        class="dd-chevron"
        :class="{ 'dd-chevron--open': isOpen }"
        width="14"
        height="14"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2.5"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <polyline points="6 9 12 15 18 9" />
      </svg>
    </button>

    <Teleport to="body">
      <div
        v-if="isOpen"
        ref="menuRef"
        class="dd-teleport-layer"
        :style="layerStyle"
      >
        <Transition :name="direction === 'up' ? 'dd-fade-up' : 'dd-fade-down'" appear>
          <div class="dd-menu" role="menu">
            <a
              href="https://github.com/milkilabs/launcher/releases/latest/download/app-release.apk"
              class="dd-option"
              target="_blank"
              role="menuitem"
              @click="handleSelect('apk')"
              :data-umami-event="analyticsPrefix"
              :data-umami-event-data="JSON.stringify({ method: 'apk' })"
            >
              <svg
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                <polyline points="7 10 12 15 17 10" />
                <line x1="12" y1="15" x2="12" y2="3" />
              </svg>
              <div class="dd-option__text">
                <span class="dd-option__title">Direct APK</span>
                <span class="dd-option__desc">Download from GitHub</span>
              </div>
            </a>

            <a
              href="https://f-droid.org/en/packages/com.milki.launcher/"
              class="dd-option"
              target="_blank"
              role="menuitem"
              @click="handleSelect('fdroid')"
              :data-umami-event="analyticsPrefix"
              :data-umami-event-data="JSON.stringify({ method: 'fdroid' })"
            >
              <svg
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M12 2L2 7l10 5 10-5-10-5z" />
                <path d="M2 17l10 5 10-5" />
                <path d="M2 12l10 5 10-5" />
              </svg>
              <div class="dd-option__text">
                <span class="dd-option__title">F-Droid</span>
                <span class="dd-option__desc">Open source repository</span>
              </div>
            </a>
          </div>
        </Transition>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
/* ── Wrapper ── */
.dd-wrapper {
  display: inline-block;
}

/* ── Trigger button ── */
.dd-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0.85rem 2rem;
  font-size: 0.95rem;
  font-weight: 700;
  border-radius: 14px;
  border: none;
  font-family:
    "DM Sans",
    system-ui,
    -apple-system,
    sans-serif;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

/* Primary variant */
.dd-trigger--primary {
  background: linear-gradient(135deg, #22c55e, #16a34a);
  color: #fff;
  box-shadow:
    0 4px 16px rgba(34, 197, 94, 0.3),
    0 1px 3px rgba(0, 0, 0, 0.08);
}
.dd-trigger--primary:hover {
  transform: translateY(-2px);
  box-shadow:
    0 8px 28px rgba(34, 197, 94, 0.35),
    0 2px 6px rgba(0, 0, 0, 0.1);
  background: linear-gradient(135deg, #4ade80, #22c55e);
}

/* Nav variant */
.dd-trigger--nav {
  background: linear-gradient(135deg, #22c55e, #16a34a);
  color: #fff;
  box-shadow: 0 4px 14px rgba(34, 197, 94, 0.25);
  padding: 0.55rem 1.4rem;
  font-size: 0.95rem;
  border-radius: 12px;
}
.dd-trigger--nav:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(34, 197, 94, 0.35);
}

/* Ghost variant */
.dd-trigger--ghost {
  background: transparent;
  color: var(--vp-c-text-1);
  border: 1.5px solid var(--vp-c-divider);
}
.dd-trigger--ghost:hover {
  border-color: #4ade80;
  background: rgba(34, 197, 94, 0.08);
  transform: translateY(-2px);
}

/* ── Chevron ── */
.dd-chevron {
  transition: transform 0.3s ease;
}
.dd-chevron--open {
  transform: rotate(180deg);
}

/* ── Teleport Layer & Menu ── */
.dd-teleport-layer {
  pointer-events: auto;
}

.dd-menu {
  min-width: 240px;
  max-width: calc(100vw - 24px);
  box-sizing: border-box;
  background: var(--vp-c-bg);
  border: 1px solid var(--vp-c-divider);
  border-radius: 14px;
  padding: 8px;
  box-shadow:
    0 10px 40px rgba(0, 0, 0, 0.15),
    0 2px 8px rgba(0, 0, 0, 0.08);
  font-family:
    "DM Sans",
    system-ui,
    -apple-system,
    sans-serif;
}

/* ── Animations ── */
.dd-fade-down-enter-active,
.dd-fade-down-leave-active,
.dd-fade-up-enter-active,
.dd-fade-up-leave-active {
  transition:
    opacity 0.15s ease,
    transform 0.15s ease;
}

.dd-fade-down-enter-from,
.dd-fade-down-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.dd-fade-up-enter-from,
.dd-fade-up-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

/* ── Menu items ── */
.dd-option {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 10px;
  text-decoration: none;
  color: var(--vp-c-text-1);
  transition: background 0.2s ease;
}
.dd-option:hover {
  background: rgba(34, 197, 94, 0.08);
}
.dd-option svg {
  color: #22c55e;
  flex-shrink: 0;
}

.dd-option__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.dd-option__title {
  font-weight: 600;
  font-size: 0.9rem;
}
.dd-option__desc {
  font-size: 0.75rem;
  color: var(--vp-c-text-3);
}
</style>
