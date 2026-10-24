// Isolated local UI fixtures. This server never connects to production APIs.
import {createRequire} from 'node:module';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
const root=path.resolve(import.meta.dirname,'../../apps/web');
process.chdir(root);
const require=createRequire(path.join(root,'package.json'));
const {questionTransport,protectedPath}=require('../server/services/questionTransport');
const {createServer}=await import(pathToFileURL(path.join(root,'node_modules/vite/dist/node/index.js')).href);
const user={id:1,username:'preview',nickname:'预览用户',role:'admin',subscription_end_date:'2099-01-01',created_at:'2026-10-01T00:00:00Z'};
const subjects=[{id:10,name:'计算机基础',major_id:1,question_count:121}];
const users=[user,{id:2,username:'student',role:'user',subscription_end_date:null,created_at:'2026-10-01'}];
const questions=Array.from({length:121},(_,i)=>({id:i+1,subject_id:10,subject_name:'计算机基础',is_active:1,type:'single',content:`第 ${i+1} 题：计算机中用于暂时存储运行数据的部件是？`,options:{A:'内存',B:'硬盘',C:'显示器',D:'键盘'},answer:'A',analysis:'内存用于暂时存储程序运行时的数据。',tags:['基础'],difficulty:1}));
const port=Number(process.env.STUDY_PREVIEW_PORT||4175);
const server=await createServer({root,server:{host:'127.0.0.1',port,strictPort:true},plugins:[{name:'isolated-ui-fixtures',enforce:'pre',transformIndexHtml(html){return html.replace('<head>','<head><script>localStorage.setItem("token","local-fixture");localStorage.setItem("user",'+JSON.stringify(JSON.stringify(user))+');</script>')},configureServer(server){
 server.middlewares.use((req,res,next)=>{
  if(!req.url.startsWith('/api/'))return next();
  const url=new URL(req.url,'http://localhost');const p=url.pathname;
  res.setHeader('Content-Type','application/json');
  if(!['GET','HEAD'].includes(req.method)){res.statusCode=405;return res.end(JSON.stringify({error:'预览仅允许读取，未发送任何修改请求'}))}
  let data=[];
  if(p==='/api/auth/me')data={user};
  else if(p==='/api/data/detailed-stats')data={subjectStats:[],typeStats:[],weekHeatmap:[],streak:0,today:{day:'2026-10-23',total:0,correct:0,accuracy:0}};
  else if(p==='/api/data/analysis')data={accuracy:0,total:0,correct:0,typeStats:[],weaknesses:[]};
  else if(['/api/admin/reports','/api/admin/comments'].includes(p))data={data:[],total:0,page:1};
  else if(p==='/api/admin/cards')data={data:[],pagination:{page:1,total:0,totalPages:1,limit:20}};
  else if(p==='/api/admin/articles')data=[];
  else if(p.endsWith('/users'))data=users;
  else if(p.endsWith('/subjects'))data=subjects;
  else if(p.endsWith('/majors'))data=[{id:1,name:'计算机类'}];
  else if(p==='/api/data/questions'){
   const page=Number(url.searchParams.get('page')||1); data=questions.slice((page-1)*50,page*50);
   res.setHeader('X-Question-Has-More',String(page*50<121));res.setHeader('X-Question-Access','full');
  }
  else if(p==='/api/admin/questions')data={data:questions.slice(0,10),pagination:{page:1,total:121,totalPages:13,limit:10}};
  else if(p.endsWith('/progress'))data=[];
  else if(p.endsWith('/settings'))data={registration_open:'true'};
  else if(p.endsWith('/home-data'))data={settings:{},countdowns:[],externalLinks:[]};
  else if(p.endsWith('/stats'))data={totalQuestions:121,totalUsers:2};
  else if(p.includes('/articles'))data={data:[],total:0,pagination:{page:1,total:0,totalPages:1}};
  if(protectedPath(req.url)) {
   req.originalUrl=req.url;req.get=name=>req.headers[name.toLowerCase()];
   res.set=(name,value)=>{res.setHeader(name,value);return res};res.status=value=>{res.statusCode=value;return res};
   res.json=body=>res.end(JSON.stringify(body));
   questionTransport(req,res,()=>res.json(data));
  } else res.end(JSON.stringify(data));
 });
}}]});
await server.listen();console.log(`Isolated UI preview http://127.0.0.1:${port}`);
