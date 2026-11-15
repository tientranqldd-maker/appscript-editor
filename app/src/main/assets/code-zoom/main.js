// Main-world helper, limited to Monaco editor typography on script.google.com.
// No network, no authentication or source-code access; do not modify models.
(() => {
  if (window.__astEditorFontZoom) return;
  window.__astEditorFontZoom = true;
  const defaults = new WeakMap();
  function update() {
    const factor = Number(document.documentElement.getAttribute("data-ast-code-zoom")) || 1;
    const api = window.monaco && window.monaco.editor;
    if (!api || typeof api.getEditors !== "function") return;
    try {
      for (const editor of api.getEditors()) {
        if (!defaults.has(editor)) {
          const options = typeof editor.getRawOptions === "function" ? editor.getRawOptions() : {};
          defaults.set(editor, {
            font: Number(options.fontSize) || 14,
            line: Number(options.lineHeight) || 0
          });
        }
        const base = defaults.get(editor);
        editor.updateOptions({
          fontSize: Math.max(9, Math.round(base.font * factor)),
          lineHeight: base.line ? Math.round(base.line * factor) : 0
        });
      }
    } catch (_) {}
  }
  new MutationObserver(mutations => {
    if (mutations.some(m => m.attributeName === "data-ast-code-zoom")) update();
  }).observe(document.documentElement, {attributes:true,attributeFilter:["data-ast-code-zoom"]});
  update();
})();
