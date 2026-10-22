
import { Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType, BorderStyle, Table, TableRow, TableCell, WidthType } from "docx";
import { saveAs } from "file-saver";

/**
 * 导出考试记录详情到 Word 文档
 * @param {Object} data - 包含 record 和 questions 的数据对象
 */
export const exportRecordToWord = async (data) => {
    const { record, questions } = data;
    const children = [];

    // 1. Title
    children.push(
        new Paragraph({
            text: record.paper_title,
            heading: HeadingLevel.HEADING_1,
            alignment: AlignmentType.CENTER,
            spacing: { after: 400 },
        })
    );

    // 2. User Info Table
    children.push(
        new Table({
            width: {
                size: 100,
                type: WidthType.PERCENTAGE,
            },
            rows: [
                new TableRow({
                    children: [
                        new TableCell({
                            children: [new Paragraph({ children: [new TextRun({ text: "考生名称", bold: true }), new TextRun({ text: `: ${record.nickname || record.username}` })] })],
                        }),
                        new TableCell({
                            children: [new Paragraph({ children: [new TextRun({ text: "考试得分", bold: true }), new TextRun({ text: `: ${record.score} / ${record.total_score}`, color: record.passed ? "2E7D32" : "C62828" })] })],
                        }),
                    ],
                }),
                new TableRow({
                    children: [
                        new TableCell({
                            children: [new Paragraph({ children: [new TextRun({ text: "及格状态", bold: true }), new TextRun({ text: `: ${record.passed ? '及格' : '不及格'}`, color: record.passed ? "2E7D32" : "C62828" })] })],
                        }),
                        new TableCell({
                            children: [new Paragraph({ children: [new TextRun({ text: "考试时间", bold: true }), new TextRun({ text: `: ${new Date(record.start_time).toLocaleString()}` })] })],
                        }),
                    ],
                }),
            ],
            spacing: { after: 400 }
        })
    );

    // 3. Questions
    questions.forEach((q, index) => {
        const typeMap = { single: '单选题', multiple: '多选题', judge: '判断题', fill: '填空题', sort: '排序题', material: '材料题' };
        
        // Question Stem
        children.push(
            new Paragraph({
                children: [
                    new TextRun({
                        text: `${index + 1}. [${typeMap[q.type] || '未知'}] `,
                        bold: true,
                        size: 24,
                    }),
                    new TextRun({
                        text: q.content,
                        size: 24,
                    }),
                    new TextRun({
                        text: ` (${q.score}分)`,
                        size: 20,
                        color: "666666"
                    })
                ],
                spacing: { before: 200, after: 100 },
            })
        );

        // Options
        if (q.type === 'single' || q.type === 'multiple' || q.type === 'judge') {
            const options = q.options || {};
            if (Array.isArray(options)) {
                options.forEach(opt => {
                    children.push(
                        new Paragraph({
                            text: `${opt.label || ''}. ${opt.content || opt}`,
                            indent: { left: 400 },
                            spacing: { after: 50 },
                        })
                    );
                });
            } else {
                Object.entries(options).forEach(([key, val]) => {
                    children.push(
                        new Paragraph({
                            text: `${key}. ${val}`,
                            indent: { left: 400 },
                            spacing: { after: 50 },
                        })
                    );
                });
            }
        }

        // User Answer vs Correct Answer
        const isCorrect = q.is_correct;
        children.push(
            new Paragraph({
                children: [
                    new TextRun({ text: "【你的回答】", bold: true }),
                    new TextRun({ 
                        text: ` ${q.user_answer || "未作答"}`, 
                        color: isCorrect ? "2E7D32" : "C62828",
                        bold: true
                    }),
                    new TextRun({ text: "    " }),
                    new TextRun({ text: "【正确答案】", bold: true, color: "2E7D32" }),
                    new TextRun({ text: ` ${q.answer}` }),
                ],
                spacing: { before: 100 },
                indent: { left: 200 }
            })
        );

        // Analysis
        children.push(
            new Paragraph({
                children: [
                    new TextRun({ text: "【解析】", bold: true, color: "1565C0" }),
                    new TextRun({ text: ` ${q.analysis || "暂无解析"}` }),
                ],
                spacing: { after: 200 },
                indent: { left: 200 },
                border: { bottom: { style: BorderStyle.DOTTED, space: 1, color: "E0E0E0" } }
            })
        );
    });

    const doc = new Document({
        sections: [
            {
                properties: {},
                children: children,
            },
        ],
    });

    const blob = await Packer.toBlob(doc);
    const fileName = `${record.paper_title}_${record.nickname || record.username}_${new Date().toLocaleDateString()}.docx`;
    saveAs(blob, fileName);
};
