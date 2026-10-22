const axios = require('./safePageFetch');
const { validateShareUrl } = require('./safePageFetch');

/**
 * 提取文本中的 URL
 */
function extractUrl(text) {
    const urlMatch = text.match(/https?:\/\/[^\s]+/);
    return urlMatch ? urlMatch[0] : null;
}

/**
 * 解析抖音无水印视频 (基于公开接口或页面解析提取)
 * 注意：视频平台接口经常变化，以下作为基础参考实现，生产环境中通常建议对接稳定的第三方免签 API。
 */
async function parseDouyin(shareUrl) {
    try {
        // 1. 访问短连接获取重定向后的真实 ID
        const redirectRes = await axios.get(shareUrl, {
            headers: {
                'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1'
            },
            maxRedirects: 5
        });

        const finalUrl = redirectRes.request.res.responseUrl;
        
        // 尝试从 URL 提取视频 ID
        let videoId = '';
        const videoIdMatch = finalUrl.match(/video\/(\d+)/);
        if (videoIdMatch) {
            videoId = videoIdMatch[1];
        }

        // 2. 核心去水印逻辑：请求网页本身提取 JSON 数据 (应对官方接口风控)
        const htmlRes = await axios.get(finalUrl, {
            headers: {
                'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1',
                'Accept': 'text/html,application/xhtml+xml,application/xml;'
            }
        });

        const html = htmlRes.data;
        let dataJson = '';
        
        // 匹配 RENDER_DATA 或者 window._ROUTER_DATA
        const matchRender = html.match(/<script id="RENDER_DATA" type="application\/json">([^<]+)<\/script>/s);
        if (matchRender) {
            dataJson = decodeURIComponent(matchRender[1]);
        } else {
            const matchRouter = html.match(/window\._ROUTER_DATA\s*=\s*(\{.*?\})\s*<\/script>/s);
            if (matchRouter) {
                dataJson = matchRouter[1];
            }
        }

        if (dataJson) {
            const data = JSON.parse(dataJson);
            // 抖音的页面结构通常包含 loaderData['video_(id)/page'].videoInfoRes.item_list
            // 但路径的 (id) 会变，所以我们遍历 loaderData 找到包含 videoInfoRes 的 key
            let item = null;
            if (data.loaderData) {
                for (const key in data.loaderData) {
                    if (data.loaderData[key] && data.loaderData[key].videoInfoRes && data.loaderData[key].videoInfoRes.item_list) {
                        item = data.loaderData[key].videoInfoRes.item_list[0];
                        break;
                    }
                }
            }

            if (item) {
                const result = {
                    platform: 'douyin',
                    title: item.desc,
                    author: item.author ? item.author.nickname : 'Unknown'
                };

                // 判断是否是图集
                if (item.images && item.images.length > 0) {
                    result.type = 'image';
                    result.images = item.images.map(img => img.url_list[0]);
                    result.coverUrl = result.images[0];
                } else if (item.video && item.video.play_addr && item.video.play_addr.url_list) {
                    result.type = 'video';
                    let videoUrl = item.video.play_addr.url_list[0];
                    videoUrl = videoUrl.replace('playwm', 'play');
                    result.videoUrl = videoUrl;
                    
                    if (item.video.cover && item.video.cover.url_list) {
                        result.coverUrl = item.video.cover.url_list[0];
                    }
                } else {
                    throw new Error('未找到支持的视频或图集节点。');
                }

                return result;
            } else {
                 throw new Error('成功提取页面配置，但未找到对应的媒体节点。');
            }
        } else {
             // 如果正则没有匹配到，说明页面结构大改
            throw new Error('平台页面结构已更新，无法提取视频配置。');
        }

    } catch (e) {
        console.error('Douyin parse error:', e.message);
        throw new Error('解析抖音链接失败: ' + e.message);
    }
}

