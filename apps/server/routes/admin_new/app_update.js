const express = require('express');
const router = express.Router();
const db = require('../../config/db');

// --- App Versions ---

// Get all versions
router.get('/versions', async (req, res) => {
    try {
        const [rows] = await db.execute('SELECT * FROM app_versions ORDER BY version_code DESC');
        res.json(rows);
    } catch (error) {
        console.error('Error fetching app versions:', error);
        res.status(500).json({ message: '获取版本列表失败', error: error.message });
    }
});

// Create version
router.post('/versions', async (req, res) => {
    const { version_name, version_code, download_url, update_log, is_force, status } = req.body;
    try {
        const query = `
            INSERT INTO app_versions (version_name, version_code, download_url, update_log, is_force, status) 
            VALUES (?, ?, ?, ?, ?, ?)
        `;
        const [result] = await db.execute(query, [
            version_name, 
            version_code, 
            download_url, 
            update_log || '', 
            is_force ? 1 : 0, 
            status !== undefined ? status : 1
        ]);
        
        const [newRecord] = await db.execute('SELECT * FROM app_versions WHERE id = ?', [result.insertId]);
        res.status(201).json(newRecord[0]);
    } catch (error) {
        console.error('Error creating app version:', error);
        res.status(500).json({ message: '创建版本失败', error: error.message });
    }
});

// Update version
router.put('/versions/:id', async (req, res) => {
    const { id } = req.params;
    const { version_name, version_code, download_url, update_log, is_force, status } = req.body;
    try {
        const query = `
            UPDATE app_versions 
            SET version_name = ?, version_code = ?, download_url = ?, update_log = ?, is_force = ?, status = ?
            WHERE id = ?
        `;
        await db.execute(query, [
            version_name, 
            version_code, 
            download_url, 
            update_log || '', 
            is_force ? 1 : 0, 
            status !== undefined ? status : 1, 
            id
        ]);
        res.json({ message: '更新成功' });
    } catch (error) {
        console.error('Error updating app version:', error);
        res.status(500).json({ message: '更新版本失败', error: error.message });
    }
});

// Delete version
router.delete('/versions/:id', async (req, res) => {
    const { id } = req.params;
    try {
        await db.execute('DELETE FROM app_versions WHERE id = ?', [id]);
        res.json({ message: '删除成功' });
    } catch (error) {
        console.error('Error deleting app version:', error);
        res.status(500).json({ message: '删除版本失败', error: error.message });
    }
});


// --- App Patches (Tinker) ---

// Get all patches
router.get('/patches', async (req, res) => {
    try {
        const [rows] = await db.execute('SELECT * FROM app_patches ORDER BY target_version_code DESC, patch_version DESC');
        res.json(rows);
    } catch (error) {
        console.error('Error fetching app patches:', error);
        res.status(500).json({ message: '获取补丁列表失败', error: error.message });
    }
});

// Create patch
router.post('/patches', async (req, res) => {
    const { target_version_code, patch_version, download_url, md5, status } = req.body;
    try {
        const query = `
            INSERT INTO app_patches (target_version_code, patch_version, download_url, md5, status) 
            VALUES (?, ?, ?, ?, ?)
        `;
        const [result] = await db.execute(query, [
            target_version_code, 
            patch_version, 
            download_url, 
            md5 || '', 
            status !== undefined ? status : 1
        ]);
        
        const [newRecord] = await db.execute('SELECT * FROM app_patches WHERE id = ?', [result.insertId]);
        res.status(201).json(newRecord[0]);
    } catch (error) {
        console.error('Error creating app patch:', error);
        res.status(500).json({ message: '创建补丁失败', error: error.message });
    }
});

// Update patch
router.put('/patches/:id', async (req, res) => {
    const { id } = req.params;
    const { target_version_code, patch_version, download_url, md5, status } = req.body;
    try {
        const query = `
            UPDATE app_patches 
            SET target_version_code = ?, patch_version = ?, download_url = ?, md5 = ?, status = ?
            WHERE id = ?
        `;
        await db.execute(query, [
            target_version_code, 
            patch_version, 
            download_url, 
            md5 || '', 
            status !== undefined ? status : 1, 
            id
        ]);
        res.json({ message: '更新成功' });
    } catch (error) {
        console.error('Error updating app patch:', error);
        res.status(500).json({ message: '更新补丁失败', error: error.message });
    }
});

// Delete patch
router.delete('/patches/:id', async (req, res) => {
    const { id } = req.params;
    try {
        await db.execute('DELETE FROM app_patches WHERE id = ?', [id]);
        res.json({ message: '删除成功' });
    } catch (error) {
        console.error('Error deleting app patch:', error);
        res.status(500).json({ message: '删除补丁失败', error: error.message });
    }
});

module.exports = router;
