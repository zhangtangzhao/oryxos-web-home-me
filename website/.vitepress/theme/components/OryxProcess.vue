<script setup>
defineProps({
  title: { type: String, default: 'oryxos ps' },
  note: { type: String, default: '' },
  header: { type: Array, required: true },
  rows: { type: Array, required: true },
})
</script>

<template>
  <div class="ops">
    <div class="ops__bar">
      <span class="ops__title">{{ title }}</span>
      <span v-if="note" class="ops__note">{{ note }}</span>
    </div>
    <div class="ops__scroll">
      <table class="ops__table">
        <thead>
          <tr>
            <th v-for="h in header" :key="h">{{ h }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row[0]">
            <td class="ops__pid">{{ row[0] }}</td>
            <td class="ops__name">{{ row[1] }}</td>
            <td class="ops__status">
              <span class="ops__dot" :class="'ops__dot--' + row[2]"></span>{{ row[2] }}
            </td>
            <td class="ops__dim">{{ row[3] }}</td>
            <td class="ops__dim">{{ row[4] }}</td>
            <td class="ops__dim">{{ row[5] }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.ops {
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  background: #0D0D0D;
  overflow: hidden;
  max-width: 880px;
  margin: 0 auto;
}
.ops__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  background: #141414;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  font-family: 'Cascadia Mono', 'JetBrains Mono', Consolas, monospace;
}
.ops__title {
  font-size: 13px;
  font-weight: 700;
  color: #FF8C4A;
}
.ops__title::before { content: '$ '; color: #9C948C; font-weight: 400; }
.ops__note {
  font-size: 11px;
  color: #9C948C;
  text-align: right;
}
.ops__scroll { overflow-x: auto; }
.ops__table {
  width: 100%;
  border-collapse: collapse;
  font-family: 'Cascadia Mono', 'JetBrains Mono', Consolas, 'Microsoft YaHei', monospace;
  font-size: 12.5px;
  min-width: 640px;
}
.ops__table th {
  text-align: left;
  padding: 10px 16px;
  font-size: 10.5px;
  letter-spacing: 0.14em;
  color: #9C948C;
  font-weight: 600;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  white-space: nowrap;
}
.ops__table td {
  padding: 11px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  color: #E8E3DE;
  white-space: nowrap;
}
.ops__table tbody tr:last-child td { border-bottom: none; }
.ops__table tbody tr:hover td { background: rgba(255, 107, 43, 0.05); }
.ops__pid { color: #9C948C; }
.ops__name { color: #F5F0EB; font-weight: 700; }
.ops__dim { color: #9C948C; }
.ops__status { color: #E8E3DE; }
.ops__dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 8px;
  vertical-align: 0;
}
.ops__dot--running { background: #28C840; animation: ops-pulse-green 1.6s ease-in-out infinite; }
.ops__dot--idle { background: #6B655F; }
.ops__dot--scheduled { background: #FF8C4A; animation: ops-pulse-orange 2.4s ease-in-out infinite; }
@keyframes ops-pulse-green {
  0%, 100% { box-shadow: 0 0 0 0 rgba(40, 200, 64, 0.5); }
  50% { box-shadow: 0 0 0 5px rgba(40, 200, 64, 0); }
}
@keyframes ops-pulse-orange {
  0%, 100% { box-shadow: 0 0 0 0 rgba(255, 140, 74, 0.5); }
  50% { box-shadow: 0 0 0 5px rgba(255, 140, 74, 0); }
}
</style>
