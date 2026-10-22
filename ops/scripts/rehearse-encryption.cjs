// Exact normalized production copies; this command never encrypts the live tables.
const fs=require('node:fs'),crypto=require('node:crypto'),path=require('node:path');
const root='/www/wwwroot/study_center',release='/root/study-security-release/server';
require(root+'/node_modules/dotenv').config({path:root+'/.env'});
const mysql=require(root+'/node_modules/mysql2/promise');
const backup='/www/backup/codex/study-center-20261022';
const keyFile=backup+'/question-encryption.key';
if(!fs.existsSync(keyFile))fs.writeFileSync(keyFile,crypto.randomBytes(32).toString('base64'),{mode:0o600,flag:'wx'});
process.env.QUESTION_ENCRYPTION_KEY=fs.readFileSync(keyFile,'utf8').trim();
const map={questions:'codex_enc_questions_20261022',exam_paper_questions:'codex_enc_exams_20261022',question_search_tokens:'codex_enc_tokens_20261022',security_rate_limits:'codex_enc_limits_20261022',exam_records:'codex_enc_records_20261022'};
const rewrite=sql=>sql.replace(/\b(questions|exam_paper_questions|question_search_tokens|security_rate_limits|exam_records)\b/g,name=>map[name]);
const pool=mysql.createPool({host:process.env.DB_HOST||'localhost',user:process.env.DB_USER,password:process.env.DB_PASSWORD,database:process.env.DB_NAME,connectionLimit:2});
function adapt(db){return {
 query(sql,params=[]){if(sql.includes('information_schema'))params=params.map(value=>map[value]||value);return db.query(rewrite(sql),params);},
 beginTransaction:()=>db.beginTransaction(),commit:()=>db.commit(),rollback:()=>db.rollback(),release:()=>db.release(),
 getConnection:async()=>adapt(await pool.getConnection())
};}
(async()=>{
 const start=Date.now();
 if(!process.argv.includes('--resume')) {
  for(const table of ['questions','exam_paper_questions','exam_records']) {
   const [present]=await pool.query('SHOW TABLES LIKE ?',[map[table]]);if(present.length)throw new Error('Encryption rehearsal copy exists; use --resume after inspection');
   await pool.query(`CREATE TABLE ${map[table]} LIKE ${table}`);
   await pool.query(`INSERT INTO ${map[table]} SELECT * FROM ${table}`);
  }
 }
 const {createStorageProtection}=require(release+'/scripts/protect_question_storage');
 let lastLog=0;
 await createStorageProtection(adapt(pool),{log(message){
  const n=Number((message.match(/migrated (\d+)/)||[])[1]||0);
  if(!n||n-lastLog>=1000){console.log(message);lastLog=n;}
  const stat=fs.statfsSync('/www');if(Number(stat.bavail)*Number(stat.bsize)<6*1024**3)throw new Error('Insufficient safe free space; live data untouched');
 }}).run({apply:true});
 const {openQuestion}=require(release+'/utils/questionCrypto');
 const report={seconds:(Date.now()-start)/1000,tables:{}};
 const normalized=JSON.parse(fs.readFileSync(backup+'/normalization-applied.json','utf8'));
 for(const table of ['questions','exam_paper_questions']) {
  let last=0,total=0;const hash=crypto.createHash('sha256');
  while(true){const [rows]=await pool.query(`SELECT id,type,content,options,answer,analysis FROM ${map[table]} WHERE id>? ORDER BY id LIMIT 500`,[last]);if(!rows.length)break;
   for(const row of rows){const plain=openQuestion(row.content,table,row.id);const digest=crypto.createHash('sha256').update(JSON.stringify([row.id,row.type,plain.content,plain.options,plain.answer,plain.analysis])).digest('hex');hash.update(digest);total++;last=row.id;}
  }
  const checksum=hash.digest('hex');if(checksum!==normalized.tables[table].after)throw new Error('Rehearsal plaintext fingerprint mismatch');
  report.tables[table]={total,checksum};
 }
 const [[size]]=await pool.query('SELECT SUM(data_length+index_length) bytes FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN (?)',[Object.values(map)]);
 report.bytes=size.bytes;
 fs.writeFileSync(backup+'/encryption-rehearsal.json',JSON.stringify(report,null,2),{mode:0o600});console.log(JSON.stringify(report));
})().catch(e=>{console.error(e.message);process.exitCode=1}).finally(()=>pool.end());
