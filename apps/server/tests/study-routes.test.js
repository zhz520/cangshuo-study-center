const {test}=require('node:test');
const assert=require('node:assert/strict');
const express=require('express');
const {PassThrough}=require('node:stream');
const {IncomingMessage,ServerResponse}=require('node:http');
const {dayKey}=require('../services/studyStats');

process.env.JWT_SECRET='isolated-study-route-access-'.repeat(3);
process.env.JWT_REFRESH_SECRET='isolated-study-route-refresh-'.repeat(3);
const statements=[];
let rankingRows=[];
const db={async query(sql,params){
    statements.push({sql,params});
    if(sql.includes('CREATE TABLE'))return [{affectedRows:0}];
    if(sql.includes('LIMIT ? OFFSET ?'))return [rankingRows];
    if(sql.includes('COUNT(DISTINCT DATE'))return [[{total:12,correct:9,active_days:3}]];
    if(sql.includes('SELECT s.id'))return [[{id:10,subject_name:'计算机基础',total:12,correct:9}]];
    if(sql.includes('SELECT q.type'))return [[{type:'single',total:12,correct:9}]];
    if(sql.includes('SELECT DISTINCT DATE_FORMAT'))return [[{day:dayKey()}]];
    if(sql.includes('DATE_FORMAT(up.updated_at'))return [[{day:dayKey(),count:4,correct:3}]];
    throw new Error('Unexpected fixture query');
}};
function mock(path,exports){const id=require.resolve(path);require.cache[id]={id,filename:id,loaded:true,exports};}
mock('../config/db',db);
mock('../middleware/authMiddleware',{verifyToken:(req,_res,next)=>{req.userId=7;next();}});
mock('../services/notificationService',{sendPushNotification:async()=>{}});
mock('../services/emailService',{});
const app=express();app.use('/data',require('../routes/data'));
function get(path){return new Promise((resolve,reject)=>{
    const req=new IncomingMessage(new PassThrough());req.url=path;req.method='GET';req.headers={};
    const res=new ServerResponse(req);res.end=data=>{resolve({status:res.statusCode,body:JSON.parse(String(data))});return res;};
    app.handle(req,res,e=>reject(e||new Error('Unmatched route')));
});}

test('home statistics use the same active records, calendar date and accuracy as analytics',async()=>{
    statements.length=0;
    const result=await get('/data/detailed-stats');
    assert.equal(result.status,200);
    assert.deepEqual(result.body.today,{day:dayKey(),total:4,correct:3,accuracy:75});
    assert.equal(result.body.streak,1);
    assert.equal(result.body.subjectStats[0].accuracy,75);
    for(const {sql,params} of statements){
        assert.match(sql,/up\.is_deleted = FALSE/);
        assert.match(sql,/q\.is_active = 1/);
        assert.deepEqual(params,[7]);
        assert.doesNotMatch(sql,/(?<!\.)\bDATE_FORMAT\(updated_at/);
    }
    const overall=await get('/data/user-stats');
    assert.deepEqual(overall.body,{active_days:3,total_questions:12,accuracy:75});
});
test('all three ranking modes paginate consistently, break ties, and exclude deleted progress',async()=>{
    rankingRows=[{user_id:9,username:'fixture',score:75}];
    for(const type of ['total','accuracy','score']){
        statements.length=0;
        const result=await get(`/data/leaderboard?type=${type}&subject_id=10&page=2&limit=50`);
        assert.equal(result.status,200);
        assert.equal(result.body[0].rank,51);
        const {sql,params}=statements.at(-1);
        assert.deepEqual(params,['10',50,50]);
        assert.match(sql,/up\.is_deleted = FALSE/);
        assert.match(sql,/up\.user_id ASC/);
        assert.match(sql,/q\.subject_id = \?/);
    }
});
test('invalid ranking pages are rejected before querying the database',async()=>{
    statements.length=0;
    assert.equal((await get('/data/leaderboard?page=-1')).status,400);
    assert.equal(statements.length,0);
});
