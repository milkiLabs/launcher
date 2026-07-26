<script setup>
import { ref, onMounted, onUnmounted, nextTick } from "vue";

const props = defineProps({
  variant: {
    type: String,
    default: "primary",
    validator: (v) => ["primary", "nav", "ghost"].includes(v),
  },
  direction: { type: String, default: "down", validator: (v) => ["up", "down"].includes(v) },
  align: {
    type: String,
    default: "left",
    validator: (v) => ["left", "center", "right"].includes(v),
  },
  analyticsPrefix: { type: String, default: "download" },
});

const emit = defineEmits(["select"]);

const detailsRef = ref(null);
const summaryRef = ref(null);
const menuRef = ref(null);
const isOpen = ref(false);
const menuStyle = ref({});

const positionMenu = () => {
  const rect = summaryRef.value.getBoundingClientRect();
  const menuWidth = 240;
  const gap = 10;

  let top = props.direction === "up" ? rect.top - gap : rect.bottom + gap;
  let left =
    props.align === "center"
      ? rect.left + rect.width / 2 - menuWidth / 2
      : props.align === "right"
        ? rect.right - menuWidth
        : rect.left;

  menuStyle.value = {
    position: "fixed",
    top: props.direction === "up" ? "auto" : `${top}px`,
    bottom: props.direction === "up" ? `${window.innerHeight - top}px` : "auto",
    left: `${left}px`,
  };
};

const close = () => {
  if (detailsRef.value) detailsRef.value.open = false;
  isOpen.value = false;
};

const handleToggle = async (e) => {
  isOpen.value = e.target.open;
  if (isOpen.value) {
    await nextTick();
    positionMenu();
  }
};

const handleSelect = (method) => {
  emit("select", method);
  close();
};

const handleClickOutside = (event) => {
  if (!isOpen.value) return;
  if (detailsRef.value?.contains(event.target)) return;
  if (menuRef.value?.contains(event.target)) return;
  close();
};

const handleEscape = (event) => {
  if (event.key === "Escape" && isOpen.value) close();
};

const handleScroll = () => {
  if (isOpen.value) close();
};

onMounted(() => {
  document.addEventListener("click", handleClickOutside);
  document.addEventListener("keydown", handleEscape);
  window.addEventListener("scroll", handleScroll, { passive: true });
});
onUnmounted(() => {
  document.removeEventListener("click", handleClickOutside);
  document.removeEventListener("keydown", handleEscape);
  window.removeEventListener("scroll", handleScroll);
});
</script>

<template>
  <details ref="detailsRef" class="download-dropdown" @toggle="handleToggle">
    <summary
      ref="summaryRef"
      class="download-trigger"
      :class="[`trigger-${variant}`]"
      :data-umami-event="analyticsPrefix"
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
        >
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
          <polyline points="7 10 12 15 17 10" />
          <line x1="12" y1="15" x2="12" y2="3" />
        </svg>
      </slot>
      <slot name="trigger-text">Download</slot>
      <svg
        class="chevron"
        :class="{ 'is-open': isOpen }"
        width="14"
        height="14"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <polyline points="6 9 12 15 18 9" />
      </svg>
    </summary>
  </details>

  <Teleport to="body">
    <div v-if="isOpen" ref="menuRef" class="download-dropdown-menu" :style="menuStyle">
      <a
        href="https://github.com/milkilabs/launcher/releases/latest/download/app-release.apk"
        class="dd-item"
        target="_blank"
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
        >
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
          <polyline points="7 10 12 15 17 10" />
          <line x1="12" y1="15" x2="12" y2="3" />
        </svg>
        <div class="dd-item-text">
          <span class="dd-item-title">Direct APK</span>
          <span class="dd-item-desc">Download from GitHub</span>
        </div>
      </a>

      <a
        href="https://f-droid.org/en/packages/com.milki.launcher/"
        class="dd-item"
        target="_blank"
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
        >
          <path d="M12 2L2 7l10 5 10-5-10-5z" />
          <path d="M2 17l10 5 10-5" />
          <path d="M2 12l10 5 10-5" />
        </svg>
        <div class="dd-item-text">
          <span class="dd-item-title">F-Droid</span>
          <span class="dd-item-desc">Open source repository</span>
        </div>
      </a>
    </div>
  </Teleport>
</template>

<style>
.download-dropdown {
  display: inline-block;
}

.download-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0.85rem 2rem;
  font-size: 0.95rem;
  font-weight: 700;
  border-radius: 14px;
  font-family:
    "DM Sans",
    system-ui,
    -apple-system,
    sans-serif;
  cursor: pointer;
  list-style: none;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}
.download-trigger::-webkit-details-marker,
.download-trigger::marker {
  display: none;
  content: "";
}

.trigger-primary {
  background: linear-gradient(135deg, #22c55e, #16a34a);
  color: #fff;
  box-shadow:
    0 4px 16px rgba(34, 197, 94, 0.3),
    0 1px 3px rgba(0, 0, 0, 0.08);
}
.trigger-primary:hover {
  transform: translateY(-2px);
  box-shadow:
    0 8px 28px rgba(34, 197, 94, 0.35),
    0 2px 6px rgba(0, 0, 0, 0.1);
  background: linear-gradient(135deg, #4ade80, #22c55e);
}

.trigger-nav {
  background: linear-gradient(135deg, #22c55e, #16a34a);
  color: #fff;
  box-shadow: 0 4px 14px rgba(34, 197, 94, 0.25);
  padding: 0.55rem 1.4rem;
  font-size: 0.95rem;
  border-radius: 12px;
}
.trigger-nav:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(34, 197, 94, 0.35);
}

.trigger-ghost {
  background: transparent;
  color: var(--vp-c-text-1);
  border: 1.5px solid var(--vp-c-divider);
}
.trigger-ghost:hover {
  border-color: #4ade80;
  background: rgba(34, 197, 94, 0.08);
  transform: translateY(-2px);
}

.chevron {
  transition: transform 0.3s ease;
}
.chevron.is-open {
  transform: rotate(180deg);
}

.download-dropdown-menu {
  min-width: 240px;
  background: var(--vp-c-bg);
  border: 1px solid var(--vp-c-divider);
  border-radius: 14px;
  padding: 8px;
  box-shadow:
    0 10px 40px rgba(0, 0, 0, 0.12),
    0 2px 8px rgba(0, 0, 0, 0.06);
  z-index: 9999;
  animation: dropdown-in 0.15s ease;
}
@keyframes dropdown-in {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.dd-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 10px;
  text-decoration: none;
  color: var(--vp-c-text-1);
  transition: all 0.2s ease;
}
.dd-item:hover {
  background: rgba(34, 197, 94, 0.08);
}
.dd-item svg {
  color: #22c55e;
  flex-shrink: 0;
}
.dd-item-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.dd-item-title {
  font-weight: 600;
  font-size: 0.9rem;
}
.dd-item-desc {
  font-size: 0.75rem;
  color: var(--vp-c-text-3);
}
</style>
