// Read every question; print shapes and a bounded set of content examples, never config.
const fs = require('node:fs');
const root = '/www/wwwroot/study_center';
require(root + '/node_modules/dotenv').config({ path: root + '/.env' });
const mysql = require(root + '/node_modules/mysql2/promise');
function unpack(value) {
  let depth = 0;
  while (typeof value === 'string' && value.trim() && depth < 5) {
    try { const next = JSON.parse(value); if (next === value) break; value = next; depth++; }
    catch { break; }
  }
  return { value, depth };
}
function shape(raw) {
  const { value, depth } = unpack(raw);
  return `${depth}:${value === null ? 'null' : Array.isArray(value) ? 'array:' + [...new Set(value.map(v => v === null ? 'null' : typeof v === 'object' ? Object.keys(v).sort().join(',') : typeof v))].join('|') : typeof value === 'object' ? 'object:' + Object.keys(value).sort().join(',').slice(0,180) : typeof value}`;
}
(async () => {
  const db = await mysql.createConnection({host:process.env.DB_HOST||'localhost',user:process.env.DB_USER,password:process.env.DB_PASSWORD,database:process.env.DB_NAME});
  const output = {};
  for (const table of ['questions','exam_paper_questions']) {
    const counts = {}, examples = {}, summary = { total:0, maxId:0, blankContent:0, blankAnswer:0, longContent:0, optionCharacters:0 };
    let id = 0;
    while (true) {
      const [rows] = await db.query(`SELECT id,type,content,options,answer,analysis FROM ${table} WHERE id>? ORDER BY id LIMIT 500`,[id]);
      if (!rows.length) break;
      for (const row of rows) {
        summary.total++; summary.maxId = id = row.id;
        if (!String(row.content||'').trim()) summary.blankContent++;
        if (!String(row.answer||'').trim()) summary.blankAnswer++;
        if (String(row.content||'').length>10000) summary.longContent++;
        summary.optionCharacters += String(row.options||'').length;
        const key = [row.type,shape(row.content),shape(row.options),shape(row.answer),shape(row.analysis)].join(' / ');
        counts[key] = (counts[key]||0)+1;
        if (!examples[key] && Object.keys(examples).length<100) examples[key] = {id:row.id,type:row.type,content:String(row.content).slice(0,450),options:row.options,answer:row.answer,analysis:String(row.analysis||'').slice(0,350)};
      }
    }
    output[table]={summary,counts,examples};
  }
  await db.end();
  const dir='/www/backup/codex/study-center-20261022';
  fs.writeFileSync(dir+'/format-profile.json',JSON.stringify(output,null,2),{mode:0o600});
  console.log(JSON.stringify(Object.fromEntries(Object.entries(output).map(([k,v])=>[k,{summary:v.summary,counts:v.counts}]))));
})().catch(e=>{console.error(e.code||'PROFILE_FAILED');process.exitCode=1});
