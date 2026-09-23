import fs from 'node:fs/promises';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
import {Presentation,PresentationFile} from '@oai/artifact-tool';
const root=process.cwd();
const out=path.join(root,'docs/product/presentation');
const build=path.join(root,'.symposium/scratch/presentation-build');
const skill='/Users/jominjun/.codex/plugins/cache/openai-primary-runtime/presentations/26.905.11957/skills/presentations';
const runtime='/Users/jominjun/.cache/codex-runtimes/codex-primary-runtime/dependencies';
const data=JSON.parse(await fs.readFile(path.join(out,'dearby-presentation.json'),'utf8'));
const {finalizePresentation}=await import(pathToFileURL(path.join(skill,'container_tools/artifact_tool_utils.mjs')).href);
const theme=data.theme;
const p=Presentation.create({slideSize:{width:1280,height:720}});
const FONT=theme.font;
function text(slide,value,x,y,w,h,size=30,color=theme.ink,bold=false){
 const sh=slide.shapes.add({geometry:'textbox',position:{left:x,top:y,width:w,height:h},fill:'none',line:{fill:'none',width:0}});
 sh.text=value;
 sh.text.style={typeface:FONT,fontSize:size,bold,color,autoFit:'none'};
 return sh;
}
function pair(slide,arr,x,y,w){text(slide,arr[0],x,y,w,44,29,theme.accent,true);text(slide,arr[1],x,y+55,w,106,29,theme.ink);}
function nativeTable(slide,d){
 const vals=[d.headers,...d.rows];
 const widths=d.headers.length===3?[230,275,635]:[330,810];
 const t=slide.tables.add({rows:vals.length,columns:d.headers.length,left:70,top:190,width:1140,height:vals.length*66,values:vals,columnWidths:widths});
 t.borders.assign({fill:theme.background,width:0,style:'solid'});
 t.cells.block({row:0,column:0,rowCount:vals.length,columnCount:d.headers.length}).assign({textStyle:{typeface:FONT,fontSize:27,color:theme.ink},margins:{left:14,right:14,top:10,bottom:10},anchor:'center'});
 for(let r=0;r<vals.length;r++){
  t.rows[r].height=66;
  for(let c=0;c<d.headers.length;c++){
   const cell=t.getCell(r,c);
   cell.fill=r===0?'#E7EEE9':(r%2===0?'#EFF2F1':theme.background);
   cell.text.style={typeface:FONT,fontSize:27,bold:r===0,color:r===0?theme.accent:theme.ink};
  }
 }
}
for(const d of data.slides){
 const s=p.slides.add();s.background.fill=theme.background;
 if(d.layout==='cover'){
  text(s,'Dearby',68,86,500,104,86,theme.accent,true);
  text(s,d.subtitle,70,238,610,150,44,theme.ink,true);
  text(s,d.caption,73,552,550,48,25,theme.muted);
  s.images.add({blob:new Uint8Array(await fs.readFile(path.join(out,d.image))),contentType:'image/png',alt:'대학생 협업 활동을 표현한 AI 생성 콘셉트 이미지',fit:'cover',position:{left:720,top:78,width:490,height:495}});
  text(s,'협업 활동을 표현한 AI 생성 이미지',725,588,480,35,17,theme.muted);
 }else{
  text(s,d.title,70,64,1140,82,46,theme.ink,true);
  if(d.layout==='statement'||d.layout==='closing'){
   text(s,d.headline,70,182,1120,150,d.layout==='closing'?52:54,theme.accent,true);
   d.items.forEach((a,i)=>pair(s,a,70+i*585,395,545));
  }else if(d.layout==='split'){
   d.columns.forEach((c,i)=>{
    const x=70+i*585;text(s,c.title,x,195,535,64,35,theme.accent,true);
    c.lines.forEach((v,j)=>text(s,v,x,291+j*66,535,54,29,theme.ink));
   });
  }else if(d.layout==='sequence'){
   d.steps.forEach((a,i)=>{
    const y=192+i*130;
    text(s,a[0],70,y,580,66,34,theme.accent,true);
    text(s,a[1],650,y+5,560,77,28,theme.ink);
   });
  }else if(d.layout==='ranking'){
   text(s,d.priority,70,186,1140,105,32,theme.accent,true);
   d.items.forEach((a,i)=>pair(s,a,70+i*585,357,545));
  }else if(d.layout==='table')nativeTable(s,d);
  text(s,d.footnote,70,628,1060,50,21,theme.muted);
 }
 text(s,d.appendix?`부록 ${d.id-12}`:`${d.id} / 12`,1160,676,95,30,16,theme.muted);
 s.speakerNotes.textFrame.setText(`${d.notes}\n\n상태: ${d.status}\n발표 시간: ${d.seconds}초\n근거 문서:\n${d.sources.map(v=>path.resolve(out,v)).join('\n')}${d.id===1?'\n표지: AI 생성 콘셉트 이미지. 실제 행사·이용자 사진 아님.':''}`);
}
let md=`# ${data.title}\n\n학교·프로젝트 발표용 · 본문 12장 + 부록 3장 · 약 ${Math.round(data.planned_seconds/60)}분\n\n기준일: ${data.date}. 제품 방향과 핵심 흐름은 합의했으며, 세부 명세·자동 수집·개인화 추천 구현은 미완료입니다.\n\n이 원고는 직접 편집할 수 있습니다. PPTX/JSON과 자동 동기화되지는 않습니다. 재생성할 때는 수정한 파일을 기준으로 명시하세요.\n\n`;
for(const d of data.slides){
 md+=`---\n\n## ${d.id}. ${d.title}\n\n상태: ${d.status}${d.appendix?' · 질의응답용 부록':` · 권장 ${d.seconds}초`}\n\n`;
 if(d.subtitle)md+=d.subtitle.replaceAll('\n','  \n')+'\n\n';
 if(d.headline)md+=`**${d.headline.replaceAll('\n',' ')}**\n\n`;
 if(d.priority)md+=d.priority+'\n\n';
 if(d.headers){md+='| '+d.headers.join(' | ')+' |\n| '+d.headers.map(()=> '---').join(' | ')+' |\n';for(const r of d.rows)md+='| '+r.map(v=>v.replaceAll('|','\\|')).join(' | ')+' |\n';md+='\n';}
 for(const a of d.steps??d.items??[])md+=`- **${a[0]}:** ${a[1].replaceAll('\n',' / ')}\n`;
 for(const c of d.columns??[]){md+=`### ${c.title}\n\n`;for(const l of c.lines)md+='- '+l+'\n';md+='\n';}
 if(d.footnote)md+=`\n참고: ${d.footnote}\n`;
 if(d.image)md+=`\n![협업 활동 콘셉트 이미지](${d.image})\n\n${data.image_note}\n`;
 md+=`\n### 발표자 원고\n\n${d.notes}\n\n근거: ${d.sources.map(v=>`[${path.basename(v)}](${v})`).join(', ')}\n\n`;
}
await fs.writeFile(path.join(out,'dearby-presentation.md'),md);
await fs.mkdir(path.join(build,'renders'),{recursive:true});
const candidate=path.join(build,'candidate.pptx');
await (await PresentationFile.exportPptx(p)).save(candidate);
console.log('Exported candidate');
const finalPath=path.join(out,'dearby-school-presentation-final.pptx');
const result=await finalizePresentation({workspaceDir:root,candidatePath:candidate,finalPath,pythonExecutable:path.join(runtime,'python/bin/python3'),integrityValidatorPath:path.join(skill,'container_tools/inspect_presentation_package_integrity.py'),layoutValidatorPath:path.join(skill,'container_tools/inspect_presentation_layout_geometry.py'),layoutArgs:['--expected-slide-size-emu','12192000,6858000','--validate-heading-fit','--validate-bullet-geometry','--require-native-table-slide','5','--require-native-table-slide','13','--require-native-table-slide','14'],explicitTotalSlideCount:15,requiredNativeTableOwnerSlides:[5,13,14],fontPolicy:{basis:'design',families:[FONT]},verifyArtifactToolImport:true,receiptPath:path.join(build,'validation-final.json')});
console.log(JSON.stringify(result,null,2));
