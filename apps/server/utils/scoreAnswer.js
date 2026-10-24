function unpack(value) {
    if (typeof value !== 'string') return value;
    for (let depth = 0; depth < 5 && typeof value === 'string'; depth++) {
        try { const next = JSON.parse(value); if (next === value) break; value = next; }
        catch { return value.trim(); }
    }
    return value;
}

function canonical(value, type) {
    value = unpack(value);
    if (type === 'judge') {
        const text = String(Array.isArray(value) ? value[0] : value).trim().toUpperCase();
        if (['A', 'TRUE', 'T', '正确', '对', '√', '1'].includes(text)) return 'A';
        if (['B', 'FALSE', 'F', '错误', '错', '×', '0'].includes(text)) return 'B';
        return text;
    }
    if (type === 'single' || type === 'multiple') {
        const text = (Array.isArray(value) ? value.join(',') : String(value ?? '')).toUpperCase();
        const letters = text.match(/[A-Z]/g) || [];
        return type === 'multiple' ? [...new Set(letters)].sort().join(',') : letters.join('');
    }
    if (type === 'sort') return JSON.stringify(Array.isArray(value) ? value.map(String) : value);
    if (value && typeof value === 'object') {
        if (Array.isArray(value)) return JSON.stringify(value);
        return JSON.stringify(Object.fromEntries(Object.keys(value).sort().map(k => [k, value[k]])));
    }
    return String(value ?? '').normalize('NFKC').trim();
}

function scoreAnswer(question, answer) {
    if (answer == null || answer === '' || (Array.isArray(answer) && !answer.length)) return false;
    if (question.type === 'material') {
        const items = unpack(question.options);
        const given = unpack(answer);
        return Array.isArray(items) && items.length > 0 && given && typeof given === 'object' &&
            items.every((item, index) => scoreAnswer(item, given[index]));
    }
    const expected = canonical(question.answer, question.type);
    return expected !== '' && expected === canonical(answer, question.type);
}

module.exports = { canonical, scoreAnswer };
