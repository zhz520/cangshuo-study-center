const mysql = require('mysql2/promise');
require('dotenv').config();

const dbConfig = {
    host: process.env.DB_HOST || 'localhost',
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || 'root',
    database: process.env.DB_NAME || 'study_center'
};

const seedData = async () => {
    let connection;
    try {
        connection = await mysql.createConnection(dbConfig);
        console.log('Connected to database...');

        // 1. Majors
        console.log('Seeding Majors...');
        await connection.query('DELETE FROM majors');
        await connection.query('ALTER TABLE majors AUTO_INCREMENT = 1');
        const majors = [
            ['计算机科学'],
            ['医学护理'],
            ['财会金融'],
            ['建筑工程']
        ];
        await connection.query('INSERT INTO majors (name) VALUES ?', [majors]);

        // 2. Subjects
        console.log('Seeding Subjects...');
        await connection.query('DELETE FROM subjects');
        await connection.query('ALTER TABLE subjects AUTO_INCREMENT = 1');
        const [majorRows] = await connection.query('SELECT id, name FROM majors');
        const majorMap = {};
        majorRows.forEach(m => {
            if (m.name === '计算机科学') majorMap['computer'] = m.id;
            if (m.name === '医学护理') majorMap['medical'] = m.id;
            if (m.name === '财会金融') majorMap['finance'] = m.id;
            if (m.name === '建筑工程') majorMap['construction'] = m.id;
        });

        const subjects = [
            [majorMap['computer'], 'Java程序设计'],
            [majorMap['computer'], 'Python数据分析'],
            [majorMap['computer'], 'Web前端开发'],
            [majorMap['medical'], '内科护理学'],
            [majorMap['medical'], '外科护理学'],
            [majorMap['finance'], '基础会计'],
            [majorMap['construction'], '建筑力学']
        ];
        await connection.query('INSERT INTO subjects (major_id, name) VALUES ?', [subjects]);

        // 3. Questions (Sample)
        console.log('Seeding Questions...');
        await connection.query('DELETE FROM questions');
        await connection.query('ALTER TABLE questions AUTO_INCREMENT = 1');
        
        const [subjectRows] = await connection.query('SELECT id, name FROM subjects');
        const javaId = subjectRows.find(s => s.name === 'Java程序设计')?.id;
        
        if (javaId) {
            const questions = [];
            for (let i = 1; i <= 5; i++) {
                questions.push([
                    javaId, 
                    'single', // type
                    'Java中，以下哪个关键字用于定义类？', // content
                    JSON.stringify({A: 'class', B: 'interface', C: 'implements', D: 'extends'}), // options
                    'A', // answer
                    'class关键字用于定义类。', // analysis
                    1, // difficulty
                    JSON.stringify(['基础语法']) // tags
                ]);
            }
             await connection.query('INSERT INTO questions (subject_id, type, content, options, answer, analysis, difficulty, tags) VALUES ?', [questions]);
        }

        // 4. Countdowns
        console.log('Seeding Countdowns...');
        await connection.query('DELETE FROM countdowns');
        const countdowns = [
            ['2026年高考', '2026-06-07', 1],
            ['期末考试', '2026-01-10', 2],
            ['寒假', '2026-01-15', 3]
        ];
        await connection.query('INSERT INTO countdowns (title, target_date, sort_order) VALUES ?', [countdowns]);

        // 5. External Links
        console.log('Seeding External Links...');
        await connection.query('DELETE FROM external_links');
        const links = [
            ['教务系统', 'https://jwxt.example.com', 1],
            ['图书馆', 'https://lib.example.com', 2],
            ['慕课网', 'https://imooc.com', 3],
            ['GitHub', 'https://github.com', 4]
        ];
        await connection.query('INSERT INTO external_links (name, url, sort_order) VALUES ?', [links]);

        // 6. Daily Contents
        console.log('Seeding Daily Content...');
        await connection.query('DELETE FROM daily_contents');
        const daily = [
            ['quote', '学而不思则罔，思而不学则殆', '孔子'],
            ['proverb', 'Practice makes perfect.', '熟能生巧'],
            ['poetry', '长风破浪会有时，直挂云帆济沧海', '李白']
        ];
        await connection.query('INSERT INTO daily_contents (type, content, translation) VALUES ?', [daily]);
        
        // 7. Settings
        console.log('Seeding Settings...');
        await connection.query('DELETE FROM settings');
        const settings = [
            ['allow_register', '1'],
            ['notice', '欢迎来到学习中心！这里有丰富的题库资源，祝您学习愉快！']
        ];
        await connection.query('INSERT INTO settings (setting_key, setting_value) VALUES ?', [settings]);

        console.log('Seed completed successfully!');

    } catch (error) {
        console.error('Seed failed:', error);
    } finally {
        if (connection) await connection.end();
    }
};

seedData();
