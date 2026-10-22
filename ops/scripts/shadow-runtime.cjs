// Preload for read-only route smoke tests on the isolated encrypted question copies.
const fs=require('node:fs');
const release='/root/study-security-release/server';
require(release+'/node_modules/dotenv').config({path:release+'/.env'});
const mysql=require(release+'/node_modules/mysql2/promise');
const map={questions:'codex_enc_questions_20261022',exam_paper_questions:'codex_enc_exams_20261022',question_search_tokens:'codex_enc_tokens_20261022',security_rate_limits:'codex_enc_limits_20261022',exam_records:'codex_enc_records_20261022'};
const rewrite=sql=>sql.replace(/\b(questions|exam_paper_questions|question_search_tokens|security_rate_limits|exam_records)\b/g,name=>map[name]);
const createPool=mysql.createPool.bind(mysql);
function patch(db){
 for(const method of ['query','execute']) {
  const original=db[method].bind(db);
  db[method]=async(sql,params=[])=>{
   if(typeof sql!=='string')throw new Error('Shadow only supports SQL strings');
   const old=sql;
   if(sql.includes('information_schema'))params=params.map(value=>map[value]||value);
   const result=await original(rewrite(sql),params);
   // mysql2 exposes origin names via getter-only properties; copy metadata explicitly.
   if(Array.isArray(result[1]))result[1]=result[1].map(field=>({
    ...field, name:field.name,orgName:field.orgName,
    orgTable:Object.entries(map).find(([,shadow])=>shadow===field.orgTable)?.[0]||field.orgTable,
   }));
   return result;
  };
 }
 if(db.getConnection){const get=db.getConnection.bind(db);db.getConnection=async()=>patch(await get());}
 return db;
}
mysql.createPool=(...args)=>patch(createPool(...args));
process.env.PORT='9899';
process.env.LISTEN_HOST='127.0.0.1';
