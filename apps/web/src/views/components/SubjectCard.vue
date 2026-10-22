<template>
  <div v-if="data" class="bg-[var(--bg-body)] rounded-xl border border-[var(--glass-border)] p-5 hover:border-primary/30 transition-colors shadow-sm relative overflow-hidden">
    <!-- Icon watermark -->
    <div class="absolute -right-4 -bottom-4 text-7xl opacity-5 pointer-events-none">{{ icon }}</div>
    
    <div class="flex justify-between items-center mb-4 relative z-10">
      <div class="flex items-center space-x-2">
        <span class="text-xl">{{ icon }}</span>
        <h4 class="text-lg font-semibold text-[var(--text-main)]">{{ title }}</h4>
      </div>
      <div class="text-3xl font-bold text-primary">{{ Number(data.total) }}<span class="text-sm text-[var(--text-muted)] ml-1">分</span></div>
    </div>
    
    <div class="grid grid-cols-2 gap-y-3 gap-x-4 text-sm relative z-10">
      <div class="flex justify-between">
        <span class="text-[var(--text-muted)]">客观题</span>
        <span class="font-medium text-[var(--text-main)]">{{ Number(data.objective) || 0 }}</span>
      </div>
      <div class="flex justify-between">
        <span class="text-[var(--text-muted)]">主观题</span>
        <span class="font-medium text-[var(--text-main)]">{{ Number(data.subjective) || 0 }}</span>
      </div>
      <div class="col-span-2 h-px bg-[var(--glass-border)] my-1"></div>
      <div class="flex justify-between">
        <span class="text-[var(--text-muted)]">联考榜</span>
        <span class="font-medium text-[var(--text-main)]">#{{ data.rank_overall }}</span>
      </div>
      <div class="flex justify-between" v-if="data.rank_city">
        <span class="text-[var(--text-muted)]">市排名</span>
        <span class="font-medium text-[var(--text-main)]">#{{ data.rank_city }}</span>
      </div>
      <div class="flex justify-between" v-if="data.rank_district">
        <span class="text-[var(--text-muted)]">区排名</span>
        <span class="font-medium text-[var(--text-main)]">#{{ data.rank_district }}</span>
      </div>
      <div class="flex justify-between" v-if="data.rank_school">
        <span class="text-[var(--text-muted)]">校排名</span>
        <span class="font-medium text-[var(--text-main)]">#{{ data.rank_school }}</span>
      </div>
      <div class="flex justify-between" v-if="data.rank_class">
        <span class="text-[var(--text-muted)]">班排名</span>
        <span class="font-medium text-[var(--text-main)]">#{{ data.rank_class }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: String,
  icon: String,
  data: Object
});
</script>
