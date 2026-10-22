// Only read requests and rejected mutations; never redeem, submit, reset or delete live user data.
const fs=require('node:fs');
const root='/www/wwwroot/study_center',release='/root/study-security-release/server';
require(root+'/node_modules/dotenv').config({path:process.argv.includes('--live')?root+'/.env':release+'/.env'});
const mysql=require(root+'/node_modules/mysql2/promise'),jwt=require(root+'/node_modules/jsonwebtoken');
const live=process.argv.includes('--live'),base=live?'https://studyapi.zhzgo.cn':'http://127.0.0.1:9899';
const results=[];
const testIp='198.51.100.'+(1+Math.floor(Math.random()*254));
async function check(name,url,{id,method='GET',body,expected=200}={}) {
 const headers={'Content-Type':'application/json','X-Forwarded-For':testIp};
 if(id)headers.Authorization='Bearer '+jwt.sign({id,role:'admin',subscription_end_date:'2099-01-01'},process.env.JWT_SECRET,{expiresIn:'3m'});
 if(!live)headers['X-Forwarded-Proto']='https';
 const start=Date.now();const response=await fetch(base+url,{method,headers,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(20000)});
 let data;try{data=await response.json()}catch{data=null;}
 const ok=[].concat(expected).includes(response.status);
 results.push({name,status:response.status,ok,ms:Date.now()-start,count:Array.isArray(data)?data.length:undefined});
 if(!ok)throw new Error(name+' returned '+response.status);
 return {data,response};
}
(async()=>{
 const db=await mysql.createConnection({host:process.env.DB_HOST||'localhost',user:process.env.DB_USER,password:process.env.DB_PASSWORD,database:process.env.DB_NAME});
 try{
 const [users]=await db.query('SELECT id,role,subscription_end_date FROM users ORDER BY id');
 const free=users.find(u=>u.role==='user'&&(!u.subscription_end_date||new Date(u.subscription_end_date)<=new Date()));
 const paid=users.find(u=>u.role==='user'&&new Date(u.subscription_end_date)>new Date());
 const admin=users.find(u=>u.role==='admin');
 const [[subject]]=await db.query('SELECT subject_id id FROM questions WHERE is_active=1 GROUP BY subject_id HAVING COUNT(*)>50 ORDER BY COUNT(*) DESC LIMIT 1');
 const publicHome=await check('public settings','/api/public/home-data');
 if(Object.keys(publicHome.data.settings||{}).some(k=>/smtp|secret|password|private/i.test(k)))throw new Error('Public settings leak');
 await check('anonymous questions','/api/data/questions?subject_id='+subject.id,{expected:401});
 await check('anonymous admin','/api/admin/questions',{expected:401});
 await check('anonymous upload','/api/upload',{method:'POST',expected:401});
 await check('bot private credential','/api/bot/subjects',{expected:401});
 const forged=jwt.sign({id:free?.id||1,role:'admin',subscription_end_date:'2099-01-01'},'definitely-not-the-server-key',{expiresIn:'2m'});
 const forgedResponse=await fetch(base+'/api/admin/questions',{headers:{Authorization:'Bearer '+forged,'X-Forwarded-Proto':'https'}});
 results.push({name:'forged admin token',status:forgedResponse.status,ok:forgedResponse.status===401});
 if(forgedResponse.status!==401)throw new Error('Forged token accepted');
 for(const [label,user]of [['free',free],['paid',paid],['admin',admin]]) {
  if(!user)continue;
  const [[allowed]]=await db.query(`SELECT q.subject_id id FROM questions q JOIN subjects s ON s.id=q.subject_id WHERE q.is_active=1 AND (?='admin' OR NOT EXISTS(SELECT 1 FROM major_access_control WHERE user_id=?) OR EXISTS(SELECT 1 FROM major_access_control WHERE user_id=? AND major_id=s.major_id)) GROUP BY q.subject_id HAVING COUNT(*)>50 ORDER BY COUNT(*) DESC LIMIT 1`,[user.role,user.id,user.id]);
  if(!allowed)throw new Error('No subject available for '+label);
  const q=await check(label+' paginated questions','/api/data/questions?subject_id='+allowed.id+'&limit=99999',{id:user.id});
  if(q.data.length===0)throw new Error('Allowed question page was empty for '+label);
  if(q.data.length>50)throw new Error('Page cap failed');
  if(label==='free'&&q.data.length>20)throw new Error('Free preview bypass');
  if(q.data.some(q=>String(q.content).startsWith('zhzgo:gcm:')||typeof q.options!=='object'))throw new Error('Question decode failed');
  if(label==='free'&&q.data.length){
   const [[beyond]]=await db.query('SELECT id FROM questions WHERE subject_id=? AND is_active=1 ORDER BY id LIMIT 1 OFFSET 25',[allowed.id]);
   await check('free direct-id boundary','/api/data/questions/'+beyond.id,{id:user.id,expected:403});
  }
  await check(label+' favorites','/api/data/favorites/practice?limit=2',{id:user.id});
  await check(label+' mistakes','/api/data/mistakes?limit=2',{id:user.id});
  await check(label+' progress','/api/data/progress',{id:user.id});
  await check(label+' stats','/api/data/user-stats',{id:user.id});
  await check(label+' exam directory','/api/data/exams',{id:user.id});
  await check(label+' exam history','/api/data/exams/records',{id:user.id});
  if(label==='free')await check('free account cannot open admin', '/api/admin/questions',{id:user.id,expected:403});
 }
 if(paid){
  await check('authorized search','/api/data/questions/search?keyword='+encodeURIComponent('计算机')+'&limit=2',{id:paid.id});
  await check('invalid pagination','/api/data/questions?subject_id='+subject.id+'&page=-1',{id:paid.id,expected:400});
  const profile=await check('public profile privacy','/api/data/user/'+paid.id);
  if('email'in profile.data.user||'qq_openid'in profile.data.user)throw new Error('Public profile leaks private account fields');
 }
 if(admin){
  const adminQuestions=await check('admin question console','/api/admin/questions?limit=2',{id:admin.id});
  const rows=adminQuestions.data?.data;
  if(!Array.isArray(rows)||rows.some(q=>String(q.content).startsWith('zhzgo:gcm:')||typeof q.options!=='object'))throw new Error('Admin question decode failed');
  await check('admin stats','/api/admin/stats',{id:admin.id});
  await check('admin users','/api/admin/users?limit=2',{id:admin.id});
  await check('admin exams','/api/admin/exams',{id:admin.id});
  await check('admin settings','/api/admin/settings',{id:admin.id});
  await check('admin export credentials','/api/admin/exports?limit=2',{id:admin.id});
 const integrations=await check('admin integrations','/api/admin/integrations',{id:admin.id});
 if('ai_api_key'in integrations.data||'geetest_web_key'in integrations.data)throw new Error('Integration secrets leaked');
 }
 const demo=users.find(u=>u.role==='demo');
 if(demo){
  await check('demo cannot read export credentials','/api/admin/exports',{id:demo.id,expected:403});
  await check('demo cannot read integrations','/api/admin/integrations',{id:demo.id,expected:403});
 }
 const authConfig=await check('public auth config','/api/public/auth-config');
 if(JSON.stringify(authConfig.data).match(/secret|api_key|web_key/i))throw new Error('Public auth config leaks secrets');
 await check('anonymous export','/api/data/export/questions',{method:'POST',body:{subject_id:subject.id,password:'AAAA-BBBB-CCCC-DDDD'},expected:401});
 if(paid){
  await check('export rejects wrong password','/api/data/export/questions',{id:paid.id,method:'POST',body:{subject_id:subject.id,password:'AAAA-BBBB-CCCC-DDDD'},expected:403});
 }
 await check('resource categories','/api/data/resources/categories');
 await check('resource directory','/api/data/resources/articles?limit=2');
 await check('app update','/api/app/check_update?versionCode=112');
 const webPages=['/','/login','/register','/download','/score-query','/agreement','/privacy','/export'];
 for(const page of webPages){
  const response=await fetch('https://study.zhzgo.cn'+page,{signal:AbortSignal.timeout(20000)});
  const html=await response.text();
  const ok=response.status===200&&html.includes('id="app"');
  results.push({name:'web '+page,status:response.status,ok});
  if(!ok)throw new Error('Web page '+page+' failed');
 }
 console.log(JSON.stringify({ok:true,checks:results}));
 fs.writeFileSync(process.env.STUDY_SMOKE_REPORT || '/www/backup/codex/study-center-20261022/'+(live?'live-api-smoke.json':'shadow-api-smoke.json'),JSON.stringify({time:new Date().toISOString(),checks:results},null,2),{mode:0o600});
 }finally{await db.end();}
})().catch(error=>{console.log(JSON.stringify({ok:false,error:error.message,checks:results}));process.exitCode=1});
