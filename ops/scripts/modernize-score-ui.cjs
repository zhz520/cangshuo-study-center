const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'../..');
const file=path.join(root,'apps/web/src/views/components/ScoreAnalysisCard.vue');
let source=fs.readFileSync(file,'utf8');
const script=source.slice(source.indexOf('<script setup>'));
const template=`<template>
  <section class="score-diagnosis">
    <h3 class="ui-section-title">成绩分析</h3>
    <div class="score-diagnosis-overview">
      <div>
        <span class="ui-small ui-muted">本次成绩超过约</span>
        <p class="score-diagnosis-value">{{ defeatRate }}<small>%</small></p>
        <p class="ui-small ui-muted">按本次联考排名计算，仅作参考。</p>
        <strong class="score-diagnosis-summary">{{ aiPrediction.title }}</strong>
        <p class="ui-small ui-muted">{{ aiPrediction.desc }}</p>
      </div>
      <div ref="radarChartRef" class="score-diagnosis-chart"></div>
    </div>
    <div class="score-diagnosis-subjects">
      <div><span class="ui-small ui-muted">相对优势</span><h4>{{ strengths.name }}</h4><p>{{ strengths.comment }}</p></div>
      <div><span class="ui-small ui-muted">优先复习</span><h4>{{ weaknesses.name }}</h4><p>{{ weaknesses.comment }}</p></div>
    </div>
    <div class="score-diagnosis-ai">
      <div class="ui-row-between"><div><h4 class="ui-section-title">AI 学情建议</h4><p class="ui-small ui-muted">结合成绩与各科表现生成建议。</p></div><button v-if="!aiAnalysis" class="ui-btn ui-btn-outline ui-btn-sm" :disabled="aiLoading" @click="requestAiAnalysis">{{ aiLoading ? '分析中…' : '生成报告' }}</button></div>
      <div v-if="aiLoading" class="ui-loading"><span class="ui-spinner" style="display:inline-block"></span><p>正在分析成绩…</p></div>
      <div v-else-if="aiAnalysis" class="score-diagnosis-report">{{ aiAnalysis }}</div>
      <p v-else-if="aiError" class="score-error" role="alert">{{ aiError }} <button @click="requestAiAnalysis">重试</button></p>
    </div>
  </section>
</template>
`;
source=template+script;
source=source.replace("color: ['#a855f7']","color: ['#2563eb']").replaceAll("'#e8e8f0'","'#64748b'").replaceAll("'rgba(255, 255, 255, 0.08)'","'#cbd5e1'").replaceAll("'rgba(255, 255, 255, 0.05)'","'#e2e8f0'").replaceAll("'rgba(255, 255, 255, 0.1)'","'#cbd5e1'");
fs.writeFileSync(file,source);
