'use strict';
const $ = s => document.querySelector(s);

const PRESETS = [
  { name: 'WhatsApp', mb: 32 },
  { name: 'Instagram DM', mb: 25 },
  { name: 'Instagram Post/Reel', mb: 100 },
  { name: 'Facebook', mb: 100 },
  { name: 'Discord', mb: 10 },
  { name: 'Email', mb: 25 },
  { name: 'Custom', mb: null }
];
const RES = [{ l: '480p', v: 480 }, { l: '720p', v: 720 }, { l: '1080p', v: 1080 }, { l: '2k', v: 1440 }, { l: '4k', v: 2160 }];
const FPS = [24, 25, 30, 50, 60];
const SPEEDS = [
  { n: 'Fast', p: 'ultrafast', h: 'Fastest, slightly softer picture' },
  { n: 'Balanced', p: 'veryfast', h: 'Good speed and quality' },
  { n: 'Quality', p: 'medium', h: 'Slowest, best quality' }
];
const VIVID_DEFAULT = { sat: 130, vib: 30, con: 105, bri: 0 };
const MAX_INPUT = 100 * 1048576; // 100 MB
const SPLIT_SEC = 30;
const MAX_VBPS = 40e6;

// When this page runs inside the WM/CMPRSS Android app, a native bridge is available
// (saving to the phone, keeping the screen on). In a normal browser it is null.
const APP = typeof window.AndroidBridge !== 'undefined' ? window.AndroidBridge : null;
if (APP) document.body.classList.add('inapp');

let file = null, meta = null, cancelled = false, busy = false, wake = null;
let parts = [], previewUrl = null, speedIdx = 1;
let ff = null, ffInput = null, logSink = null, curProg = null, lastSpeed = null;

// ---------- helpers ----------
const mbOf = b => (b / 1048576).toFixed(1);
function fmtDur(s) {
  const t = Math.round(s), h = Math.floor(t / 3600), m = Math.floor((t % 3600) / 60), x = t % 60;
  return h ? `${h}:${String(m).padStart(2, '0')}:${String(x).padStart(2, '0')}` : `${m}:${String(x).padStart(2, '0')}`;
}
function fmtEta(s) {
  if (s == null || !isFinite(s)) return '';
  s = Math.max(0, Math.round(s));
  return s >= 60 ? `~${Math.floor(s / 60)}m ${s % 60}s left` : `~${s}s left`;
}
const setStatus = t => { $('#status').textContent = t; };
const targetBytes = () => Math.max(1, parseFloat($('#mb').value) || 1) * 1048576;
const sleep = ms => new Promise(r => setTimeout(r, ms));
const inName = () => 'input' + (file && /\.mov$/i.test(file.name) ? '.mov' : '.mp4');

// ---------- UI: presets, speed, sliders ----------
const chips = $('#chips');
PRESETS.forEach((p, i) => {
  const b = document.createElement('button');
  b.className = 'chip' + (i === 0 ? ' on' : '');
  b.innerHTML = p.name + (p.mb ? `<small>${p.mb}MB</small>` : '');
  b.onclick = () => {
    [...chips.children].forEach(c => c.classList.remove('on'));
    b.classList.add('on');
    if (p.mb) $('#mb').value = p.mb; else $('#mb').focus();
    showPlan();
  };
  chips.appendChild(b);
});

const speedChips = $('#speedChips');
SPEEDS.forEach((s, i) => {
  const b = document.createElement('button');
  b.className = 'chip' + (i === speedIdx ? ' on' : '');
  b.textContent = s.n;
  b.onclick = () => {
    speedIdx = i;
    [...speedChips.children].forEach((c, k) => c.classList.toggle('on', k === i));
    $('#speedHint').textContent = s.h;
  };
  speedChips.appendChild(b);
});
$('#speedHint').textContent = SPEEDS[speedIdx].h;

const buildTicks = (id, labels) => { $(id).innerHTML = labels.map(l => `<span>${l}</span>`).join(''); };
buildTicks('#resTicks', RES.map(r => r.l));
buildTicks('#fpsTicks', FPS);

function setP(el) {
  const mn = +el.min, mx = +el.max;
  el.style.setProperty('--p', ((el.value - mn) / (mx - mn) * 100) + '%');
}

