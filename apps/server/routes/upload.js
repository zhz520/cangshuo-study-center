const express = require('express');
const multer = require('multer');
const path = require('node:path');
const fs = require('node:fs/promises');
const { randomUUID } = require('node:crypto');
const { verifyToken } = require('../middleware/authMiddleware');
const { transientRateLimit } = require('../middleware/transientRateLimit');

function imageType(bytes) {
    if (bytes.length < 12) return null;
    if (bytes.subarray(0, 8).equals(Buffer.from([137,80,78,71,13,10,26,10])) && bytes.toString('ascii',12,16) === 'IHDR') return { ext:'png', mime:'image/png' };
    if (bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255) return { ext:'jpg', mime:'image/jpeg' };
    if (['GIF87a','GIF89a'].includes(bytes.toString('ascii',0,6))) return { ext:'gif', mime:'image/gif' };
    if (bytes.toString('ascii',0,4) === 'RIFF' && bytes.toString('ascii',8,12) === 'WEBP') return { ext:'webp', mime:'image/webp' };
    return null;
}
function createUploadRouter({ authorize = verifyToken, directory = path.join(__dirname,'../public/uploads') } = {}) {
    const router = express.Router();
    const upload = multer({ storage:multer.memoryStorage(), limits:{ fileSize:5*1024*1024, files:1, fields:0, parts:2 } });
    router.post('/', authorize, transientRateLimit('upload',10), (req,res) => {
        upload.single('image')(req,res,async error => {
            if (error) return res.status(error.code === 'LIMIT_FILE_SIZE' ? 413 : 400).json({ error:'请上传一张不超过 5MB 的图片' });
            const type = req.file && imageType(req.file.buffer);
            if (!type || type.mime !== req.file.mimetype) return res.status(400).json({ error:'仅支持有效的 PNG、JPEG、GIF 或 WebP 图片' });
            const filename = `${randomUUID()}.${type.ext}`;
            try {
                await fs.mkdir(directory,{ recursive:true });
                await fs.writeFile(path.join(directory,filename),req.file.buffer,{ flag:'wx',mode:0o644 });
                res.json({ url:`/uploads/${filename}`,filename,size:req.file.size });
            } catch { res.status(500).json({ error:'上传失败，请稍后重试' }); }
        });
    });
    return router;
}
module.exports = createUploadRouter();
module.exports.createUploadRouter = createUploadRouter;
module.exports.imageType = imageType;
