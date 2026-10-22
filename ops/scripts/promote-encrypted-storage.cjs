// Promotes a completely verified encrypted copy in one transaction. Run with API stopped.
const fs=require('node:fs'),crypto=require('node:crypto');
const root='/www/wwwroot/study_center',release='/root/study-security-release/server';
require(root+'/node_modules/dotenv').config({path:release+'/.env'});
const mysql=require(root+'/node_modules/mysql2/promise');
const dir='/www/backup/codex/study-center-20261022';
const pairs={questions:'codex_enc_questions_20261022',exam_paper_questions:'codex_enc_exams_20261022'};
const cols=['type','content','options','answer','analysis'];
const fingerprint=row=>crypto.createHash('sha256').update(JSON.stringify([row.id,...cols.map(k=>row[k])])).digest('hex');
async function digest(db,table){let last=0,total=0;const hash=crypto.createHash('sha256');while(true){const [rows]=await db.query(`SELECT id,type,content,options,answer,analysis FROM ${table} WHERE id>? ORDER BY id LIMIT 500 FOR UPDATE`,[last]);if(!rows.length)break;for(const row of rows){hash.update(fingerprint(row));last=row.id;total++;}}return{total,checksum:hash.digest('hex')};}
(async()=>{
 const db=await mysql.createConnection({host:process.env.DB_HOST||'localhost',user:process.env.DB_USER,password:process.env.DB_PASSWORD,database:process.env.DB_NAME});
 try{
 const normalized=JSON.parse(fs.readFileSync(dir+'/normalization-applied.json','utf8'));
 const rehearsal=JSON.parse(fs.readFileSync(dir+'/encryption-rehearsal.json','utf8'));
 if(!JSON.parse(fs.readFileSync(dir+'/shadow-api-smoke.json','utf8')).checks.every(c=>c.ok))throw new Error('Shadow API checks required');
 const counts={};for(const table of ['users','user_progress','user_favorites','exam_records','activation_cards','exam_scores','articles','article_comments']){
  const [[r]]=await db.query(`SELECT COUNT(*) n,COALESCE(SUM(id),0) ids FROM ${table}`);counts[table]=r;
 }
 // DDL preserves all foreign keys and IDs. Every step is resumable after an interrupted run.
 for(const table of Object.keys(pairs))await db.query(`ALTER TABLE ${table} MODIFY COLUMN content MEDIUMTEXT`);
 const [previewIndex]=await db.query("SHOW INDEX FROM questions WHERE Key_name = 'idx_question_preview'");
 if(!previewIndex.length)await db.query('ALTER TABLE questions ADD INDEX idx_question_preview (subject_id,is_active,id)');
 await db.query('CREATE TABLE IF NOT EXISTS question_search_tokens LIKE codex_enc_tokens_20261022');
 await db.query('CREATE TABLE IF NOT EXISTS security_rate_limits LIKE codex_enc_limits_20261022');
 const [[limitsCount]]=await db.query('SELECT COUNT(*) n FROM security_rate_limits');
 const [[limitsCopyCount]]=await db.query('SELECT COUNT(*) n FROM codex_enc_limits_20261022');
 if(Number(limitsCount.n)===0&&Number(limitsCopyCount.n)>0)await db.query('INSERT INTO security_rate_limits SELECT * FROM codex_enc_limits_20261022');
 const [[tokenCount]]=await db.query('SELECT COUNT(*) n FROM question_search_tokens');
 if(Number(tokenCount.n)===0){
  const [copyExists]=await db.query('SHOW TABLES LIKE "codex_enc_tokens_20261022"');
  if(copyExists.length)await db.query('RENAME TABLE question_search_tokens TO codex_empty_tokens_20261022, codex_enc_tokens_20261022 TO question_search_tokens');
 }
 await db.beginTransaction();
 try{
  for(const [table,copy]of Object.entries(pairs)){
   const live=await digest(db,table);
   if(live.total!==normalized.tables[table].total||live.checksum!==normalized.tables[table].after)throw new Error('Live normalized data changed: '+table);
   const [[copyCount]]=await db.query(`SELECT COUNT(*) n FROM ${copy}`);
   if(copyCount.n!==rehearsal.tables[table].total)throw new Error('Encrypted copy count mismatch');
   await db.query(`UPDATE ${table} live JOIN ${copy} protected ON live.id=protected.id SET live.content=protected.content,live.options=protected.options,live.answer=protected.answer,live.analysis=protected.analysis`);
   const [[invalid]]=await db.query(`SELECT COUNT(*) n FROM ${table} WHERE content NOT LIKE 'zhzgo:gcm:v1:%' OR answer<>'' OR COALESCE(analysis,'')<>''`);
   if(invalid.n)throw new Error('Unencrypted record remains');
  }
  // Preserve all historical grades and question positions while removing duplicated standard answers.
  const [records]=await db.query('SELECT id,answers FROM exam_records FOR UPDATE');
  for(const record of records){let answers=record.answers;if(typeof answers==='string'){try{answers=JSON.parse(answers)}catch{throw new Error('Invalid exam snapshot')}}
   if(answers==null)continue;if(!Array.isArray(answers))throw new Error('Unexpected exam snapshot');
   const clean=answers.map(answer=>Object.fromEntries(Object.entries(answer).filter(([key])=>!['correct_answer','analysis','content','options','answer'].includes(key))));
   await db.query('UPDATE exam_records SET answers=? WHERE id=?',[JSON.stringify(clean),record.id]);
  }
  for(const table of Object.keys(counts)){const [[r]]=await db.query(`SELECT COUNT(*) n,COALESCE(SUM(id),0) ids FROM ${table}`);if(JSON.stringify(r)!==JSON.stringify(counts[table]))throw new Error('Account/user data changed: '+table);}
  await db.commit();
 }catch(error){await db.rollback();throw error;}
 fs.writeFileSync(dir+'/encryption-promoted.json',JSON.stringify({time:new Date().toISOString(),counts,questions:rehearsal.tables},null,2),{mode:0o600});
 console.log('ENCRYPTED_STORAGE_PROMOTED_WITH_COUNTS_VERIFIED');
 }finally{await db.end()}
})().catch(error=>{console.error(error.message);process.exitCode=1});
