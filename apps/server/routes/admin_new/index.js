const express = require('express');
const router = express.Router();
const { verifyToken, isAdmin } = require('../../middleware/authMiddleware');

// Import modular routes
const statsRoutes = require('./stats');
const usersRoutes = require('./users');
const structureRoutes = require('./structure'); // Majors and Subjects
const questionRoutes = require('./questions');
const settingsRoutes = require('./settings'); // Settings, Countdowns, Links
const contentRoutes = require('./content');
const articlesRoutes = require('./articles');
const commentsRoutes = require('./comments');

// Apply middleware globally to all admin routes
router.use(verifyToken, isAdmin);

// Mount modular routes
router.use('/stats', statsRoutes);
router.use('/users', usersRoutes);
router.use('/questions', questionRoutes);
router.use('/daily-contents', contentRoutes);
router.use('/import', require('./import'));

// For structure and settings, we mount them at root because they have their own internal paths 
// (e.g. /majors, /subjects in structure.js; /settings, /countdowns in settings.js)
// or we could mount them at precise paths if they were single-resource routers.
// But based on my split:
// structure.js has /majors and /subjects. So mounting at / would preserve /admin/majors and /admin/subjects.
router.use('/', structureRoutes);
router.use('/', settingsRoutes);
router.use('/articles', articlesRoutes);
router.use('/comments', commentsRoutes);
router.use('/app-update', require('./app_update'));
router.use('/cards', require('./cards'));
router.use('/schedule', require('./schedule'));
router.use('/reports', require('./reports'));
router.use('/exams', require('./exams'));
router.use('/access-control', require('./access_control'));
router.use('/exports', require('./export_credentials'));

module.exports = router;
