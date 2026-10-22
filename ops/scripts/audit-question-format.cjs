// Read-only. A complete scan includes inactive questions and every exam question.
const fs=require('node:fs');
const root='/www/wwwroot/study_center';
require(root+'/node_modules/dotenv').config({path:root+'/.env'});
const mysql=require(root+'/node_modules/mysql2/promise');
const {normalizeQuestionRecord}=require('/root/study-question-format.cjs');
(async()=>{
 const db=await mysql.createConnection({host:process.env.DB_HOST||'localhost',user:process.env.DB_USER,password:process.env.DB_PASSWORD,database:process.env.DB_NAME});
 const report={time:new Date().toISOString(),tables:{}};
 for(const table of ['questions','exam_paper_questions']){
  const stats={total:0,changed:0,safe:0,blockers:0,issues:{},samples:{}};let id=0;
  while(true){
   const [rows]=await db.query(`SELECT id,type,content,options,answer,analysis FROM ${table} WHERE id>? ORDER BY id LIMIT 500`,[id]);if(!rows.length)break;
   for(const row of rows){id=row.id;stats.total++;const result=normalizeQuestionRecord(row);stats[result.safe?'safe':'blockers']++;
    if(JSON.stringify({type:row.type,content:row.content,options:row.options,answer:row.answer,analysis:row.analysis})!==JSON.stringify(result.record))stats.changed++;
    for(const issue of result.issues){stats.issues[issue]=(stats.issues[issue]||0)+1;(stats.samples[issue]??=[]);if(stats.samples[issue].length<8)stats.samples[issue].push({id:row.id,type:row.type,content:row.content,options:row.options,answer:row.answer,normalized:result.record});}
   }
  }
  report.tables[table]=stats;
 }
 await db.end();fs.writeFileSync('/www/backup/codex/study-center-20261022/format-audit.json',JSON.stringify(report,null,2),{mode:0o600});
 console.log(JSON.stringify(Object.fromEntries(Object.entries(report.tables).map(([table,{samples,...stats}])=>[table,stats]))));
})().catch(e=>{console.error(e.code||'FORMAT_AUDIT_FAILED');process.exitCode=1});
