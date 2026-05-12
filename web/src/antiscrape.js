(function () {
  // ── Right-click guard ──────────────────────────────────────────────────────
  document.addEventListener('contextmenu', function (e) {
    var t = e.target;
    if (!t || !['INPUT', 'TEXTAREA', 'SELECT', 'A'].includes(t.tagName)) {
      e.preventDefault();
    }
  });

  // ── Keyboard shortcut guard ────────────────────────────────────────────────
  // Ctrl/Cmd + U (view source), S (save page), P (print/PDF)
  document.addEventListener('keydown', function (e) {
    var mod = e.ctrlKey || e.metaKey;
    if (mod && (e.key === 'u' || e.key === 's' || e.key === 'p')) {
      e.preventDefault();
    }
  });

  // ── Bot / headless detection ───────────────────────────────────────────────
  function detectBot() {
    // webdriver flag (set by Selenium, Playwright default, CDP)
    if (navigator.webdriver === true) return true;
    // known headless UA strings
    if (/HeadlessChrome|Prerender|PhantomJS|Selenium|SlimerJS/.test(navigator.userAgent)) return true;
    // legacy automation globals
    if (typeof window.__selenium_unwrapped !== 'undefined') return true;
    if (typeof window.callPhantom !== 'undefined') return true;
    if (typeof window._phantom !== 'undefined') return true;
    // missing Chrome runtime (present in real Chrome, absent in headless w/o CDP)
    if (/Chrome/.test(navigator.userAgent) && !window.chrome) return true;
    // plugin count — headless Chrome has 0 by default
    if (navigator.plugins && navigator.plugins.length === 0 && /Chrome/.test(navigator.userAgent)) return true;
    // languages array absent or empty
    if (!navigator.languages || navigator.languages.length === 0) return true;
    return false;
  }

  if (detectBot()) {
    document.documentElement.setAttribute('data-bot', '1');
  }

  // ── Rapid-scroll rate limiter ──────────────────────────────────────────────
  // Bots often scroll at superhuman speed to harvest lazy-loaded content.
  var lastScroll = 0;
  var burst = 0;
  window.addEventListener('scroll', function () {
    var now = Date.now();
    if (now - lastScroll < 50) {
      if (++burst > 20) document.documentElement.setAttribute('data-bot', '1');
    } else {
      burst = Math.max(0, burst - 1);
    }
    lastScroll = now;
  }, { passive: true });

  // ── Honeypot link trap ─────────────────────────────────────────────────────
  // Real users never see or click this. Scrapers follow every href.
  // The server should 403 /tg-hp/ and log the requester.
  var hp = document.createElement('a');
  hp.href = '/tg-hp/trap';
  hp.setAttribute('aria-hidden', 'true');
  hp.setAttribute('tabindex', '-1');
  hp.style.cssText = 'position:absolute;left:-9999px;top:-9999px;width:1px;height:1px;overflow:hidden;';
  hp.addEventListener('click', function (e) {
    e.preventDefault();
    document.documentElement.setAttribute('data-bot', '1');
  });
  document.body.appendChild(hp);
})();
