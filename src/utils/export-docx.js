
import { Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType, TabStopType, TabStopPosition, BorderStyle } from "docx";
import { saveAs } from "file-saver";

/**
 * 导出题目到 Word 文档
 * @param {Array} questions - 题目列表
 * @param {String} title - 文档标题
 * @param {Boolean} includeAnswer - 是否包含答案和解析
 */
export const exportToWord = async (questions, title = "试题导出", includeAnswer = false) => {
    const children = [];

    // Title
    children.push(
        new Paragraph({
            text: title,
            heading: HeadingLevel.HEADING_1,
            alignment: AlignmentType.CENTER,
            spacing: { after: 400 },
        })
    );

    // Questions
    questions.forEach((q, index) => {
        // 1. Question Number & Type
        const typeMap = { single: '单选题', multiple: '多选题', judge: '判断题', fill: '填空题', sort: '排序题', material: '材料题' };
        children.push(
            new Paragraph({
                children: [
                    new TextRun({
                        text: `${index + 1}. [${typeMap[q.content.type] || '未知'}] `,
                        bold: true,
                        size: 24, // 12pt
                    }),
                    new TextRun({
                        text: q.content.question,
                        size: 24,
                    }),
                ],
                spacing: { before: 200, after: 100 },
            })
        );

        // 2. Options (if applicable)
        if (q.content.type === 'single' || q.content.type === 'multiple') {
            const options = q.content.options || {};
            Object.entries(options).forEach(([key, val]) => {
                children.push(
                    new Paragraph({
                        text: `${key}. ${val}`,
                        indent: { left: 400 }, // Indent options
                        spacing: { after: 50 },
                    })
                );
            });
        } else if (q.content.type === 'judge') {
            children.push(
                new Paragraph({
                    text: "A. 正确    B. 错误",
                    indent: { left: 400 },
                    spacing: { after: 50 },
                })
            );
        } else if (q.content.type === 'sort') {
             // For sort, show items to be sorted
             if (Array.isArray(q.content.options)) {
                 q.content.options.forEach((opt, idx) => {
                     children.push(new Paragraph({
                        text: `(${idx+1}) ${opt.content || opt}`,
                        indent: { left: 400 },
                        spacing: { after: 50 }
                     }));
                 });
             }
        } else if (q.content.type === 'material') {
             // Material Sub-questions
             if (Array.isArray(q.content.options)) {
                 q.content.options.forEach((subQ, subIdx) => {
                     children.push(
                         new Paragraph({
                            children: [
                                new TextRun({
                                    text: `   (${subIdx + 1}) [${typeMap[subQ.type]}] ${subQ.stem}`,
                                    bold: true
                                })
                            ],
                            indent: { left: 400 },
                            spacing: { before: 100, after: 50 }
                         })
                     );
                     
                     // Sub options
                     if (subQ.type === 'single' || subQ.type === 'multiple') {
                         Object.entries(subQ.options || {}).forEach(([k, v]) => {
                             children.push(new Paragraph({
                                 text: `${k}. ${v}`,
                                 indent: { left: 800 },
                                 spacing: { after: 50 }
                             }));
                         });
                     } else if (subQ.type === 'judge') {
                        children.push(new Paragraph({
                             text: "A. 正确    B. 错误",
                             indent: { left: 800 },
                             spacing: { after: 50 }
                        }));
                     }
                 });
             }
        }

        // 3. Answer & Analysis (if enabled)
        if (includeAnswer) {
            let answerText = "";
            if (q.content.type === 'material') {
                 answerText = "见子题目解析";
            } else if (Array.isArray(q.content.answer)) {
                answerText = q.content.answer.join("");
            } else {
                answerText = String(q.content.answer);
            }
            
            // Format Judge Answer
            if (q.content.type === 'judge') {
                if (answerText === 'A' || answerText === 'TRUE' || answerText === 'true' || answerText === '正确') answerText = '正确';
                else answerText = '错误';
            }

            children.push(
                new Paragraph({
                    children: [
                        new TextRun({ text: "【正确答案】", bold: true, color: "2E7D32" }),
                        new TextRun({ text: ` ${answerText}` }),
                    ],
                    spacing: { before: 100 },
                    border: { top: { style: BorderStyle.DOTTED, space: 1, color: "E0E0E0" } },
                    indent: { left: 200 }
                })
            );

            children.push(
                new Paragraph({
                    children: [
                        new TextRun({ text: "【解析】", bold: true, color: "1565C0" }),
                        new TextRun({ text: ` ${q.content.analysis || "暂无解析"}` }),
                    ],
                    spacing: { after: 200 },
                    indent: { left: 200 }
                })
            );
            
            // Material answers detail
            if (q.content.type === 'material' && Array.isArray(q.content.options)) {
                 q.content.options.forEach((subQ, subIdx) => {
                     let subAns = subQ.answer;
                     if (Array.isArray(subAns)) subAns = subAns.join("");
                     if (subQ.type === 'judge') subAns = (subAns === 'TRUE' || subAns === '正确') ? '正确' : '错误';
                     
                     children.push(new Paragraph({
                         text: `   (${subIdx+1}) 答案: ${subAns}. 解析: ${subQ.analysis || '无'}`,
                         indent: { left: 400 },
                         size: 20,
                         color: "666666"
                     }));
                 });
            }
        } else {
            // Spacer if no answer
            children.push(new Paragraph({ text: "", spacing: { after: 200 } }));
        }
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
    saveAs(blob, `${title}.docx`);
};
