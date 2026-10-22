const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const files = {
  '/': ['core-review.html', 'text/html; charset=utf-8'],
  '/core-review.html': ['core-review.html', 'text/html; charset=utf-8'],
  '/assets/study-landscape.png': ['assets/study-landscape.png', 'image/png'],
};

http.createServer((req, res) => {
  const entry = files[req.url.split('?')[0]];
  if (!entry) { res.writeHead(404); return res.end(); }
  const target = path.join(__dirname, entry[0]);
  res.writeHead(200, { 'Content-Type': entry[1], 'Cache-Control': 'no-store' });
  fs.createReadStream(target).pipe(res);
}).listen(4318, '127.0.0.1', () => console.log('Preview: http://127.0.0.1:4318/'));