// ====== Lightweight Kuaishou Extraction (No-Puppeteer) ======
async function extractKuaishou(url) {
    try {
        const res = await axios.get(url, {
            headers: {
                'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1',
                'Cookie': 'did=web_8c18bd5d68a773c4f213a17074b82105'
            },
            timeout: 10000,
            maxRedirects: 5
        });

        const html = res.data;

        // 1. Extract Title (common for both)
        let title = '快手分享内容';
        const matchTitle = html.match(/<title>([^<]+)<\/title>/);
        if (matchTitle) {
            title = matchTitle[1];
        }

        // 2. Identify and extract Video content first
        // SSR often puts it in a script tag or as a direct mp4 link
        const matchMp4 = html.match(/"srcNoMark"\s*:\s*"([^"]+)"/) || 
                         html.match(/"photoUrl"\s*:\s*"([^"]+)"/) ||
                         html.match(/https?:\/\/[^"'\\]+\.mp4[^"'\\]*/);
        
        if (matchMp4) {
            let videoUrl = matchMp4[1] || matchMp4[0];
            videoUrl = videoUrl.replace(/\\u002F/g, '/');
            // If it's a direct mp4 link and contains "upic", it might be a cover, 
            // but usually in SSR context, these explicit markers are the main content.
            return { type: 'video', videoUrl, title };
        }

        // 3. Fallback to Gallery if no video URL is found
        const isGallery = html.includes('"page":"PHOTO_SHARE_PAGE"') || html.includes('"atlas"');
        if (isGallery) {
            const imageMatches = html.match(/https?:\/\/[^"'\\]+\/(?:upic|ufile\/atlas)\/[^"'\\]+\.(?:webp|jpg|jpeg|png)(?:\?[^"'\\]*)?/g);
            if (imageMatches && imageMatches.length > 0) {
                const images = [...new Set(imageMatches)].filter(img => !img.includes('kwai-icon'));
                if (images.length > 0) {
                    return { type: 'image', images, title };
                }
            }
        }

        // 4. Final Fallback for any mp4 found in the page (redundant but safe)
        const fallbackMp4 = html.match(/https?:\/\/[^"'\\]+\.mp4[^"'\\]*/);
        if (fallbackMp4) {
            return { type: 'video', videoUrl: fallbackMp4[0].replace(/\\u002F/g, '/'), title };
        }

        throw new Error('未能从页面提取到媒体资源，可能触发了风控或页面结构变更。');
    } catch (e) {
        throw new Error('快手解析失败: ' + e.message);
    }
}

// ====== Lightweight Xiaohongshu Extraction (No-Puppeteer) ======
async function extractXiaohongshu(url) {
    try {
        const res = await axios.get(url, {
            headers: {
                'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1'
            },
            timeout: 10000
        });
        const html = res.data;
        const matchState = html.match(/window\.__INITIAL_STATE__=([^<]+)<\/script>/s);
        if (matchState) {
            const jsonText = matchState[1].replace(/undefined/g, 'null').replace(/;$/, '');
            const data = JSON.parse(jsonText);
            if (data.note && data.note.noteDetailMap) {
                const keys = Object.keys(data.note.noteDetailMap);
                if (keys.length > 0) {
                    const note = data.note.noteDetailMap[keys[0]].note;
                    const title = note.title || note.desc || '小红书分享内容';
                    if (note.type === 'video' && note.video?.media?.stream?.h264) {
                        return { type: 'video', videoUrl: note.video.media.stream.h264[0].masterUrl, title };
                    } else if (note.type === 'normal' && note.imageList) {
                        return { type: 'image', images: note.imageList.map(img => img.urlDefault || img.url), title };
                    }
                }
            }
        }
        throw new Error('轻量级解析未能获取到小红书内容');
    } catch (e) {
        throw new Error('小红书解析失败: ' + e.message);
    }
}

/**
 * 统一网关：根据传入内容识别平台并路由到相应的解析函数
 */
async function parseVideoWatermark(text) {
    const url = extractUrl(typeof text === 'string' ? text.slice(0, 4096) : '');
    if (!url) {
        throw new Error('未在文本中检测到有效的链接');
    }

    const host = validateShareUrl(url).hostname;
    if (host === 'douyin.com' || host.endsWith('.douyin.com') || host === 'iesdouyin.com' || host.endsWith('.iesdouyin.com')) {
        return await parseDouyin(url);
    } else if (host === 'kuaishou.com' || host.endsWith('.kuaishou.com') || host === 'gifshow.com' || host.endsWith('.gifshow.com')) {
        const result = await extractKuaishou(url);
        return { platform: 'kuaishou', ...result };
    } else if (host === 'xhslink.com' || host.endsWith('.xhslink.com') || host === 'xiaohongshu.com' || host.endsWith('.xiaohongshu.com')) {
        const result = await extractXiaohongshu(url);
        return { platform: 'xiaohongshu', ...result };
    } else {
        throw new Error('暂不支持该平台的解析: ' + url);
    }
}

module.exports = {
    parseVideoWatermark
};
