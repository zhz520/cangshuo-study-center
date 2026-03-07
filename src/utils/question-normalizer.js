/**
 * Normalizes question data from various legacy formats into a standard structure.
 * 
 * Standard Output Structure:
 * {
 *   type: 'single' | 'multi' | 'text' | 'boolean',
 *   options: { A: "Option A", B: "Option B", ... } | null,
 *   answer: Array<string> | string, // standardized to Array for selection types, string for text
 *   question: string,
 *   analysis: string
 * }
 */
export function normalizeQuestion(q) {
    // 1. Parse content if string
    let content = q.content;
    if (typeof content === "string") {
        try {
            content = JSON.parse(content);
        } catch (e) {
            content = {};
        }
    }

    const type = detectType(content);
    const options = normalizeOptions(content.options, type);
    const answer = normalizeAnswer(content, type, options);

    return {
        ...q,
        content: {
            ...content,
            type,
            options,
            answer
        }
    };
}

function detectType(content) {
    if (content.type === "判断题" || content.type === "true_false") return "boolean";
    if (content.type === "多选题" || content.type === "multi_choice") return "multi";
    if (content.type === "单选题" || content.type === "single_choice") return "single";
    if (content.type === "pinyin_write_hanzi" || content.type === "fill") return "text";
    
    // Heuristic detection
    if (content.answer && Array.isArray(content.answer) && content.answer.length > 1) return "multi";
    if (content.answer_text && content.answer_text.length > 1) return "multi";
    if (content.answer === "√" || content.answer === "×") return "boolean";
    
    // Check options count
    if (content.options && typeof content.options === 'object') {
       const keys = Object.keys(content.options);
       if (keys.length === 2 && (keys.includes("正确") || keys.includes("错误") || content.options['A'] === '正确')) return "boolean";
    }

    return "single"; // Default
}

function normalizeOptions(rawOptions, type) {
    if (!rawOptions && type === 'boolean') {
        return { "A": "√ (正确)", "B": "× (错误)" };
    }

    if (!rawOptions) return null;

    if (Array.isArray(rawOptions)) {
        const opts = {};
        rawOptions.forEach((o, index) => {
            if (typeof o === 'string') {
                // Match "A、Option" or "A. Option"
                const match = o.match(/^([A-Z])\s*[、.]\s*(.*)/i);
                if (match) {
                    opts[match[1].toUpperCase()] = match[2];
                } else if (type === 'boolean') {
                    // Start of array might be "正确", "错误"
                    if (o.includes("正确") || o === "√") opts["A"] = o;
                    else if (o.includes("错误") || o === "×") opts["B"] = o;
                    else {
                        // If no prefix, assign A, B, C based on index
                        const key = String.fromCharCode(65 + index);
                        opts[key] = o;
                    }
                } else {
                     // If no prefix, assign A, B, C based on index
                     const key = String.fromCharCode(65 + index);
                     opts[key] = o;
                }
            } else if (typeof o === 'object' && o.label && o.text) {
                opts[o.label] = o.text;
            }
        });
        return Object.keys(opts).length > 0 ? opts : null;
    }

    if (typeof rawOptions === 'object') {
        if (Object.keys(rawOptions).length === 0) return null;
        return rawOptions;
    }

    return null;
}

function normalizeAnswer(content, type, options) {
    let ans = content.answer;
    
    // Handle answer_text override
    if (content.answer_text) ans = content.answer_text;

    // 1. Boolean Mapping
    if (type === 'boolean') {
        if (ans === '√' || ans === 'T' || ans === 'true' || ans === true) return ['A'];
        if (ans === '×' || ans === 'F' || ans === 'false' || ans === false) return ['B'];
        // Sometimes boolean answer is "A" or "B" already
        if (ans === 'A' || ans === 'B') return [ans];
    }

    // 2. Multi/Single Standard -> Array
    if (type === 'multi' || type === 'single') {
        if (Array.isArray(ans)) return ans.map(a => a.toUpperCase());
        if (typeof ans === 'string') {
            // Split "ABCD" -> ["A", "B", "C", "D"] if multi
            if (type === 'multi' && /^[A-Z]+$/i.test(ans)) {
                return ans.toUpperCase().split("");
            }
            return [ans.toUpperCase()];
        }
    }

    // 3. Text
    if (type === 'text') {
        return ans; // Keep as is string
    }

    return [];
}