function vividValues() {
  return { sat: +$('#sat').value, vib: +$('#vib').value, con: +$('#con').value, bri: +$('#bri').value };
}

function syncSliders() {
  [['#res', '#resTicks'], ['#fps', '#fpsTicks']].forEach(([s, t]) => {
    const el = $(s), v = +el.value;
    el.style.setProperty('--p', (v / 4 * 100) + '%');
    [...$(t).children].forEach((c, i) => c.classList.toggle('on', i === v));
  });
  $('#ultraBox').classList.toggle('off', !$('#ultra').checked);

  ['#sat', '#vib', '#con', '#bri'].forEach(s => setP($(s)));
  const v = vividValues();
  $('#satV').textContent = v.sat + '%';
  $('#vibV').textContent = (v.vib > 0 ? '+' : '') + v.vib;
  $('#conV').textContent = v.con + '%';
  $('#briV').textContent = (v.bri > 0 ? '+' : '') + v.bri;
  $('#vividBox').hidden = !$('#vivid').checked;
  // approximate live preview (CSS filter); vibrance is approximated by extra saturation
  const sat = (v.sat / 100) * (1 + v.vib / 250);
  $('#vividPrev').style.filter = `saturate(${sat.toFixed(3)}) contrast(${(v.con / 100).toFixed(3)}) brightness(${(1 + v.bri / 100).toFixed(3)})`;
}
['#res', '#fps', '#flow', '#ultra', '#mb', '#split', '#vivid', '#sat', '#vib', '#con', '#bri']
  .forEach(s => $(s).addEventListener('input', () => { syncSliders(); showPlan(); }));
$('#vividReset').onclick = () => {
  $('#sat').value = VIVID_DEFAULT.sat; $('#vib').value = VIVID_DEFAULT.vib;
  $('#con').value = VIVID_DEFAULT.con; $('#bri').value = VIVID_DEFAULT.bri;
  syncSliders(); showPlan();
};
syncSliders();

// ---------- Ingest & profile scan ----------
const drop = $('#drop');
['dragenter', 'dragover'].forEach(ev => drop.addEventListener(ev, e => { e.preventDefault(); drop.classList.add('over'); }));
['dragleave', 'drop'].forEach(ev => drop.addEventListener(ev, e => { e.preventDefault(); drop.classList.remove('over'); }));
drop.addEventListener('drop', e => ingest(e.dataTransfer && e.dataTransfer.files[0]));
$('#file').onchange = e => { const f = e.target.files[0]; e.target.value = ''; ingest(f); };

const ingestError = t => { $('#ingestMsg').textContent = t; };

async function ingest(f) {
  if (!f || busy) return;
  ingestError('');
  const n = f.name.toLowerCase();
  if (!(/\.(mp4|mov)$/.test(n) || f.type === 'video/mp4' || f.type === 'video/quicktime')) {
    ingestError('Only MP4 or MOV files are supported.');
    return;
  }
  if (f.size > MAX_INPUT) {
    ingestError(`This file is ${mbOf(f.size)} MB. The limit is 100 MB.`);
    return;
  }
  let m;
  try { m = await readMeta(f); } catch (e) { ingestError('Cannot read this video. Try another file.'); return; }
  file = f; meta = m;
  await clearOutputs();
  setStatus('');
  $('#drop').classList.add('has');
  $('#dropTitle').textContent = f.name;
  $('#dropInfo').textContent = 'Tap or drop to replace';
  renderProfile();
  $('#settings').hidden = false;
  showPlan();
  grabPoster(f);
}

function readMeta(f) {
  return new Promise((res, rej) => {
    const v = document.createElement('video');
    const u = URL.createObjectURL(f);
    v.preload = 'metadata';
    v.onloadedmetadata = () => {
      const d = v.duration;
      URL.revokeObjectURL(u);
      if (!isFinite(d) || d <= 0 || !v.videoWidth) { rej(); return; }
      res({ duration: d, w: v.videoWidth, h: v.videoHeight, bitrate: f.size * 8 / d });
    };
    v.onerror = () => { URL.revokeObjectURL(u); rej(); };
    v.src = u;
  });
}

