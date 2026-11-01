'use strict';
(() => {
  const q = s => document.querySelector(s);
  const KEY = 'vs_wm_settings';
  const FONT = '"Google Sans","Product Sans",Roboto,system-ui,-apple-system,sans-serif';

  let bmp = null, fileName = 'photo';

  // ---------- Tabs ----------
  function setMode(m) {
    q('#videoMode').hidden = m !== 'video';
    q('#wmMode').hidden = m !== 'wm';
    q('#tabVideo').classList.toggle('on', m === 'video');
    q('#tabWm').classList.toggle('on', m === 'wm');
    try { localStorage.setItem('vs_mode', m); } catch (e) {}
  }
  q('#tabVideo').onclick = () => setMode('video');
  q('#tabWm').onclick = () => setMode('wm');
  try { setMode(localStorage.getItem('vs_mode') === 'wm' ? 'wm' : 'video'); } catch (e) {}

  // ---------- Settings persistence ----------
  const ids = ['wmName', 'wmTags', 'wmPos', 'wmSize', 'wmOp', 'wmFmt'];
  function loadSettings() {
    try {
      const s = JSON.parse(localStorage.getItem(KEY) || '{}');
      ids.forEach(i => { if (s[i] !== undefined) q('#' + i).value = s[i]; });
      if (s.wmLogo !== undefined) q('#wmLogo').checked = s.wmLogo;
    } catch (e) {}
  }
  function saveSettings() {
    try {
      const s = {};
      ids.forEach(i => { s[i] = q('#' + i).value; });
      s.wmLogo = q('#wmLogo').checked;
      localStorage.setItem(KEY, JSON.stringify(s));
    } catch (e) {}
  }
  loadSettings();

  function opts() {
    return {
      name: q('#wmName').value.trim() || 'Pixel 10 Pro XL',
      tags: q('#wmTags').value.trim(),
      logo: q('#wmLogo').checked,
      pos: q('#wmPos').value,
      size: +q('#wmSize').value,
      op: +q('#wmOp').value
    };
  }

  // ---------- Drawing ----------
  function rrect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  // Stylised multicolour "G" mark drawn with arcs
  function gMark(ctx, cx, cy, R) {
    const t = R * 0.38, r = R - t / 2, d = Math.PI / 180;
    ctx.save();
    ctx.lineWidth = t; ctx.lineCap = 'butt';
    const arc = (c, a0, a1) => { ctx.strokeStyle = c; ctx.beginPath(); ctx.arc(cx, cy, r, a0 * d, a1 * d); ctx.stroke(); };
    arc('#EA4335', 200, 315);
    arc('#FBBC05', 135, 200);
    arc('#34A853', 45, 135);
    arc('#4285F4', 0, 45);
    ctx.fillStyle = '#4285F4';
    ctx.fillRect(cx, cy - t / 2, R, t);
    ctx.restore();
  }

  // Everything scales from the shorter image side, so preview == full-size output.
  function drawBadge(ctx, W, H, o) {
    const s = Math.min(W, H);
    const Hh = s * 0.0327 * (o.size / 100);   // badge height
    const m = s * 0.0275;                      // edge margin
    const f1 = Hh * 0.42, f2 = Hh * 0.175;
    const padL = Hh * 0.07, circ = Hh * 0.86, gap = Hh * 0.2, padR = Hh * 0.42;

    ctx.save();
    ctx.textBaseline = 'alphabetic';
    ctx.font = `600 ${f1}px ${FONT}`;
    const w1 = ctx.measureText(o.name).width;
    let w2 = 0;
    if (o.tags) { ctx.font = `500 ${f2}px ${FONT}`; w2 = ctx.measureText(o.tags).width; }
    const tw = Math.max(w1, w2);
    const left = o.logo ? padL + circ + gap : Hh * 0.45;
    const pw = left + tw + padR;

    let x = m, y = H - Hh - m;
    if (o.pos === 'bc') x = (W - pw) / 2;
    if (o.pos === 'br') x = W - pw - m;
    if (o.pos === 'tl') y = m;
    if (o.pos === 'tr') { x = W - pw - m; y = m; }

    ctx.fillStyle = `rgba(28,28,30,${o.op / 100})`;
    rrect(ctx, x, y, pw, Hh, Hh / 2);
    ctx.fill();

    if (o.logo) {
      const cx = x + padL + circ / 2, cy = y + Hh / 2;
      ctx.fillStyle = '#fff';
      ctx.beginPath(); ctx.arc(cx, cy, circ / 2, 0, Math.PI * 2); ctx.fill();
      gMark(ctx, cx, cy, circ * 0.31);
    }

    const tx = x + left;
    ctx.fillStyle = '#fff';
    ctx.font = `600 ${f1}px ${FONT}`;
    if (o.tags) {
      ctx.fillText(o.name, tx, y + Hh * 0.5);
      ctx.fillStyle = 'rgba(255,255,255,0.55)';
      ctx.font = `500 ${f2}px ${FONT}`;
      ctx.fillText(o.tags, tx, y + Hh * 0.5 + f2 * 1.45);
    } else {
      ctx.fillText(o.name, tx, y + Hh / 2 + f1 * 0.35);
    }
    ctx.restore();
  }

  // ---------- Preview ----------
  function setP(el) {
    const mn = +el.min, mx = +el.max;
    el.style.setProperty('--p', ((el.value - mn) / (mx - mn) * 100) + '%');
  }

  function renderPreview() {
    if (!bmp) return;
    const o = opts();
    const c = q('#wmPrev');
    const sc = Math.min(1, 1080 / bmp.width);
    c.width = Math.round(bmp.width * sc);
    c.height = Math.round(bmp.height * sc);
    const ctx = c.getContext('2d');
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(bmp, 0, 0, c.width, c.height);
    drawBadge(ctx, c.width, c.height, o);
    q('#wmSizeV').textContent = o.size + '%';
    q('#wmOpV').textContent = o.op + '%';
    setP(q('#wmSize')); setP(q('#wmOp'));
    q('#wmInfo').innerHTML = `Output: <b>${bmp.width}×${bmp.height}</b> (same as original)<br>Metadata such as GPS and camera info is not carried over.`;
  }

  ['wmName', 'wmTags', 'wmPos', 'wmSize', 'wmOp', 'wmFmt', 'wmLogo'].forEach(i => {
    q('#' + i).addEventListener('input', () => { saveSettings(); renderPreview(); });
  });

  q('#wmFile').onchange = async e => {
    const f = e.target.files[0];
    if (!f) return;
    q('#wmStatus').textContent = '';
    try {
      if (bmp && bmp.close) bmp.close();
      bmp = await createImageBitmap(f, { imageOrientation: 'from-image' });
    } catch (err) {
      q('#wmStatus').textContent = 'Cannot open this image.';
      return;
    }
    fileName = f.name.replace(/\.[^.]+$/, '');
    q('#wmDrop').classList.add('has');
    q('#wmDropTitle').textContent = f.name;
    q('#wmDropInfo').textContent = `${(f.size / 1048576).toFixed(1)} MB • ${bmp.width}×${bmp.height}`;
    q('#wmSettings').hidden = false;
    renderPreview();
  };

  // ---------- Export (full resolution) ----------
  async function exportBlob() {
    const o = opts();
    const c = document.createElement('canvas');
    c.width = bmp.width; c.height = bmp.height;
    const ctx = c.getContext('2d', { alpha: false });
    ctx.drawImage(bmp, 0, 0);
    drawBadge(ctx, c.width, c.height, o);
    const fmt = q('#wmFmt').value;
    const type = fmt === 'png' ? 'image/png' : 'image/jpeg';
    const quality = fmt === 'jpg100' ? 1 : 0.98;
    const blob = await new Promise(r => c.toBlob(r, type, quality));
    c.width = c.height = 1; // free memory
    return { blob, ext: fmt === 'png' ? 'png' : 'jpg', type };
  }

  q('#wmSave').onclick = async () => {
    if (!bmp) return;
    q('#wmStatus').textContent = 'Rendering…';
    try {
      const { blob, ext } = await exportBlob();
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = `${fileName}_wm.${ext}`;
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(() => URL.revokeObjectURL(a.href), 60000);
      q('#wmStatus').textContent = `Saved ${(blob.size / 1048576).toFixed(1)} MB • ${bmp.width}×${bmp.height}`;
    } catch (err) { q('#wmStatus').textContent = 'Failed: ' + err.message; }
  };

  q('#wmShare').onclick = async () => {
    if (!bmp) return;
    q('#wmStatus').textContent = 'Rendering…';
    try {
      const { blob, ext, type } = await exportBlob();
      const f = new File([blob], `${fileName}_wm.${ext}`, { type });
      if (navigator.canShare && navigator.canShare({ files: [f] })) {
        q('#wmStatus').textContent = '';
        await navigator.share({ files: [f] });
      } else q('#wmSave').click();
    } catch (err) { q('#wmStatus').textContent = ''; }
  };
})();
