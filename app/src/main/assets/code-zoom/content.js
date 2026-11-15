// Uniform page zoom on the original website (file tree + code + menus).
// Does not read source text, invoke Apps Script APIs or intercept edit shortcuts.
(() => {
  if (window.top !== window) return;
  if (document.documentElement.dataset.astPageZoomInstalled === "true") return;
  document.documentElement.dataset.astPageZoomInstalled = "true";
  let zoom = 1;
  let style;
  function setZoom(value) {
    const next = Number(value);
    if (!Number.isFinite(next)) return;
    zoom = Math.max(0.5, Math.min(2.25, next));
    // CSS zoom is a layout-aware property in Gecko and applies to every
    // descendant of the page, including file list and Monaco editor.
    if (!style || !style.isConnected) {
      style = document.createElement("style");
      style.id = "ast-whole-page-zoom";
      (document.head || document.documentElement).appendChild(style);
    }
    style.textContent = "html { zoom: " + zoom + " !important; }";
  }
  function ensureStyle() {
    if (!style || !style.isConnected) setZoom(zoom);
  }
  const port = browser.runtime.connect({name:"ast-page-frame"});
  port.onMessage.addListener(msg => {
    if (msg && msg.type === "pageZoom") setZoom(msg.factor);
  });
  document.addEventListener("DOMContentLoaded", ensureStyle, {once:true});
  // SPA rerenders should not lose the zoom setting.
  const watcher = new MutationObserver(ensureStyle);
  watcher.observe(document.documentElement, {childList:true});
})();