function grabPoster(f) {
  const v = document.createElement('video');
  const u = URL.createObjectURL(f);
  const done = () => { URL.revokeObjectURL(u); };
  v.muted = true; v.playsInline = true; v.preload = 'auto'; v.src = u;
  v.onloadeddata = () => { v.currentTime = Math.min(1, meta.duration / 2); };
  v.onseeked = () => {
    try {
      const k = Math.min(1, 640 / Math.max(v.videoWidth, v.videoHeight));
      const c = document.createElement('canvas');
      c.width = Math.round(v.videoWidth * k); c.height = Math.round(v.videoHeight * k);
      c.getContext('2d').drawImage(v, 0, 0, c.width, c.height);
      $('#vividPrev').src = c.toDataURL('image/jpeg', 0.85);
      $('#vividPrev').hidden = false;
    } catch (e) {}
    done();
  };
  v.onerror = done;
  setTimeout(done, 8000);
}

function renderProfile() {
  const kbps = meta.bitrate / 1000;
  const br = kbps >= 1000 ? (kbps / 1000).toFixed(1) + ' Mbps' : Math.round(kbps) + ' kbps';
  const short = Math.min(meta.w, meta.h);
  const cls = short >= 2160 ? '4K' : short >= 1440 ? '2K' : short >= 1080 ? '1080p' : short >= 720 ? '720p' : 'SD';
  const items = [
    ['Resolution', `${meta.w}×${meta.h}`],
    ['Duration', fmtDur(meta.duration)],
    ['Bitrate', br],
    ['Size', mbOf(file.size) + ' MB'],
    ['Class', cls],
    ['Format', /\.mov$/i.test(file.name) ? 'MOV' : 'MP4']
  ];
  $('#profile').innerHTML = items.map(([k, v]) => `<div><small>${k}</small><b>${v}</b></div>`).join('');
  $('#profile').hidden = false;
}

// ---------- Planning (our size-fit method) ----------
// srcFps is null until the file has been probed; then it decides blend / drop / keep.
function plan(tBytes, factor, dur, srcFps) {
  const { w, h } = meta;
  const ultra = $('#ultra').checked;
  const total = (tBytes * 8 * factor) / dur;
  let audioBps = 128000;
  if (total < 2.5e6) audioBps = 96000;
  if (total < 1e6) audioBps = 64000;
  const vbps = Math.min(MAX_VBPS, Math.max(80000, Math.floor(total - audioBps)));

  const targetFps = ultra ? FPS[+$('#fps').value] : 30;
  let fps = targetFps, mode = 'keep';
  if (srcFps) {
    if ($('#flow').checked && ultra && srcFps <= 35 && targetFps >= srcFps * 1.5) mode = 'blend';
    else if (targetFps < srcFps - 0.5) mode = 'drop';
    else fps = Math.max(1, Math.round(srcFps));
  }

  let scale = 1;
  if (ultra) scale = Math.min(1, RES[+$('#res').value].v / Math.min(w, h));
  const fit = Math.sqrt(vbps / (0.03 * w * h * fps)); // keep at least ~0.03 bits/pixel/frame
  let reduced = false;
  if (fit < scale) { scale = Math.max(0.2, fit); reduced = true; }
  const ev = n => Math.max(2, Math.round(n / 2) * 2);
  return { vbps, audioBps, fps, mode, tw: ev(w * scale), th: ev(h * scale), reduced, known: !!srcFps };
}

// Exact, back-to-back 30 s ranges: [0,30) [30,60) ... the last part keeps the remainder.
function chunkList() {
  const total = meta.duration;
  if (!$('#split').checked || total <= SPLIT_SEC + 0.05) return [{ start: 0, end: total }];
  const n = Math.ceil(total / SPLIT_SEC - 1e-9);
  return Array.from({ length: n }, (_, i) => ({ start: i * SPLIT_SEC, end: Math.min((i + 1) * SPLIT_SEC, total) }));
}

