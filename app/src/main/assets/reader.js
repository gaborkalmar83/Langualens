/* LinguaLens bilingual reader.
 * Walks the page, splits Dutch into sentences, and asks the Android side for
 * English which is then inserted directly underneath each sentence.
 * Non-destructive: only text nodes are wrapped, links and layout stay intact.
 */
(function () {
  if (window.__tolkLoaded) { return; }
  window.__tolkLoaded = true;

  var MODE = window.__tolkMode || 'sentence';   // 'sentence' | 'paragraph'
  var HIDDEN = !!window.__tolkHidden;

  var SKIP_TAGS = {
    SCRIPT: 1, STYLE: 1, NOSCRIPT: 1, CODE: 1, PRE: 1, TEXTAREA: 1,
    SELECT: 1, OPTION: 1, IFRAME: 1, SVG: 1, CANVAS: 1, INPUT: 1,
    BUTTON: 1, NAV: 1, FOOTER: 1
  };
  var BLOCK_SELECTOR = 'p, li, blockquote, dd, h1, h2, h3, h4, figcaption, td, article > div';

  var counter = 0;
  var pending = {};
  var queue = [];
  var flushTimer = null;

  /* ---------------- styling ---------------- */
  var style = document.createElement('style');
  style.textContent =
    '.tolk-en{display:block !important;color:#2f6fe4;font-style:italic;' +
    'font-size:0.92em;line-height:1.4;margin:3px 0 12px 0;' +
    'border-left:3px solid rgba(47,111,228,.35);padding-left:9px;' +
    'font-family:inherit;text-align:left;}' +
    '.tolk-en:empty{display:none !important;}' +
    '.tolk-en.tolk-veil{color:transparent;background:rgba(47,111,228,.13);' +
    'border-radius:5px;cursor:pointer;user-select:none;}' +
    '.tolk-en.tolk-veil::after{content:"tik voor Engels";color:#2f6fe4;' +
    'font-size:.8em;opacity:.75;}' +
    '.tolk-en.tolk-veil.tolk-open{color:#2f6fe4;background:transparent;}' +
    '.tolk-en.tolk-veil.tolk-open::after{content:"";}' +
    '@media (prefers-color-scheme: dark){.tolk-en{color:#7fb2ff;' +
    'border-left-color:rgba(127,178,255,.4);}}' +
    '#tolk-bar{position:fixed;left:0;right:0;bottom:0;z-index:2147483647;' +
    'display:none;gap:8px;padding:10px 12px;background:rgba(20,24,33,.96);' +
    'font-family:-apple-system,Roboto,sans-serif;box-shadow:0 -2px 12px rgba(0,0,0,.4);}' +
    '#tolk-bar button{flex:1;border:none;border-radius:10px;padding:12px 8px;' +
    'font-size:14px;font-weight:600;background:#2f6fe4;color:#fff;}' +
    '#tolk-bar button.sec{background:#2c3446;color:#dbe4f5;}' +
    '#tolk-progress{position:fixed;top:0;left:0;height:3px;width:0;' +
    'background:#2f6fe4;z-index:2147483647;transition:width .25s ease;}';
  (document.head || document.documentElement).appendChild(style);

  var progress = document.createElement('div');
  progress.id = 'tolk-progress';
  document.documentElement.appendChild(progress);

  /* ---------------- selection action bar ---------------- */
  var bar = document.createElement('div');
  bar.id = 'tolk-bar';
  bar.innerHTML =
    '<button id="tolk-save">Bewaar</button>' +
    '<button id="tolk-look" class="sec">Vertaal</button>' +
    '<button id="tolk-say" class="sec">Spreek uit</button>';
  document.documentElement.appendChild(bar);

  function selectedText() {
    var s = window.getSelection();
    return s ? String(s).trim() : '';
  }

  document.addEventListener('selectionchange', function () {
    var t = selectedText();
    bar.style.display = t.length > 0 ? 'flex' : 'none';
  });

  bar.addEventListener('mousedown', function (e) { e.preventDefault(); });

  document.getElementById('tolk-save').addEventListener('click', function () {
    var t = selectedText();
    if (!t) { return; }
    var ctx = contextFor(t);
    LinguaLens.save(t, ctx, document.title + ' — ' + location.href);
    bar.style.display = 'none';
    var s = window.getSelection(); if (s) { s.removeAllRanges(); }
  });

  document.getElementById('tolk-look').addEventListener('click', function () {
    var t = selectedText();
    if (t) { LinguaLens.lookup(t); }
  });

  document.getElementById('tolk-say').addEventListener('click', function () {
    var t = selectedText();
    if (t) { LinguaLens.speak(t); }
  });

  function contextFor(text) {
    var s = window.getSelection();
    if (!s || s.rangeCount === 0) { return ''; }
    var node = s.getRangeAt(0).startContainer;
    var el = node.nodeType === 3 ? node.parentElement : node;
    var host = el ? el.closest('.tolk-sent, p, li, blockquote, td, h1, h2, h3') : null;
    var ctx = host ? host.textContent.trim() : '';
    return ctx.length > 400 ? ctx.slice(0, 400) : ctx;
  }

  /* ---------------- sentence splitting ---------------- */
  var ABBR = ['dhr', 'mevr', 'mw', 'bijv', 'bv', 'enz', 'nr', 'art', 'blz', 'ca',
    'dr', 'prof', 'ir', 'drs', 'mr', 'st', 'nl', 'zgn', 'evt', 'tel', 'fig',
    'incl', 'excl', 'max', 'min', 'vs', 'etc', 'jr', 'sr', 'ong', 'resp', 'pag'];

  function splitSentences(text) {
    var out = [];
    var buf = '';
    for (var i = 0; i < text.length; i++) {
      var c = text[i];
      buf += c;
      if (c === '.' || c === '!' || c === '?' || c === '…') {
        var j = i + 1;
        while (j < text.length && '"”\')».!?'.indexOf(text[j]) >= 0) {
          buf += text[j]; j++;
        }
        var next = j < text.length ? text[j] : null;
        var boundary = next === null || next === ' ' || next === '\n' || next === '\t';
        if (boundary && !endsAbbr(buf)) {
          out.push(buf);
          buf = '';
        }
        i = j - 1;
      }
    }
    if (buf.trim().length) { out.push(buf); }
    return out;
  }

  function endsAbbr(s) {
    var t = s.replace(/\s+$/, '');
    if (t.charAt(t.length - 1) !== '.') { return false; }
    var body = t.slice(0, -1);
    var m = body.match(/([A-Za-zÀ-ÿ\.]+)$/);
    if (!m) { return false; }
    var tok = m[1].toLowerCase();
    if (tok.length === 1) { return true; }               // initial, e.g. "J."
    return ABBR.indexOf(tok) >= 0;
  }

  /* ---------------- collecting work ---------------- */
  function isVisible(el) {
    if (!el) { return false; }
    var s = window.getComputedStyle(el);
    return s && s.display !== 'none' && s.visibility !== 'hidden';
  }

  function hasLetters(s) { return /[a-zA-ZÀ-ÿ]/.test(s); }

  function makeSlot(afterNode, parent, text) {
    var id = 't' + (++counter);
    var slot = document.createElement('span');
    slot.className = 'tolk-en' + (HIDDEN ? ' tolk-veil' : '');
    slot.setAttribute('data-tolk', id);
    if (HIDDEN) {
      slot.addEventListener('click', function () { slot.classList.toggle('tolk-open'); });
    }
    if (afterNode && afterNode.parentNode) {
      afterNode.parentNode.insertBefore(slot, afterNode.nextSibling);
    } else if (parent) {
      parent.appendChild(slot);
    }
    pending[id] = slot;
    queue.push({ id: id, text: text });
    return slot;
  }

  function collectParagraphMode() {
    var blocks = document.querySelectorAll(BLOCK_SELECTOR);
    for (var i = 0; i < blocks.length; i++) {
      var el = blocks[i];
      if (el.querySelector('.tolk-en')) { continue; }
      if (el.closest('.tolk-en')) { continue; }
      if (SKIP_TAGS[el.tagName]) { continue; }
      if (!isVisible(el)) { continue; }
      var txt = (el.innerText || el.textContent || '').trim();
      if (txt.length < 25 || !hasLetters(txt)) { continue; }
      if (txt.length > 1800) { txt = txt.slice(0, 1800); }
      makeSlot(null, el, txt);
    }
  }

  function collectSentenceMode() {
    var walker = document.createTreeWalker(
      document.body, NodeFilter.SHOW_TEXT,
      {
        acceptNode: function (node) {
          var t = node.nodeValue;
          if (!t || t.trim().length < 20 || !hasLetters(t)) {
            return NodeFilter.FILTER_REJECT;
          }
          var p = node.parentElement;
          if (!p || SKIP_TAGS[p.tagName]) { return NodeFilter.FILTER_REJECT; }
          if (p.classList.contains('tolk-en') || p.closest('.tolk-en')) {
            return NodeFilter.FILTER_REJECT;
          }
          if (p.closest('#tolk-bar')) { return NodeFilter.FILTER_REJECT; }
          if (!isVisible(p)) { return NodeFilter.FILTER_REJECT; }
          return NodeFilter.FILTER_ACCEPT;
        }
      }
    );

    var nodes = [];
    var n;
    while ((n = walker.nextNode())) { nodes.push(n); }

    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i];
      var parent = node.parentNode;
      if (!parent) { continue; }
      var sentences = splitSentences(node.nodeValue);
      if (!sentences.length) { continue; }

      var frag = document.createDocumentFragment();
      var slots = [];
      for (var k = 0; k < sentences.length; k++) {
        var raw = sentences[k];
        var trimmed = raw.trim();
        var span = document.createElement('span');
        span.className = 'tolk-sent';
        span.appendChild(document.createTextNode(raw));
        frag.appendChild(span);
        if (trimmed.length >= 12 && hasLetters(trimmed)) {
          var id = 't' + (++counter);
          var slot = document.createElement('span');
          slot.className = 'tolk-en' + (HIDDEN ? ' tolk-veil' : '');
          slot.setAttribute('data-tolk', id);
          if (HIDDEN) {
            (function (s) {
              s.addEventListener('click', function () { s.classList.toggle('tolk-open'); });
            })(slot);
          }
          frag.appendChild(slot);
          pending[id] = slot;
          slots.push({ id: id, text: trimmed });
        }
      }
      parent.replaceChild(frag, node);
      for (var q = 0; q < slots.length; q++) { queue.push(slots[q]); }
    }
  }

  /* ---------------- talking to Android ---------------- */
  var total = 0, done = 0;

  function flush() {
    if (!queue.length) { return; }
    var batch = queue.splice(0, 25);
    total += batch.length;
    try {
      LinguaLens.requestTranslate(JSON.stringify(batch));
    } catch (e) { /* bridge missing */ }
    if (queue.length) {
      flushTimer = setTimeout(flush, 60);
    }
  }

  window.tolkApply = function (json) {
    var map;
    try { map = JSON.parse(json); } catch (e) { return; }
    for (var id in map) {
      var slot = pending[id];
      if (slot) {
        slot.textContent = map[id];
        delete pending[id];
        done++;
      }
    }
    var pct = total ? Math.round((done / total) * 100) : 100;
    progress.style.width = pct + '%';
    if (pct >= 100) {
      setTimeout(function () { progress.style.opacity = '0'; }, 500);
    }
  };

  window.tolkRun = function () {
    counter = 0;
    if (MODE === 'paragraph') { collectParagraphMode(); } else { collectSentenceMode(); }
    flush();
    try { LinguaLens.onReady(queue.length + total); } catch (e) {}
  };

  window.tolkSetHidden = function (hidden) {
    HIDDEN = !!hidden;
    var els = document.querySelectorAll('.tolk-en');
    for (var i = 0; i < els.length; i++) {
      if (HIDDEN) { els[i].classList.add('tolk-veil'); }
      else { els[i].classList.remove('tolk-veil', 'tolk-open'); }
    }
  };

  window.tolkClear = function () {
    var els = document.querySelectorAll('.tolk-en');
    for (var i = 0; i < els.length; i++) { els[i].parentNode.removeChild(els[i]); }
    pending = {}; queue = []; total = 0; done = 0;
    window.__tolkLoaded = true;
  };

  // Re-scan when infinite-scroll pages add content.
  var rescanTimer = null;
  var observer = new MutationObserver(function () {
    if (rescanTimer) { clearTimeout(rescanTimer); }
    rescanTimer = setTimeout(function () {
      if (MODE === 'paragraph') { collectParagraphMode(); } else { collectSentenceMode(); }
      flush();
    }, 900);
  });
  try {
    observer.observe(document.body, { childList: true, subtree: true });
  } catch (e) {}
})();
