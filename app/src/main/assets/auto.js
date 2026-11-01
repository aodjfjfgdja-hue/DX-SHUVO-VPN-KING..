(function () {
  if (window.__dxa) return;
  window.__dxa = 1;
  var RE = /^(join|join call|join now|join with audio|join audio|answer|accept)$/i;
  var last = 0;
  function label(el) {
    var t = el.getAttribute('aria-label') || el.innerText || '';
    return t.trim();
  }
  function scan() {
    if (Date.now() - last < 4000) return;
    var els = document.querySelectorAll('[role="button"],button,a[role="link"]');
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      var t = label(el);
      if (t && t.length < 20 && RE.test(t) && el.offsetParent !== null) {
        last = Date.now();
        try { el.click(); } catch (e) {}
        return;
      }
    }
  }
  setInterval(scan, 1500);
})();