function showPlan() {
  if (!meta) return;
  const t = targetBytes();
  const chunks = chunkList();
  const first = chunks[0];
  const p = plan(t, 0.92, first.end - first.start, null);
  let h = '';
  if (chunks.length === 1 && file.size <= t) h += `<div class="good">Already under the limit (${mbOf(file.size)} MB). Compressing is optional.</div>`;
  h += `Output: <b>${p.tw}×${p.th}</b> @ up to <b>${p.fps} fps</b> • ~<b>${Math.round(p.vbps / 1000)} kbps</b>`;
  h += `<br>Encoder: FFmpeg.wasm • libx264 Main • AAC-LC`;
  if (chunks.length > 1) {
    const last = chunks[chunks.length - 1];
    h += `<br>Split: <b>${chunks.length} parts</b> × ${SPLIT_SEC}s (last part ${fmtDur(last.end - last.start)}), each up to ${mbOf(t)} MB`;
  } else if ($('#split').checked) {
    h += `<br>This video is ${SPLIT_SEC}s or shorter, so there is nothing to split.`;
  }
  if ($('#vivid').checked) h += `<br>Vivid color: <b>on</b>`;
  if ($('#ultra').checked && $('#flow').checked) h += `<br>Optical flow: <b>on</b> (frame blending, used when the video has fewer fps than the target)`;
  if (p.reduced) h += `<br><span class="warn">Resolution lowered automatically so the picture stays clean at this size limit.</span>`;
  if (p.vbps <= 80000) h += `<br><span class="warn">Limit is too small for this duration.</span>`;
  $('#plan').innerHTML = h;
}

// ---------- FFmpeg.wasm (loaded from the local ffmpeg/ folder, no network) ----------
function showThread() {
  const sp = lastSpeed ? ` • ${lastSpeed.toFixed(2)}× realtime` : '';
  $('#thread').textContent = `FFmpeg.wasm • single-thread core • ${SPEEDS[speedIdx].p}${sp}`;
}

async function loadFF() {
  if (ff) return ff;
  const inst = new FFmpegWASM.FFmpeg();
  inst.on('log', ({ message }) => {
    if (logSink) logSink.push(message);
    const sp = /speed=\s*([\d.]+)x/.exec(message);
    if (sp) { lastSpeed = parseFloat(sp[1]); showThread(); }
    const tm = /time=(\d+):(\d+):(\d+(?:\.\d+)?)/.exec(message);
    if (tm && curProg) curProg(+tm[1] * 3600 + +tm[2] * 60 + parseFloat(tm[3]));
  });
  inst.on('progress', ({ time }) => { if (curProg && time > 0) curProg(time / 1e6); });
  const base = new URL('ffmpeg/', location.href).href;
  // read the local files ourselves and hand FFmpeg blob: URLs (works from any local origin)
  const blobURL = async (url, type) => {
    const r = await fetch(url);
    if (!r.ok) throw new Error('Missing file: ' + url);
    return URL.createObjectURL(new Blob([await r.arrayBuffer()], { type }));
  };
  const coreURL = await blobURL(base + 'ffmpeg-core.js', 'text/javascript');
  const wasmURL = await blobURL(base + 'ffmpeg-core.wasm', 'application/wasm');
  const workerURL = await blobURL(base + '814.ffmpeg.js', 'text/javascript');
  // FFmpeg starts its (classic) worker from ffmpeg/814.ffmpeg.js; point that at our blob copy
  const NativeWorker = window.Worker;
  window.Worker = function (url, opts) {
    return new NativeWorker(String(url).endsWith('814.ffmpeg.js') ? workerURL : url, opts);
  };
  try {
    await inst.load({ coreURL, wasmURL });
  } finally {
    window.Worker = NativeWorker;
  }
  ff = inst; ffInput = null;
  return ff;
}

async function ensureInput(ffi) {
  if (ffInput === file) return;
  await ffi.writeFile(inName(), new Uint8Array(await file.arrayBuffer()));
  ffInput = file;
}

async function dropInput() {
  if (ff && ffInput) { try { await ff.deleteFile(inName()); } catch (e) {} }
  ffInput = null;
}

