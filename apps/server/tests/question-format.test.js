const {test}=require('node:test');
const assert=require('node:assert/strict');
const {normalizeQuestionRecord}=require('../utils/questionFormat');
const {scoreAnswer}=require('../utils/scoreAnswer');
const row=(extra={})=>({type:'single',content:'题干 $x^2$ <img src="/uploads/one.png">',options:{A:'甲',B:'乙'},answer:'A',analysis:'说明\n\\frac{1}{2}',...extra});
test('all legacy option containers and answer wrappers converge without changing rich content',()=>{
 for(const options of [{A:'甲',B:'乙'},[{label:'A',content:'甲'},{label:'B',text:'乙'}],JSON.stringify([{label:'A',content:'甲'},{label:'B',content:'乙'}]),JSON.stringify(JSON.stringify({A:'甲',B:'乙'}))]) {
  for(const answer of ['A','"A"','["A"]']) {
   const input=row({options,answer}),result=normalizeQuestionRecord(input);
   assert.equal(result.safe,true);assert.deepEqual(result.issues,[]);
   assert.deepEqual(result.record,row());
   assert.deepEqual(normalizeQuestionRecord(result.record).record,result.record);
   assert.equal(scoreAnswer(input,'A'),scoreAnswer(result.record,'A'));
  }
 }
});
test('multiple answers are unique sorted labels and preserve the answer set',()=>{
 for(const answer of ['BA','B,A','["B","A"]','"B、A"']) {
  const input=row({type:'multiple',answer});const r=normalizeQuestionRecord(input);
  assert.equal(r.record.answer,'A,B');assert.equal(scoreAnswer(r.record,['B','A']),true);
 }
});
test('judge variants including reversed explicit labels preserve meaning',()=>{
 for(const answer of ['TRUE','正确','["A"]','"对"',true,1])assert.equal(normalizeQuestionRecord(row({type:'judge',options:{},answer})).record.answer,'A');
 const r=normalizeQuestionRecord(row({type:'judge',options:{A:'错误',B:'正确'},answer:'A'}));
 assert.equal(r.record.answer,'B');assert.deepEqual(r.record.options,{A:'正确',B:'错误'});
});
test('ambiguous labels block migration and missing academic data is reported without invention',()=>{
 const duplicate=normalizeQuestionRecord(row({options:[{label:'A',content:'甲'},{label:'A',content:'乙'}]}));
 assert.equal(duplicate.safe,false);assert.ok(duplicate.issues.includes('DUPLICATE_OPTION_LABEL'));
 const missing=normalizeQuestionRecord(row({content:'',options:{A:'甲'},answer:'D'}));
 assert.equal(missing.record.answer,'D');assert.ok(missing.issues.includes('ANSWER_OPTION_MISSING'));
 const multi=normalizeQuestionRecord(row({answer:'AB'}));assert.equal(multi.record.type,'single');assert.ok(multi.issues.includes('SINGLE_HAS_MULTIPLE_ANSWERS'));
});
test('fill text and sort order remain intact; format is idempotent',()=>{
 for(const input of [row({type:'fill',options:[],answer:'翱'}),row({type:'fill',answer:'"对"'}),row({type:'sort',options:[{label:'A',content:'第二'},{label:'B',content:'第一'}],answer:'["第一","第二"]'})]) {
  const r=normalizeQuestionRecord(input);assert.equal(r.safe,true);
  assert.deepEqual(normalizeQuestionRecord(r.record).record,r.record);
 }
 const sorted=normalizeQuestionRecord(row({type:'sort',options:[{label:'A',content:'第二'},{label:'B',content:'第一'}],answer:'["第一","第二"]'})).record;
 assert.ok(Array.isArray(sorted.options));assert.equal(scoreAnswer(sorted,['第一','第二']),true);assert.equal(scoreAnswer(sorted,['第二','第一']),false);
});
