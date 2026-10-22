const https = require('node:https');
const dns = require('node:dns').promises;
const { isIP, BlockList } = require('node:net');
const DOMAINS = ['douyin.com', 'iesdouyin.com', 'kuaishou.com', 'gifshow.com', 'xhslink.com', 'xiaohongshu.com'];
const denied = new BlockList();
for (const [ip, prefix] of [['0.0.0.0',8],['10.0.0.0',8],['100.64.0.0',10],['127.0.0.0',8],['169.254.0.0',16],['172.16.0.0',12],['192.0.0.0',24],['192.0.2.0',24],['192.168.0.0',16],['198.18.0.0',15],['198.51.100.0',24],['203.0.113.0',24],['224.0.0.0',4],['240.0.0.0',4]]) denied.addSubnet(ip, prefix, 'ipv4');
const globalV6 = new BlockList(); globalV6.addSubnet('2000::', 3, 'ipv6');
const deniedV6 = new BlockList(); deniedV6.addSubnet('2001:db8::', 32, 'ipv6');
function isPublicAddress(address) {
    return isIP(address) === 4 ? !denied.check(address, 'ipv4') : isIP(address) === 6 && globalV6.check(address, 'ipv6') && !deniedV6.check(address, 'ipv6');
}
function validateShareUrl(input) {
    const url = new URL(input);
    if (url.protocol !== 'https:' || url.username || url.password || (url.port && url.port !== '443') ||
        !DOMAINS.some(domain => url.hostname === domain || url.hostname.endsWith('.' + domain))) throw new Error('不支持的分享链接');
    return url;
}
function createSafePageClient({ lookup = dns.lookup, request = https.request } = {}) {
    async function get(input, options = {}, redirects = 0, deadline = Date.now() + 20000) {
        const url = validateShareUrl(input);
        if (redirects > 4 || Date.now() >= deadline) throw new Error('分享链接跳转过多或超时');
        const addresses = await lookup(url.hostname, { all: true });
        if (!addresses.length || addresses.some(item => !isPublicAddress(item.address))) throw new Error('分享链接指向了不可访问的地址');
        return new Promise((resolve, reject) => {
            const req = request(url, {
                headers: { 'User-Agent': options.headers?.['User-Agent'] || 'Mozilla/5.0', 'Accept': 'text/html', 'Accept-Encoding': 'identity' },
                // Pin verified answers to the socket to prevent DNS rebinding.
                lookup: (_host, opts, callback) => opts.all ? callback(null, addresses) : callback(null, addresses[0].address, addresses[0].family),
            }, res => {
                res.on('error', reject);
                if ([301,302,303,307,308].includes(res.statusCode)) {
                    res.resume();
                    let next;
                    try { next = validateShareUrl(new URL(res.headers.location, url).href); }
                    catch (error) { reject(error); return; }
                    get(next.href, options, redirects + 1, deadline).then(resolve, reject);
                    return;
                }
                if (res.statusCode < 200 || res.statusCode >= 300) { res.resume(); reject(new Error('平台暂时无法访问')); return; }
                const chunks = []; let bytes = 0;
                res.on('data', chunk => {
                    bytes += chunk.length;
                    if (bytes > 2 * 1024 * 1024) req.destroy(new Error('平台响应过大'));
                    else chunks.push(chunk);
                });
                res.on('end', () => resolve({ data: Buffer.concat(chunks).toString('utf8'), request: { res: { responseUrl: url.href } } }));
            });
            req.setTimeout(Math.max(1, Math.min(10000, deadline - Date.now())), () => req.destroy(new Error('平台请求超时')));
            const timer = setTimeout(() => req.destroy(new Error('平台请求超时')), Math.max(1, deadline - Date.now()));
            req.on('close', () => clearTimeout(timer));
            req.on('error', reject);
            req.end();
        });
    }
    return { get };
}
module.exports = { validateShareUrl, isPublicAddress, createSafePageClient, ...createSafePageClient() };
