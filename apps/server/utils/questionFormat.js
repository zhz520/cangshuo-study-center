/** Storage contract v1. Convert containers, never infer missing educational content. */
const FORMAT_VERSION = 1;
const TYPES = new Set(['single','multiple','judge','fill','text','sort','material']);
function unpackJson(value) {
    for (let depth=0; depth<5 && typeof value==='string'; depth++) {
        try { const next=JSON.parse(value); if(next===value)break; value=next; }
        catch { break; }
    }
    return value;
}
function truth(value) {
    const v=String(value).normalize('NFKC').trim().toUpperCase();
    if(['正确','对','√','✓','TRUE','T','1'].includes(v))return 'A';
    if(['错误','错','×','✗','FALSE','F','0'].includes(v))return 'B';
    return null;
}
function normalizeQuestionRecord(row) {
    const issues=[];
    const type=String(row.type||'').trim();
    if(!TYPES.has(type))issues.push('UNKNOWN_TYPE');
    const content=typeof row.content==='string'?row.content:String(row.content??'');
    const analysis=typeof row.analysis==='string'?row.analysis:String(row.analysis??'');
    if(!content.trim())issues.push('EMPTY_CONTENT');
    const raw=unpackJson(row.options);
    const options={};
    if (type === 'material') {
        // Material questions retain an ordered list of subquestions, never a letter map.
        if (!Array.isArray(raw)) issues.push('INVALID_MATERIAL_OPTIONS');
        const nested = Array.isArray(raw) ? raw.map(item => {
            if (!item || typeof item !== 'object') { issues.push('INVALID_MATERIAL_QUESTION'); return item; }
            const result = normalizeQuestionRecord({ ...item, content: item.content ?? item.question ?? item.stem ?? '' });
            if (!result.safe) issues.push('INVALID_MATERIAL_QUESTION');
            return { ...item, ...result.record };
        }) : raw;
        return { record: {type,content,options:nested,answer:typeof row.answer==='string'?row.answer:JSON.stringify(row.answer??''),analysis},
            issues:[...new Set(issues)],safe:!issues.some(code=>code.startsWith('INVALID_')),formatVersion:FORMAT_VERSION };
    }
    function add(key,value) {
        key=String(key).normalize('NFKC').trim().toUpperCase();
        if(!/^[A-Z]$/.test(key))issues.push('INVALID_OPTION_LABEL');
        if(Object.hasOwn(options,key))issues.push('DUPLICATE_OPTION_LABEL');
        if(value!==null && typeof value==='object')issues.push('NON_TEXT_OPTION');
        options[key]=value==null?'':String(value);
    }
    if(Array.isArray(raw))raw.forEach((item,index)=>{
        if(item && typeof item==='object' && !Array.isArray(item)) {
            const extra=Object.keys(item).filter(k=>!['label','content','text'].includes(k));
            if(extra.length)issues.push('UNKNOWN_OPTION_FIELDS');
            if(item.content!==undefined && item.text!==undefined && item.content!==item.text)issues.push('CONFLICTING_OPTION_TEXT');
            add(item.label??String.fromCharCode(65+index),item.content??item.text??'');
        } else add(String.fromCharCode(65+index),item);
    });
    else if(raw && typeof raw==='object')for(const [label,value]of Object.entries(raw))add(label,value);
    else if(raw!=null && raw!=='')issues.push('INVALID_OPTIONS_CONTAINER');

    let answer=unpackJson(row.answer);
    if(type==='judge') {
        const value=Array.isArray(answer)&&answer.length===1?answer[0]:answer;
        const text=String(value??'').normalize('NFKC').trim().toUpperCase();
        const optionTruth=Object.hasOwn(options,text)?truth(options[text]):null;
        const normalized=truth(value)||optionTruth||(['A','B'].includes(text)?text:null);
        if(!normalized)issues.push('INVALID_JUDGE_ANSWER');
        const explicit=Object.entries(options);
        if(explicit.some(([key,value])=>!['A','B'].includes(key)||!truth(value)))issues.push('NONSTANDARD_JUDGE_OPTIONS');
        answer=normalized??text;
        // An implicit judge pair is safe; unusual explicit pairs are left for review.
        if(!issues.includes('NONSTANDARD_JUDGE_OPTIONS')) {
            for(const key of Object.keys(options))delete options[key];
            options.A='正确';options.B='错误';
        }
    } else if(type==='single'||type==='multiple') {
        const values=Array.isArray(answer)?answer:[answer];
        const text=values.map(v=>typeof v==='string'?v:JSON.stringify(v)).join(',').normalize('NFKC').trim().toUpperCase();
        if(!/^[A-Z](?:[\s,，、;；|]*[A-Z])*$/.test(text)) {
            issues.push('INVALID_CHOICE_ANSWER');answer=String(row.answer??'').trim();
        } else {
            const labels=[...new Set(text.match(/[A-Z]/g))].sort();
            if(type==='single'&&labels.length!==1)issues.push('SINGLE_HAS_MULTIPLE_ANSWERS');
            if(labels.some(label=>!Object.hasOwn(options,label)))issues.push('ANSWER_OPTION_MISSING');
            answer=labels.join(',');
        }
        if(Object.keys(options).length<2)issues.push('TOO_FEW_OPTIONS');
        if(Object.values(options).some(value=>!value.trim()))issues.push('EMPTY_OPTION');
    } else {
        answer=typeof answer==='string'?answer:JSON.stringify(answer??'');
    }
    if(!String(answer).trim())issues.push('EMPTY_ANSWER');
    const normalized={type,content,options:type==='sort'?Object.entries(options).map(([label,content])=>({label,content})):options,answer:String(answer),analysis};
    // Unrecognized structures must never be flattened or discarded by a migration.
    const blockers=['UNKNOWN_TYPE','INVALID_OPTION_LABEL','DUPLICATE_OPTION_LABEL','NON_TEXT_OPTION','UNKNOWN_OPTION_FIELDS','CONFLICTING_OPTION_TEXT','INVALID_OPTIONS_CONTAINER','NONSTANDARD_JUDGE_OPTIONS'];
    return {record:normalized,issues:[...new Set(issues)],safe:!issues.some(code=>blockers.includes(code)),formatVersion:FORMAT_VERSION};
}
module.exports={FORMAT_VERSION,TYPES,unpackJson,normalizeQuestionRecord};
