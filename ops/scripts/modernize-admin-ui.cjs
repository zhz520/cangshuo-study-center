// Migrate static presentation attributes using the Vue parser; event/data bindings stay intact.
const fs = require('node:fs');
const path = require('node:path');
const { createRequire } = require('node:module');
const root = path.resolve(__dirname, '../..');
const webRequire = createRequire(path.join(root,'apps/web/package.json'));
const { parse: parseSfc } = webRequire('@vue/compiler-sfc');
const { parse } = webRequire('@vue/compiler-dom');
const names = ['UserManagement','QuestionManagement','StructureManagement','SettingsView','AppManagement','ArticleManagement','CommentManagement','CardsManagement','ScheduleManagement','QuestionReports','ExamManagement','AccessControlManagement'];
for(const name of names) {
  const file=path.join(root,'apps/web/src/views/admin',name+'.vue');
  let source=fs.readFileSync(file,'utf8');
  if(source.includes('admin-workspace')) continue;
  const {descriptor}=parseSfc(source);
  const template=descriptor.template;
  const ast=parse(template.content);
  const edits=[];
  const replacedNodes=[];
  let heading=false;
  const replace=(start,end,text)=>edits.push({start:template.loc.start.offset+start,end:template.loc.start.offset+end,text});
  function visit(node,depth=0) {
    if(node.type!==1) return;
    if(!heading && ['h1','h2'].includes(node.tag) && node.children.every(n=>n.type===2)) {
      const title=node.children.map(n=>n.content).join('').replace(/[\p{Extended_Pictographic}\uFE0F]/gu,'').trim();
      replace(node.loc.start.offset,node.loc.end.offset,`<AdminPageHeading title="${title}" />`);
      replacedNodes.push([template.loc.start.offset+node.loc.start.offset,template.loc.start.offset+node.loc.end.offset]);
      heading=true;
    }
    for(const prop of node.props) {
      if(prop.type!==6||prop.name!=='class'||!prop.value) continue;
      let classes=prop.value.content.split(/\s+/).filter(Boolean);
      classes=classes.filter(c=>!/^shadow(?:-|$)|^backdrop-blur|^hover:(?:shadow|scale|-[tT]ranslate)|^active:scale|^font-black$|^tracking-(?:widest|wider)$/.test(c));
      classes=classes.map(c=>c==='glass-card'?'ui-card':c==='rounded-3xl'||c==='rounded-2xl'?'rounded-xl':c==='p-8'?'p-5':c==='px-8'?'px-4':c==='py-5'?'py-3':c==='gap-6'?'gap-4':c==='space-y-8'?'space-y-5':c==='font-extrabold'?'font-semibold':c);
      if(depth===0) classes=['admin-workspace','ui-page',...classes.filter(c=>!['p-6','p-4','sm:p-6','px-3','py-4'].includes(c))];
      if(['input','select','textarea'].includes(node.tag) && !node.props.some(p=>p.name==='type'&&['checkbox','radio','range','file'].includes(p.value?.content))) {
        classes.push(node.tag==='input'?'ui-input':node.tag==='select'?'ui-select':'ui-textarea');
      }
      if(node.tag==='button') classes.push('admin-control');
      if(node.tag==='table') classes.push('admin-data-table');
      if(node.tag==='th'||node.tag==='td') classes=classes.filter(c=>!['py-4','py-6','px-6'].includes(c));
      replace(prop.value.loc.start.offset,prop.value.loc.end.offset,JSON.stringify([...new Set(classes)].join(' ')));
    }
    for(const child of node.children) visit(child,depth+1);
  }
  ast.children.filter(n=>n.type===1).forEach(n=>visit(n));
  edits.filter(e=>!replacedNodes.some(([start,end])=>e.start>start&&e.end<end)).sort((a,b)=>b.start-a.start).forEach(e=>{source=source.slice(0,e.start)+e.text+source.slice(e.end)});
  source=source.replace(/<script setup[^>]*>/,match=>match+"\nimport AdminPageHeading from '../../components/ui/AdminPageHeading.vue';");
  fs.writeFileSync(file,source);
  console.log(name+': '+edits.length+' presentation updates');
}
