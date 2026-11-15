// Isolated content script. Only changes CSS on script.google.com.
(() => {
  if (document.documentElement.dataset.astZoomInstalled) return;
  document.documentElement.dataset.astZoomInstalled = "1";
  let style = null;
  function applyZoom(factor) {
    const scale = Math.max(0.65, Math.min(1.8, Number(factor) || 1));
    const font = Math.round(14 * scale * 10) / 10;
    document.documentElement.setAttribute("data-ast-code-zoom", String(scale));
    if (!style) {
      style = document.createElement("style");
      style.id = "ast-local-code-zoom";
      (document.head || document.documentElement).appendChild(style);
    }
    // Monaco's model/source is never touched. Adjust glyphs + gutter only.
    // When Monaco is exposed by the page, main.js uses updateOptions to let
    // Monaco recalculate line height / cursor coordinates as well.
    style.textContent = [
      ".monaco-editor .view-line span { font-size: " + font + "px !important; }",
      ".monaco-editor .margin-view-overlays .line-numbers { font-size: " + font + "px !important; }",
      ".CodeMirror-code, .CodeMirror-code span, .CodeMirror-linenumber, .cm-content, .cm-gutterElement { font-size: " + font + "px !important; }"
    ].join("\n");
  }
  const port = browser.runtime.connect({name:"ast-code-frame"});
  port.onMessage.addListener(msg => {
    if (msg && msg.type === "zoom") applyZoom(msg.factor);
  });
})();
