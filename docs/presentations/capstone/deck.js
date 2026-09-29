/* Slide content lives in slides.md. This file only supplies navigation/layout. */
const deck = new Reveal({
  width: 1280, height: 720, margin: 0.025, center: false,
  hash: true, controls: true, controlsTutorial: false, progress: true,
  transition: 'none', backgroundTransition: 'none',
  slideNumber: false, keyboard: {78: () => toggleNotes()},
  pdfSeparateFragments: false, plugins: [RevealMarkdown]
});
const notes = document.getElementById('notes');
const toggle = document.getElementById('notes-toggle');
function toggleNotes(force) {
  notes.hidden = typeof force === 'boolean' ? !force : !notes.hidden;
  toggle.setAttribute('aria-expanded', String(!notes.hidden));
}
function update() {
  document.getElementById('position').textContent = `${deck.getIndices().h + 1} / ${deck.getTotalSlides()}`;
  document.getElementById('notes-content').innerHTML = deck.getCurrentSlide()?.querySelector('aside.notes')?.innerHTML || '발표 노트가 없습니다.';
}
const zoom = document.createElement('dialog');
const close = document.createElement('button');
close.textContent = '이미지 닫기';
close.addEventListener('click', () => zoom.close());
const zoomImage = document.createElement('img');
zoom.append(close, zoomImage);
zoom.addEventListener('click', event => { if (event.target === zoom) zoom.close(); });
zoom.addEventListener('keydown', event => event.stopPropagation());
document.body.append(zoom);
deck.initialize().then(() => {
  document.querySelectorAll('.slides section').forEach(section => {
    if (section.querySelector('.footnotes')) section.classList.add('has-footnotes');
  });
  document.querySelectorAll('section.screens').forEach(section => {
    const grid = document.createElement('div');
    grid.className = 'screen-grid';
    section.querySelectorAll('p > img').forEach(img => {
      const paragraph = img.parentElement;
      const figure = document.createElement('figure');
      const caption = document.createElement('figcaption');
      caption.textContent = img.alt;
      img.tabIndex = 0;
      img.setAttribute('role', 'button');
      img.setAttribute('aria-label', `${img.alt} 원본 확대`);
      const open = () => { zoomImage.src = img.src; zoomImage.alt = img.alt; zoom.showModal(); };
      img.addEventListener('click', open);
      img.addEventListener('keydown', event => { if (event.key === 'Enter') { event.stopPropagation(); open(); } });
      figure.append(img, caption);
      grid.append(figure);
      if (!paragraph.textContent.trim() && !paragraph.children.length) paragraph.remove();
    });
    section.append(grid);
    const status = document.createElement('div');
    status.className = 'screen-status';
    const isCard = Boolean(section.querySelector('img[src*="profile"],img[src*="card"],img[src*="qr-"],img[src*="wallet"]')) && !section.querySelector('img[src*="calendar"]');
    status.textContent = isCard ? '승인 시안 · 명함/프로필은 현재 기본 화면에서 숨김' : '승인 시안 · 이미지의 활동·일정은 예시';
    section.append(status);
  });
  update();
  deck.layout();
});
deck.on('slidechanged', update);
document.getElementById('overview').addEventListener('click', () => deck.toggleOverview());
toggle.addEventListener('click', () => toggleNotes());
document.getElementById('notes-close').addEventListener('click', () => toggleNotes(false));
document.getElementById('fullscreen').addEventListener('click', () => {
  if (document.fullscreenElement) document.exitFullscreen();
  else document.documentElement.requestFullscreen?.();
});
