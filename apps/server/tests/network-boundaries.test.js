const {test}=require('node:test');
const assert=require('node:assert/strict');
const {EventEmitter}=require('node:events');
const {PassThrough}=require('node:stream');
const {validateShareUrl,isPublicAddress,createSafePageClient}=require('../utils/safePageFetch');
test('share URL host validation rejects spoofing, userinfo, private literals and alternate protocols',()=>{
 for(const url of ['http://v.douyin.com/a','https://v.douyin.com.evil.test/a','https://127.0.0.1/?douyin.com','https://douyin.com@evil.test/a','https://v.douyin.com:8443/a','file:///douyin.com'])assert.throws(()=>validateShareUrl(url));
 assert.equal(validateShareUrl('https://v.douyin.com/one/').hostname,'v.douyin.com');
 for(const ip of ['127.0.0.1','10.1.2.3','169.254.169.254','172.31.1.1','192.168.1.1','100.64.0.1','::1','::ffff:127.0.0.1','fc00::1'])assert.equal(isPublicAddress(ip),false,ip);
 assert.equal(isPublicAddress('8.8.8.8'),true);
});
test('DNS private addresses are rejected before any network request',async()=>{
 let calls=0;const client=createSafePageClient({lookup:async()=>[{address:'127.0.0.1',family:4}],request(){calls++;}});
 await assert.rejects(client.get('https://v.douyin.com/a'));assert.equal(calls,0);
});
test('redirects are revalidated and the actual socket uses the verified DNS answer',async()=>{
 let calls=0,pinned;
 const client=createSafePageClient({lookup:async()=>[{address:'8.8.8.8',family:4}],request(url,opts,callback){
  calls++;opts.lookup(url.hostname,{},(error,address)=>{assert.ifError(error);pinned=address;});
  const req=new EventEmitter();req.setTimeout=()=>{};req.destroy=error=>req.emit('error',error);
  req.end=()=>queueMicrotask(()=>{const response=new PassThrough();response.statusCode=302;response.headers={location:'https://127.0.0.1/private'};callback(response);response.end();req.emit('close');});
  return req;
 }});
 await assert.rejects(client.get('https://v.douyin.com/a'));assert.equal(calls,1);assert.equal(pinned,'8.8.8.8');
});
test('upload signature checks do not trust SVG, HTML or client filenames',()=>{
 process.env.JWT_SECRET='test-access-secret-'.repeat(3);process.env.JWT_REFRESH_SECRET='test-refresh-secret-'.repeat(3);
 require.cache[require.resolve('../config/db')]={id:require.resolve('../config/db'),loaded:true,exports:{}};
 const {imageType}=require('../routes/upload');
 assert.equal(imageType(Buffer.from('<svg onload="alert(1)"></svg>')),null);
 assert.equal(imageType(Buffer.from('<html><script>alert(1)</script></html>')),null);
 const png=Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),Buffer.alloc(4),Buffer.from('IHDR'),Buffer.alloc(20)]);
 assert.equal(imageType(png).ext,'png');assert.equal(imageType(Buffer.from('GIF89a0000000000')).ext,'gif');
});
