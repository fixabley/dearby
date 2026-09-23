"""JSON 원고에서 외부 의존성이 없는 HTML 발표자료를 생성합니다."""
import base64
import html
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATA = json.loads((ROOT / 'dearby-presentation.json').read_text())
def esc(value): return html.escape(str(value), quote=True)
def lines(value): return esc(value).replace('\n', '<br>')
sections=[]
for s in DATA['slides']:
    body=''
    if s['layout']=='cover':
        uri='data:image/png;base64,'+base64.b64encode((ROOT/s['image']).read_bytes()).decode()
        body=f'<div class="cover-copy"><h1>{esc(s["title"])}</h1><p class="subtitle">{lines(s["subtitle"])}</p><p class="caption">{esc(s["caption"])}</p></div><figure><img src="{uri}" alt="대학생 협업 활동을 표현한 AI 생성 콘셉트 이미지" width="1536" height="1024"><figcaption>협업 활동을 표현한 AI 생성 이미지</figcaption></figure>'
    else:
        body=f'<h2>{esc(s["title"])}</h2><div class="content">'
        if 'headline' in s: body+=f'<p class="headline">{lines(s["headline"])}</p>'
        if 'priority' in s: body+=f'<p class="priority">{esc(s["priority"])}</p>'
        if 'headers' in s:
            body+='<div class="table-scroll"><table><thead><tr>'+''.join(f'<th scope="col">{esc(h)}</th>' for h in s['headers'])+'</tr></thead><tbody>'
            for row in s['rows']: body+='<tr>'+''.join(f'<{ "th scope=\"row\"" if i==0 else "td"}>{esc(v)}</{"th" if i==0 else "td"}>' for i,v in enumerate(row))+'</tr>'
            body+='</tbody></table></div>'
        if 'columns' in s:
            body+='<div class="columns">'
            for col in s['columns']: body+=f'<div><h3>{esc(col["title"])}</h3><ul>'+''.join(f'<li>{esc(v)}</li>' for v in col['lines'])+'</ul></div>'
            body+='</div>'
        if 'steps' in s:
            body+='<ol class="sequence-list">'+''.join(f'<li><h3>{esc(a)}</h3><p>{lines(b)}</p></li>' for a,b in s['steps'])+'</ol>'
        if 'items' in s:
            body+='<div class="pairs">'+''.join(f'<div><h3>{esc(a)}</h3><p>{lines(b)}</p></div>' for a,b in s['items'])+'</div>'
        body+='</div>'
        if 'footnote' in s: body+=f'<p class="footnote">{esc(s["footnote"])}</p>'
    label=f'부록 {s["id"]-12}' if s['appendix'] else f'{s["id"]} / 12'
    sections.append(f'<section class="slide {esc(s["layout"])}" id="slide-{s["id"]}" aria-label="{s["id"]}. {esc(s["title"])}" tabindex="-1"{ " hidden" if s["id"]!=1 else ""}>{body}<p class="folio">{label}</p></section>')
