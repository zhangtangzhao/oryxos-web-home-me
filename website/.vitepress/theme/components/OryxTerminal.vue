<script setup>
import { onMounted, onUnmounted, ref, computed } from 'vue'

const props = defineProps({
  title: { type: String, default: 'oryxos' },
  lines: { type: Array, required: true },
  loop: { type: Boolean, default: true },
})

const shown = ref(0)
const chars = ref(0)
const done = ref(false)

let timer = null
let cycleTimer = null

function step() {
  if (shown.value >= props.lines.length) {
    done.value = true
    clearInterval(timer)
    if (props.loop) {
      cycleTimer = setTimeout(() => {
        shown.value = 0
        chars.value = 0
        done.value = false
        start()
      }, 7000)
    }
    return
  }
  const line = props.lines[shown.value]
  if (chars.value < line.t.length) {
    const speed = line.c === 'user' || line.c === 'cmd' ? 1 : 3
    chars.value = Math.min(line.t.length, chars.value + speed)
  } else {
    shown.value++
    chars.value = 0
  }
}

function start() {
  timer = setInterval(step, 26)
}

onMounted(() => {
  if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    shown.value = props.lines.length
    done.value = true
    return
  }
  start()
})

onUnmounted(() => {
  clearInterval(timer)
  clearTimeout(cycleTimer)
})

const visibleLines = computed(() => props.lines.slice(0, shown.value))
const currentLine = computed(() => {
  if (done.value || shown.value >= props.lines.length) return null
  const l = props.lines[shown.value]
  return { c: l.c, t: l.t.slice(0, chars.value) }
})
</script>

<template>
  <div class="oterm">
    <div class="oterm__bar">
      <span class="oterm__dot oterm__dot--r"></span>
      <span class="oterm__dot oterm__dot--y"></span>
      <span class="oterm__dot oterm__dot--g"></span>
      <span class="oterm__title">{{ title }}</span>
      <span class="oterm__badge">ReAct</span>
    </div>
    <div class="oterm__body">
      <div v-for="(l, i) in visibleLines" :key="i" class="oterm__line" :class="'oterm__line--' + l.c">{{ l.t }}</div>
      <div v-if="currentLine" class="oterm__line" :class="'oterm__line--' + currentLine.c">{{ currentLine.t }}<span class="oterm__cursor"></span></div>
      <div v-else-if="!done" class="oterm__line oterm__line--cmd"><span class="oterm__cursor"></span></div>
    </div>
  </div>
</template>

<style scoped>
.oterm {
  border: 1px solid rgba(255, 107, 43, 0.25);
  border-radius: 12px;
  background: #0D0D0D;
  box-shadow: 0 24px 64px -24px rgba(255, 107, 43, 0.25), 0 0 0 1px rgba(0, 0, 0, 0.6);
  overflow: hidden;
  max-width: 760px;
  margin: 0 auto;
}
.oterm__bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #141414;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.oterm__dot {
  width: 11px;
  height: 11px;
  border-radius: 50%;
}
.oterm__dot--r { background: #FF5F57; }
.oterm__dot--y { background: #FEBC2E; }
.oterm__dot--g { background: #28C840; }
.oterm__title {
  margin-left: 8px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
  font-family: 'Cascadia Mono', 'JetBrains Mono', Consolas, monospace;
}
.oterm__badge {
  margin-left: auto;
  font-size: 10px;
  letter-spacing: 0.15em;
  color: #FF8C4A;
  border: 1px solid rgba(255, 107, 43, 0.4);
  border-radius: 4px;
  padding: 1px 8px;
  font-family: 'Cascadia Mono', 'JetBrains Mono', Consolas, monospace;
}
.oterm__body {
  padding: 18px 20px 22px;
  min-height: 320px;
  font-family: 'Cascadia Mono', 'JetBrains Mono', Consolas, 'Microsoft YaHei', monospace;
  font-size: 13px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-all;
}
.oterm__line { color: #E8E3DE; }
.oterm__line--cmd { color: #E8E3DE; }
.oterm__line--user { color: #FFB86B; font-weight: 700; }
.oterm__line--reason { color: #FF8C4A; }
.oterm__line--act { color: #FF6B2B; }
.oterm__line--observe { color: #9C948C; }
.oterm__line--ok { color: #5DD8A6; font-weight: 700; }
.oterm__line--ok2 { color: #3FA97E; }
.oterm__cursor {
  display: inline-block;
  width: 8px;
  height: 15px;
  margin-left: 2px;
  vertical-align: -2px;
  background: #FF6B2B;
  animation: oterm-blink 1s steps(1) infinite;
}
@keyframes oterm-blink {
  50% { opacity: 0; }
}
@media (max-width: 640px) {
  .oterm__body { font-size: 11.5px; min-height: 280px; padding: 14px 14px 18px; }
}
</style>