// Reads fps / HDR / audio from FFmpeg's own stream report.
async function probe(ffi) {
  logSink = [];
  try { await ffi.exec(['-hide_banner', '-i', inName()]); } catch (e) {}
  const text = logSink.join('\n');
  logSink = null;
  const v = (text.match(/Stream #0:\d+[^\n]*Video:[^\n]*/) || [''])[0];
  const fm = v.match(/([\d.]+) fps/);
  return {
    fps: fm ? parseFloat(fm[1]) : null,
    hdr: /arib-std-b67|smpte2084/.test(v),
    audio: /Stream #0:\d+[^\n]*Audio:/.test(text)
  };
}

// Manual "Vivid color" -> FFmpeg filters (eq for saturation/contrast/brightness, vibrance for muted colors)
function vividFilters() {
  if (!$('#vivid').checked) return [];
  const v = vividValues();
  const sat = v.sat / 100, con = v.con / 100, bri = v.bri / 100, vib = v.vib / 100 * 2;
  const f = [];
  if (Math.abs(sat - 1) > 0.005 || Math.abs(con - 1) > 0.005 || Math.abs(bri) > 0.0005) {
    f.push(`eq=contrast=${con.toFixed(3)}:saturation=${sat.toFixed(3)}:brightness=${bri.toFixed(3)}`);
  }
  if (Math.abs(vib) > 0.01) f.push('format=gbrp', `vibrance=intensity=${vib.toFixed(3)}`, 'format=yuv420p');
  return f;
}

function ffArgs(p, r, info, outName) {
  const dur = r.end - r.start;
  const vf = [];
  if (p.tw !== meta.w || p.th !== meta.h) vf.push(`scale=${p.tw}:${p.th}:flags=lanczos`);
  if (info.hdr) {
    // HDR (HLG / PQ) -> SDR so colors are not washed out
    vf.push('zscale=t=linear:npl=100', 'format=gbrpf32le', 'zscale=p=bt709', 'tonemap=tonemap=hable:desat=0', 'zscale=t=bt709:m=bt709:r=tv', 'format=yuv420p');
  }
  if (p.mode === 'blend') { vf.push(...vividFilters()); vf.push(`framerate=fps=${p.fps}`); }
  else if (p.mode === 'drop') { vf.push(`fps=${p.fps}`); vf.push(...vividFilters()); }
  else vf.push(...vividFilters());
  vf.push('format=yuv420p');

  const V = String(p.vbps);
  const args = [];
  if (r.start > 0.001) args.push('-ss', r.start.toFixed(3));
  args.push('-i', inName(), '-t', dur.toFixed(3));
  args.push('-map', '0:v:0', '-map', '0:a:0?', '-vf', vf.join(','));
  args.push('-c:v', 'libx264', '-profile:v', 'main', '-preset', SPEEDS[speedIdx].p, '-pix_fmt', 'yuv420p',
    '-b:v', V, '-maxrate', V, '-bufsize', String(p.vbps * 2), '-g', String(Math.max(1, p.fps * 2)));
  if (info.hdr) args.push('-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709');
  args.push('-c:a', 'aac', '-profile:a', 'aac_low', '-b:a', String(p.audioBps), '-ac', '2',
    '-movflags', '+faststart', '-y', outName);
  return args;
}

// ---------- Progress track ----------
const STAGES = ['init', 'read', 'encode', 'finish'];
function setStage(name) {
  const i = STAGES.indexOf(name);
  document.querySelectorAll('#stages li').forEach((li, k) => {
    li.classList.toggle('done', k < i);
    li.classList.toggle('active', k === i);
  });
}
function finishStages() {
  document.querySelectorAll('#stages li').forEach(li => { li.classList.add('done'); li.classList.remove('active'); });
}
function updateProgress(frac, partText, eta) {
  $('#bar').style.width = (frac * 100).toFixed(1) + '%';
  $('#pct').textContent = Math.round(frac * 100) + '%';
  $('#part').textContent = partText || '';
  $('#eta').textContent = fmtEta(eta);
}

// ---------- Local storage for finished parts (disk-backed, keeps RAM low) ----------
let store = null;
async function getStore() {
  if (store !== null) return store || null;
  try {
    const root = await navigator.storage.getDirectory();
    store = await root.getDirectoryHandle('wm-out', { create: true });
  } catch (e) { store = false; }
  return store || null;
}
async function storeFile(name, data) {
  const dir = await getStore();
  if (dir) {
    try {
      const fh = await dir.getFileHandle(name, { create: true });
      const w = await fh.createWritable();
      await w.write(data);
      await w.close();
      return await fh.getFile();
    } catch (e) { /* fall back to memory */ }
  }
  return new Blob([data], { type: 'video/mp4' });
}
async function removeStored(name) {
  const dir = await getStore();
  if (dir) { try { await dir.removeEntry(name); } catch (e) {} }
}
async function wipeStore() {
  const dir = await getStore();
  if (!dir) return;
  try { for await (const [n] of dir.entries()) { await dir.removeEntry(n); } } catch (e) {}
}
wipeStore();

// ---------- Run ----------
$('#go').onclick = async () => {
  if (busy || !file) return;
  if (typeof WebAssembly !== 'object' || typeof FFmpegWASM === 'undefined') {
    setStatus('FFmpeg files are missing. Keep the "ffmpeg" folder next to index.html and reload.');
    return;
  }
  busy = true; cancelled = false;
  await clearOutputs();
  setStatus('');
  $('#go').disabled = true; $('#cancel').hidden = false; $('#stages').hidden = false;
  lastSpeed = null; showThread();
  if (APP) APP.keepAwake(true);
  else { try { if (navigator.wakeLock) wake = await navigator.wakeLock.request('screen'); } catch (e) {} }

  const t0 = performance.now();
  const tBytes = targetBytes();
  const chunks = chunkList();
  const totalDur = chunks.reduce((a, c) => a + (c.end - c.start), 0);
  const base = file.name.replace(/\.[^.]+$/, '');
  let doneDur = 0;

  try {
    setStage('init');
    updateProgress(0, 'Loading FFmpeg core…', null);
    const ffi = await loadFF();

    setStage('read');
    updateProgress(0, 'Reading file…', null);
    await ensureInput(ffi);
    const info = await probe(ffi);
    if (info.hdr) setStatus('HDR video detected: converting to normal SDR colors (slower).');

    setStage('encode');
    for (let i = 0; i < chunks.length && !cancelled; i++) {
      const r = chunks[i], dur = r.end - r.start;
      const outName = `out_${i + 1}.mp4`;
      let factor = 0.92, best = null;

      for (let attempt = 1; attempt <= 2; attempt++) {
        const p = plan(tBytes, factor, dur, info.fps);
        let sec = 0;
        curProg = t => {
          sec = Math.max(sec, t);
          const frac = Math.min(1, sec / dur);
          const overall = (doneDur + frac * dur) / totalDur;
          const elapsed = (performance.now() - t0) / 1000;
          const eta = overall > 0.02 ? elapsed * (1 - overall) / overall : null;
          updateProgress(overall, `Part ${i + 1}/${chunks.length} • pass ${attempt}/2`, eta);
        };
        curProg(0);
        const code = await ffi.exec(ffArgs(p, r, info, outName));
        curProg = null;
        if (cancelled) { best = null; break; }
        if (code !== 0) throw new Error(`FFmpeg stopped (code ${code}). Try the Fast speed or a lower resolution.`);
        const data = await ffi.readFile(outName);
        await ffi.deleteFile(outName);
        if (!best || data.length < best.length) best = data;
        if (data.length <= tBytes) { best = data; break; }
        factor *= (tBytes / data.length) * 0.95;
      }
      if (!best || cancelled) break;

      doneDur += dur;
      const suffix = chunks.length > 1 ? `_part${String(i + 1).padStart(2, '0')}` : '_compressed';
      const name = `${base}${suffix}.mp4`;
      const stored = await storeFile(name, best);
      best = null; // release the copy held in JS memory
      parts.push({ name, blob: stored, dur, fits: stored.size <= tBytes });
      renderOutputs();
    }

    if (cancelled) {
      setStatus('Cancelled.');
    } else {
      setStage('finish');
      finishStages();
      updateProgress(1, 'Done', 0);
      if (!info.audio) setStatus('Note: this video has no audio track.');
    }
  } catch (err) {
    setStatus(cancelled ? 'Cancelled.' : 'Failed: ' + (err && err.message ? err.message : err));
  }

  curProg = null;
  await dropInput();
  if (APP) APP.keepAwake(false);
  if (wake) { wake.release().catch(() => {}); wake = null; }
  $('#go').disabled = false; $('#cancel').hidden = true; busy = false;
};

$('#cancel').onclick = () => {
  cancelled = true;
  if (ff) { try { ff.terminate(); } catch (e) {} ff = null; ffInput = null; } // stops encoding right away
};

// ---------- Output workspace (blob URLs are created only when needed) ----------
const outList = $('#outList');

function renderOutputs() {
  $('#outputs').hidden = parts.length === 0;
  const total = parts.reduce((a, p) => a + p.blob.size, 0);
  $('#outTotal').textContent = `${parts.length} file${parts.length === 1 ? '' : 's'} • ${mbOf(total)} MB total`;
  outList.innerHTML = parts.map((p, i) => `
    <div class="orow" data-i="${i}">
      <div class="oinfo"><b>${p.name}</b><small class="${p.fits ? '' : 'bad'}">${fmtDur(p.dur)} • ${mbOf(p.blob.size)} MB${p.fits ? '' : ' • over limit'}</small></div>
      <div class="obtn">
        <button data-a="play">Play</button>
        <button data-a="dl">Save</button>
        <button data-a="rm">✕</button>
      </div>
    </div>`).join('');
  if (parts.length === 0) hidePreview();
}

const asMp4 = blob => blob.slice(0, blob.size, 'video/mp4');

const blobToB64 = b => new Promise((res, rej) => {
  const fr = new FileReader();
  fr.onload = () => res(String(fr.result).split(',')[1] || '');
  fr.onerror = rej;
  fr.readAsDataURL(b);
});

// Inside the app: stream the file to Movies/WM-CMPRSS in 1 MB pieces (low RAM).
async function saveInApp(p) {
  const id = APP.beginSave(p.name);
  if (id < 0) { setStatus('Could not save the file.'); return false; }
  const CH = 1 << 20;
  for (let o = 0; o < p.blob.size; o += CH) {
    const ok = APP.writeChunk(id, await blobToB64(p.blob.slice(o, o + CH)));
    if (!ok) { APP.cancelSave(id); setStatus('Saving failed.'); return false; }
  }
  setStatus('Saved to ' + APP.endSave(id));
  return true;
}

async function saveBlob(p) {
  if (APP) return saveInApp(p);
  const url = URL.createObjectURL(asMp4(p.blob));
  const a = document.createElement('a');
  a.href = url; a.download = p.name;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
  return true;
}

function hidePreview() {
  const v = $('#preview');
  v.pause(); v.removeAttribute('src'); v.load(); v.hidden = true;
  if (previewUrl) { URL.revokeObjectURL(previewUrl); previewUrl = null; }
}

async function clearOutputs() {
  hidePreview();
  parts = [];
  renderOutputs();
  await wipeStore();
}

outList.addEventListener('click', async e => {
  const btn = e.target.closest('button');
  if (!btn) return;
  const i = +btn.closest('.orow').dataset.i;
  const p = parts[i];
  if (!p) return;
  const a = btn.dataset.a;
  if (a === 'dl') await saveBlob(p);
  if (a === 'play') {
    hidePreview();
    previewUrl = URL.createObjectURL(asMp4(p.blob));
    const v = $('#preview');
    v.src = previewUrl; v.hidden = false; v.play().catch(() => {});
    v.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }
  if (a === 'rm') { hidePreview(); parts.splice(i, 1); renderOutputs(); removeStored(p.name); }
});

$('#dlAll').onclick = async () => {
  for (const p of [...parts]) { await saveBlob(p); if (!APP) await sleep(700); }
};
$('#clearAll').onclick = () => { clearOutputs(); };

// ---------- PWA ----------
const APP_VERSION = 'v7';
if ($('#ver')) $('#ver').textContent = APP_VERSION;
if (!APP && 'serviceWorker' in navigator) {
  const hadController = !!navigator.serviceWorker.controller;
  let reloaded = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (!hadController || reloaded) return;
    reloaded = true;
    location.reload(); // new version took over, load it now
  });
  navigator.serviceWorker.register('sw.js', { updateViaCache: 'none' })
    .then(r => { r.update(); document.addEventListener('visibilitychange', () => { if (!document.hidden) r.update(); }); })
    .catch(() => {});
}
let deferred = null;
window.addEventListener('beforeinstallprompt', e => { e.preventDefault(); deferred = e; $('#install').hidden = false; });
$('#install').onclick = async () => { if (!deferred) return; deferred.prompt(); await deferred.userChoice; deferred = null; $('#install').hidden = true; };
