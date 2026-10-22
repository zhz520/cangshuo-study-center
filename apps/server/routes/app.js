const express = require("express");
const router = express.Router();
const db = require("../config/db");

// Check for updates
router.get("/check_update", async (req, res) => {
  try {
    const { versionCode } = req.query;
    if (!versionCode) {
      return res.status(400).json({ error: "versionCode is required" });
    }

    const vCode = parseInt(versionCode, 10);

    // 1. Check for APK full update
    const [versions] = await db.execute(
      "SELECT * FROM app_versions WHERE status = 1 AND version_code > ? ORDER BY version_code DESC LIMIT 1",
      [vCode],
    );

    if (versions.length > 0) {
      return res.json({
        hasUpdate: true,
        updateType: "apk",
        data: versions[0],
      });
    }

    // 2. Check for Tinker patch
    const [patches] = await db.execute(
      "SELECT * FROM app_patches WHERE status = 1 AND target_version_code = ? ORDER BY patch_version DESC LIMIT 1",
      [vCode],
    );

    if (patches.length > 0) {
      return res.json({
        hasUpdate: true,
        updateType: "patch",
        data: patches[0],
      });
    }

    // 3. No update
    return res.json({
      hasUpdate: false,
      message: "Already up to date",
    });
  } catch (error) {
    console.error("Check update error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

// Get all app versions for history
router.get("/versions", async (req, res) => {
  try {
    const [versions] = await db.execute(
      "SELECT version_code, version_name, update_log, create_time as created_at FROM app_versions WHERE status = 1 ORDER BY version_code DESC",
    );
    return res.json({ data: versions });
  } catch (error) {
    console.error("Get versions error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

module.exports = router;
