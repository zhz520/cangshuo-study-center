// Stages every row and validates a reversible conversion before the live update.
// --stage only creates private copies. --apply requires a matching staged fingerprint.
const fs=require('node:fs');
const crypto=require('node:crypto');
const root='/www/wwwroot/study_center';
require(root+'/node_modules/dotenv').config({path:root+'/.env'});
const mysql=require(root+'/node_modules/mysql2/promise');
const {normalizeQuestionRecord,unpackJson}=require('/root/study-question-format.cjs');
const {canonical}=require('/root/study-score-answer.cjs');
const dir='/www/backup/codex/study-center-20261022';
const tables={questions:'codex_qfmt_questions_20261022',exam_paper_questions:'codex_qfmt_exams_20261022'};
const columns=['type','content','options','answer','analysis'];
const digest=row=>crypto.createHash('sha256').update(JSON.stringify([row.id,...columns.map(k=>row[k])])).digest('hex');
function resolveKnownLabelTypo(row,table) {
 // Both labels and option text come from the original ordered array, corroborated by its explanation.
 if(table==='questions'&&[42666,42704].includes(row.id)) {
  const raw=unpackJson(row.options);
  const expected=row.id===42666?['打印机的速度','CPU的性能','键盘上键的多少','所带软件的多少']:['文件格式协议','信息交换协议','TCP/IP协议','硬件生产标准协议'];
  if(!Array.isArray(raw)||raw.length!==4||raw.some((v,i)=>v.content!==expected[i]))throw new Error('Reviewed option correction no longer matches original');
  return {...row,options:raw.map((item,i)=>({...item,label:'ABCD'[i]}))};
 }
 return row;
}
(async()=>{
 const db=await mysql.createConnection({host:process.env.DB_HOST||'localhost',user:process.env.DB_USER,password:process.env.DB_PASSWORD,database:process.env.DB_NAME});
 try {
 if(process.argv.includes('--stage')) {
  const report={time:new Date().toISOString(),formatVersion:1,tables:{}};
  for(const [table,stage]of Object.entries(tables)) {
   const [present]=await db.query('SHOW TABLES LIKE ?',[stage]);if(present.length)throw new Error('Stage exists; inspect it before retry');
   await db.query(`CREATE TABLE ${stage} LIKE ${table}`);
   await db.query(`INSERT INTO ${stage} SELECT * FROM ${table}`);
   const before=crypto.createHash('sha256');const after=crypto.createHash('sha256');const stats={total:0,changed:0,qualityIssues:{},qualityIds:{},reviewedLabelRepairs:[],before:null,after:null};let last=0;
   while(true) {
    const [rows]=await db.query(`SELECT id,type,content,options,answer,analysis FROM ${stage} WHERE id>? ORDER BY id LIMIT 200`,[last]);if(!rows.length)break;
    await db.beginTransaction();
    try {
     for(const original of rows) {
      const input=resolveKnownLabelTypo(original,table);
      const result=normalizeQuestionRecord(input);const n=result.record;
      if(!result.safe)throw new Error('Unresolved format conflict at '+table+':'+original.id);
      if(n.content!==original.content||n.analysis!==(original.analysis??''))throw new Error('Rich content changed');
      if(input!==original)stats.reviewedLabelRepairs.push(original.id);
      // Stored correctness must not change when removing wrappers or normalizing answer separators.
      if(canonical(original.answer,original.type)!==canonical(n.answer,n.type))throw new Error('Scoring changed at '+table+':'+original.id);
      for(const issue of result.issues){stats.qualityIssues[issue]=(stats.qualityIssues[issue]||0)+1;(stats.qualityIds[issue]??=[]).push(original.id);}
      before.update(digest(original));after.update(digest({id:original.id,...n}));
      if(columns.some(k=>JSON.stringify(original[k])!==JSON.stringify(n[k]))) {
       await db.query(`UPDATE ${stage} SET type=?,content=?,options=?,answer=?,analysis=? WHERE id=?`,[n.type,n.content,JSON.stringify(n.options),n.answer,n.analysis,original.id]);stats.changed++;
      }
      last=original.id;stats.total++;
     }
     await db.commit();
    }catch(e){await db.rollback();throw e;}
   }
   stats.before=before.digest('hex');stats.after=after.digest('hex');report.tables[table]=stats;
   console.log(JSON.stringify({table,stage,total:stats.total,changed:stats.changed,qualityIssues:stats.qualityIssues}));
  }
  fs.writeFileSync(dir+'/normalization-stage.json',JSON.stringify(report,null,2),{mode:0o600});
 } else if(process.argv.includes('--refresh-stage')) {
  const report=JSON.parse(fs.readFileSync(dir+'/normalization-stage.json','utf8'));
  for(const [table,stage]of Object.entries(tables)) {
   let last=0,count=0;const before=crypto.createHash('sha256'),after=crypto.createHash('sha256');
   await db.beginTransaction();
   try {
    while(true){const [rows]=await db.query(`SELECT id,type,content,options,answer,analysis FROM ${stage} WHERE id>? ORDER BY id LIMIT 500 FOR UPDATE`,[last]);if(!rows.length)break;
     for(const row of rows){before.update(digest(row));const result=normalizeQuestionRecord(row);if(!result.safe)throw new Error('Stage normalization blocked');const n=result.record;
      if(JSON.stringify(row.answer)!==JSON.stringify(n.answer))await db.query(`UPDATE ${stage} SET answer=? WHERE id=?`,[n.answer,row.id]);
      if(columns.filter(k=>k!=='answer').some(k=>JSON.stringify(row[k])!==JSON.stringify(n[k])))throw new Error('Unexpected staged change');
      after.update(digest({id:row.id,...n}));last=row.id;count++;
     }
    }
    if(count!==report.tables[table].total||before.digest('hex')!==report.tables[table].after)throw new Error('Stage changed unexpectedly');
    await db.commit();report.tables[table].after=after.digest('hex');
   }catch(e){await db.rollback();throw e;}
  }
  fs.writeFileSync(dir+'/normalization-stage.json',JSON.stringify(report,null,2),{mode:0o600});console.log('STAGE_REFRESH_VERIFIED');
 } else if(process.argv.includes('--verify-stage')) {
  const report=JSON.parse(fs.readFileSync(dir+'/normalization-stage.json','utf8'));
  for (const [table,stage] of Object.entries(tables)) {
   let last=0,total=0;const hash=crypto.createHash('sha256');
   while (true) {
    const [rows]=await db.query(`SELECT id,type,content,options,answer,analysis FROM ${stage} WHERE id>? ORDER BY id LIMIT 500`,[last]);if(!rows.length)break;
    for(const row of rows){hash.update(digest(row));const result=normalizeQuestionRecord(row);if(!result.safe)throw new Error('Stage is not safe');if(JSON.stringify({...result.record})!==JSON.stringify(Object.fromEntries(columns.map(k=>[k,row[k]]))))throw new Error('Stage is not idempotent at '+table+':'+row.id);last=row.id;total++;}
   }
   if(total!==report.tables[table].total||hash.digest('hex')!==report.tables[table].after)throw new Error('Staged row fingerprint mismatch');
   console.log(JSON.stringify({table,verified:total,fingerprint:report.tables[table].after}));
  }
 } else if(process.argv.includes('--apply')) {
  const report=JSON.parse(fs.readFileSync(dir+'/normalization-stage.json','utf8'));
  await db.beginTransaction();
  try {
   for(const [table,stage]of Object.entries(tables)) {
    const before=crypto.createHash('sha256');let last=0,count=0;
    while(true){const [rows]=await db.query(`SELECT id,type,content,options,answer,analysis FROM ${table} WHERE id>? ORDER BY id LIMIT 500 FOR UPDATE`,[last]);if(!rows.length)break;for(const row of rows){before.update(digest(row));last=row.id;count++;}}
    if(count!==report.tables[table].total||before.digest('hex')!==report.tables[table].before)throw new Error('Live questions changed after staging; regenerate the stage');
    await db.query(`UPDATE ${table} live JOIN ${stage} staged ON staged.id=live.id SET live.type=staged.type,live.content=staged.content,live.options=staged.options,live.answer=staged.answer,live.analysis=staged.analysis`);
    const after=crypto.createHash('sha256');last=0;
    while(true){const [rows]=await db.query(`SELECT id,type,content,options,answer,analysis FROM ${table} WHERE id>? ORDER BY id LIMIT 500`,[last]);if(!rows.length)break;for(const row of rows){after.update(digest(row));last=row.id;}}
    if(after.digest('hex')!==report.tables[table].after)throw new Error('Live verification mismatch');
   }
   await db.commit();fs.writeFileSync(dir+'/normalization-applied.json',JSON.stringify({time:new Date().toISOString(),tables:report.tables},null,2),{mode:0o600});console.log('ALL_QUESTION_FORMATS_APPLIED_AND_VERIFIED');
  }catch(e){await db.rollback();throw e;}
 } else throw new Error('Use --stage, --verify-stage or --apply');
 }finally{await db.end();}
})().catch(e=>{console.error(e.message);process.exitCode=1});
