// Extract existing runtime secrets in memory. Print presence flags only.
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),crypto=require('node:crypto');
const root='/www/wwwroot/study_center',release='/root/study-security-release/server';
const dotenv=require(root+'/node_modules/dotenv');
const env=dotenv.parse(fs.readFileSync(root+'/.env'));
const source=fs.readFileSync(root+'/services/geetestService.js','utf8');
const match=source.match(/const CONFIGS\s*=\s*(\{[\s\S]*?\n\});/);
if(!match)throw new Error('Unable to read existing Geetest configuration');
const geetest=vm.runInNewContext('('+match[1]+')',Object.create(null),{timeout:100});
for(const name of ['web','android'])for(const field of ['id','key']) {
 const key='GEETEST_'+name.toUpperCase()+'_'+field.toUpperCase();
 env[key] ||= geetest[name][field];
}
env.QUESTION_ENCRYPTION_KEY=fs.readFileSync('/www/backup/codex/study-center-20261022/question-encryption.key','utf8').trim();
env.BOT_API_SECRET ||= crypto.randomBytes(48).toString('base64');
env.NODE_ENV='production';env.TRUSTED_PROXIES='loopback';env.LISTEN_HOST='127.0.0.1';
env.QQ_LOGIN_APP_ID ||= '102861787';
// Retain the currently configured AI credential; rotate separately after verification.
for(const file of ['routes/data.js','routes/public.js']) {
 const text=fs.readFileSync(path.join(root,file),'utf8');
 const key=text.match(/sk-[A-Za-z0-9]{20,}/)?.[0];
 if(key&&!env.DEEPSEEK_API_KEY)env.DEEPSEEK_API_KEY=key;
}
if(!env.DEEPSEEK_API_KEY)console.log('AI_KEY_NOT_FOUND');
const output=Object.entries(env).map(([key,value])=>{
 const text=String(value);
 if(/[\r\n']/.test(text))throw new Error('Environment value requires special quoting: '+key);
 return key+"='"+text+"'";
}).join('\n')+'\n';
fs.writeFileSync(release+'/.env',output,{mode:0o600});
fs.writeFileSync('/www/backup/codex/study-center-20261022/release.env',output,{mode:0o600});
fs.mkdirSync(release+'/config',{recursive:true});
fs.copyFileSync(root+'/config/jpush-config.json',release+'/config/jpush-config.json');
console.log(JSON.stringify({configured:['GEETEST_WEB_ID','GEETEST_WEB_KEY','GEETEST_ANDROID_ID','GEETEST_ANDROID_KEY','QUESTION_ENCRYPTION_KEY','BOT_API_SECRET','DEEPSEEK_API_KEY'].map(key=>({key,present:!!env[key]}))}));