notes=json.dumps([{'title':s['title'],'notes':s['notes'],'status':s['status'],'seconds':s['seconds']} for s in DATA['slides']],ensure_ascii=False).replace('<','\\u003c')
options=''.join(f'<option value="{i}">{s["id"]}. {esc(s["title"])}</option>' for i,s in enumerate(DATA['slides']))
page=r'''<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="color-scheme" content="light"><meta name="description" content="Dearby 제품 재기획 발표자료. 본문 12장과 부록 3장."><title>Dearby | 제품 재기획 발표</title>
<style>
:root{--paper:#f7f8fa;--ink:#172422;--muted:#53635e;--accent:#246b59;--line:#d8e0dc;--soft:#e7eee9;color-scheme:light;font-family:"Apple SD Gothic Neo","Noto Sans KR","Malgun Gothic",sans-serif;color:var(--ink);background:var(--paper)}
*{box-sizing:border-box}body{margin:0}button,select{font:inherit;color:inherit}button,select{border:1px solid var(--line);background:var(--paper);border-radius:6px;min-height:40px;padding:7px 12px}button{cursor:pointer;white-space:nowrap}button:hover:not(:disabled){background:var(--soft)}button:disabled{opacity:.4;cursor:default}:focus-visible{outline:3px solid var(--accent);outline-offset:4px}[hidden]{display:none!important}.skip{position:fixed;top:-80px;left:20px;z-index:10;background:white;padding:12px;color:var(--accent)}.skip:focus{top:12px}.toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:12px 28px;border-bottom:1px solid var(--line);min-height:66px}.brand{color:var(--accent);font-size:22px;font-weight:800;letter-spacing:-.6px}.tools{display:flex;align-items:center;gap:8px}.slide-select{max-width:330px;font-size:14px}.stage{max-width:1440px;margin:0 auto}.slide{position:relative;min-height:min(810px,calc(100dvh - 150px));padding:clamp(28px,4.5vw,68px) clamp(24px,5.5vw,80px) 72px;display:flex;flex-direction:column}.slide:focus{outline:none}h1,h2,h3,p,ul,ol,figure{margin:0}h1{font-size:clamp(72px,7.5vw,110px);letter-spacing:-3px;color:var(--accent);line-height:1.08}h2{font-size:clamp(28px,3.45vw,49px);letter-spacing:-1.3px;line-height:1.3;font-weight:750;margin-bottom:clamp(28px,4vw,58px);word-break:keep-all}h3{font-size:clamp(21px,2.35vw,34px);color:var(--accent);letter-spacing:-.5px;line-height:1.4;margin-bottom:22px}p,li,td,th{word-break:keep-all;overflow-wrap:break-word}.content{flex:1;display:flex;flex-direction:column;justify-content:center}.cover{display:grid;grid-template-columns:1.1fr 1fr;align-items:center;gap:60px}.cover-copy{align-self:stretch;display:flex;flex-direction:column;justify-content:center}.subtitle{font-size:clamp(27px,3.3vw,47px);line-height:1.4;letter-spacing:-1px;font-weight:700;margin-top:44px}.caption{font-size:20px;color:var(--muted);margin-top:70px}.cover figure{width:100%;min-width:0}.cover img{width:100%;height:clamp(320px,38vw,515px);object-fit:cover;display:block}.cover figcaption{font-size:14px;color:var(--muted);margin-top:12px}.headline{font-size:clamp(33px,4.3vw,59px);line-height:1.3;letter-spacing:-1.7px;font-weight:750;color:var(--accent);margin-bottom:clamp(38px,5vw,72px)}.columns,.pairs{display:grid;grid-template-columns:1fr 1fr;gap:clamp(30px,5vw,75px)}.columns ul{list-style:none;padding:0;display:grid;gap:22px}.columns li,.pairs p,.sequence-list p{font-size:clamp(21px,2.15vw,30px);line-height:1.6}.priority{font-size:clamp(23px,2.65vw,37px);font-weight:700;line-height:1.65;color:var(--accent);margin-bottom:60px;word-break:keep-all}.sequence-list{list-style:none;padding:0;display:grid;gap:clamp(28px,4vw,57px)}.sequence-list li{display:grid;grid-template-columns:1fr 1.05fr;gap:40px;align-items:baseline}.sequence-list h3{margin:0}.footnote{font-size:clamp(15px,1.5vw,21px);line-height:1.5;color:var(--muted);margin-top:44px}.folio{position:absolute;right:clamp(24px,5vw,70px);bottom:20px;font-size:15px;color:var(--muted)}table{border-collapse:collapse;width:100%;font-size:clamp(20px,2.05vw,29px);line-height:1.4}th,td{text-align:left;padding:18px 20px;vertical-align:middle}thead{background:var(--soft);color:var(--accent)}tbody th{font-weight:500;min-width:165px}tbody tr:nth-child(even){background:#eff2f1}tbody tr{border-bottom:1px solid var(--line)}.table-scroll{width:100%;overflow:auto}.controls{display:flex;gap:16px;align-items:center;justify-content:center;padding:12px 22px 20px}.controls span{min-width:78px;text-align:center;color:var(--muted);font-size:14px}.help{text-align:center;color:var(--muted);font-size:13px;padding-bottom:15px}.notes{border-top:1px solid var(--line);max-width:1280px;margin:0 auto;padding:28px 38px 45px}.notes h2{font-size:24px;margin:0 0 14px}.notes p{font-size:19px;line-height:1.8;max-width:80ch}.notes .meta{font-size:14px;margin-top:16px;color:var(--muted)}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap}.notice{text-align:center;font-size:14px;color:var(--accent);padding:8px}.toolbar label{display:contents}:fullscreen .toolbar{padding-block:8px}:fullscreen .slide{min-height:calc(100dvh - 180px)}
@media(max-width:760px){.toolbar{padding:12px 18px;flex-wrap:wrap;gap:12px}.tools{flex-wrap:wrap;width:100%}.slide-select{flex:1;max-width:none;width:100%;min-width:160px}.brand{font-size:22px}.tools button{font-size:13px}.slide{min-height:0;padding:32px 24px 58px}.cover{grid-template-columns:1fr;gap:28px}.cover h1{font-size:72px}.subtitle{font-size:30px;margin-top:25px}.caption{font-size:16px;margin-top:26px}.cover img{height:280px;object-position:center}.cover figcaption{font-size:12px}.columns,.pairs{grid-template-columns:1fr;gap:32px}.headline{font-size:36px;letter-spacing:-1px;margin-bottom:32px}.sequence-list li{grid-template-columns:1fr;gap:10px}.sequence-list{gap:28px}.sequence-list p,.columns li,.pairs p{font-size:21px}.columns ul{gap:12px}h2{font-size:31px;margin-bottom:30px}h3{font-size:24px;margin-bottom:12px}.priority{font-size:25px;margin-bottom:32px}.footnote{font-size:15px;margin-top:30px}th,td{padding:12px 10px;font-size:17px}tbody th{min-width:85px}.notes{padding:24px}.help{padding-inline:24px}.content{display:block}.folio{font-size:13px}.controls button{flex:1;max-width:150px}}
@media print{@page{size:A4 landscape;margin:0}body{background:#fff}.toolbar,.controls,.help,.notes,.notice,.skip{display:none!important}.stage{max-width:none}.slide,.slide[hidden]{display:flex!important;min-height:0;height:210mm;width:297mm;padding:16mm 18mm 17mm;break-after:page;page-break-after:always;overflow:hidden;background:var(--paper);print-color-adjust:exact;-webkit-print-color-adjust:exact}.slide.cover{display:grid!important;grid-template-columns:1.1fr 1fr;gap:35px}h1{font-size:70px}.subtitle{font-size:32px}.caption{font-size:18px;margin-top:50px}.cover img{height:115mm}h2{font-size:35px;margin-bottom:26px}h3{font-size:26px}.headline{font-size:42px;margin-bottom:40px}.columns li,.pairs p,.sequence-list p{font-size:23px}.priority{font-size:26px;margin-bottom:36px}.sequence-list{gap:30px}.footnote{font-size:15px;margin-top:25px}th,td{font-size:21px;padding:13px}.slide:last-child{break-after:auto}.folio{bottom:8mm}.slide[hidden] *{visibility:visible}}
</style></head><body>
<a class="skip" href="#stage">발표 내용으로 이동</a>
<header class="toolbar"><span class="brand">Dearby</span><div class="tools"><label><span class="sr-only">슬라이드 선택</span><select id="slide-select" class="slide-select">__OPTIONS__</select></label><button id="notes-toggle" aria-expanded="false" aria-controls="notes">발표 원고</button><button id="fullscreen">전체화면</button><button id="print">인쇄 / PDF</button></div></header>
<main id="stage" class="stage">__SLIDES__</main>
<nav class="controls" aria-label="슬라이드 이동"><button id="prev" aria-label="이전 슬라이드">← 이전</button><span id="counter" aria-live="polite"></span><button id="next" aria-label="다음 슬라이드">다음 →</button></nav>
<p class="help">← → 슬라이드 이동 · N 발표 원고 · F 전체화면 · Home / End 처음 / 끝</p>
<p class="notice" id="notice" role="status" hidden></p>
<aside class="notes" id="notes" aria-label="발표자 원고" hidden><h2 id="notes-title"></h2><p id="notes-body"></p><p id="notes-meta" class="meta"></p></aside>
<script id="notes-data" type="application/json">__NOTES__</script>
<script>
'use strict';
const slides=[...document.querySelectorAll('.slide')];
const notes=JSON.parse(document.getElementById('notes-data').textContent);
const select=document.getElementById('slide-select');
const prev=document.getElementById('prev'),next=document.getElementById('next');
const notesPanel=document.getElementById('notes'),notesToggle=document.getElementById('notes-toggle');
const full=document.getElementById('fullscreen');
let current=0;
function show(index,updateHash=true){
 current=Math.max(0,Math.min(slides.length-1,index));
 slides.forEach((slide,i)=>{slide.hidden=i!==current;});
 select.value=String(current);prev.disabled=current===0;next.disabled=current===slides.length-1;
 document.getElementById('counter').textContent=(current+1)+' / '+slides.length;
 document.title='Dearby | '+notes[current].title;
 document.getElementById('notes-title').textContent=notes[current].title;
 document.getElementById('notes-body').textContent=notes[current].notes;
 document.getElementById('notes-meta').textContent=notes[current].status+(notes[current].seconds?' · 권장 '+notes[current].seconds+'초':' · 질의응답용 부록');
 if(updateHash){try{history.replaceState(null,'','#slide-'+(current+1));}catch{location.hash='slide-'+(current+1);}}
 window.scrollTo({top:0,behavior:'instant'});
}
function fromHash(){const m=location.hash.match(/^#slide-(\d+)$/);show(m?Number(m[1])-1:0,false);}
function toggleNotes(){notesPanel.hidden=!notesPanel.hidden;notesToggle.setAttribute('aria-expanded',String(!notesPanel.hidden));}
async function fullscreen(){
 try{if(document.fullscreenElement){await document.exitFullscreen();}else if(document.documentElement.requestFullscreen){await document.documentElement.requestFullscreen();}else{throw new Error('unsupported');}}
 catch{const el=document.getElementById('notice');el.hidden=false;el.textContent='이 브라우저에서는 전체화면 전환을 지원하지 않습니다. 브라우저의 전체화면 메뉴를 이용해 주세요.';}
}
prev.addEventListener('click',()=>show(current-1));next.addEventListener('click',()=>show(current+1));
select.addEventListener('change',()=>show(Number(select.value)));
notesToggle.addEventListener('click',toggleNotes);full.addEventListener('click',fullscreen);
document.getElementById('print').addEventListener('click',()=>window.print());
document.addEventListener('fullscreenchange',()=>{full.textContent=document.fullscreenElement?'전체화면 종료':'전체화면';});
document.addEventListener('keydown',event=>{
 if(event.ctrlKey||event.metaKey||event.altKey||event.target.closest('input,textarea,select,[contenteditable="true"]'))return;
 const k=event.key.toLowerCase();
 if(k===' '&&event.target.closest('button'))return;
 if(['arrowright','pagedown',' ','arrowleft','pageup','home','end','n','f'].includes(k))event.preventDefault();
 if(['arrowright','pagedown',' '].includes(k))show(current+1);
 else if(['arrowleft','pageup'].includes(k))show(current-1);
 else if(k==='home')show(0);else if(k==='end')show(slides.length-1);
 else if(k==='n')toggleNotes();else if(k==='f')fullscreen();
});
window.addEventListener('hashchange',fromHash);fromHash();
</script></body></html>'''
page=page.replace('__OPTIONS__',options).replace('__SLIDES__','\n'.join(sections)).replace('__NOTES__',notes)
(ROOT/'dearby-presentation.html').write_text(page)
print('Created standalone HTML with',len(sections),'slides')
