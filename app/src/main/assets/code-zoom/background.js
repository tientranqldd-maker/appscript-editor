// Native-only page zoom. Does not inspect/edit code, cookies, or credentials.
let scale = 1;
const frames = new Set();
browser.runtime.onConnect.addListener(port => {
  if (port.name !== "ast-page-frame") return;
  frames.add(port);
  port.onDisconnect.addListener(() => frames.delete(port));
  try { port.postMessage({type:"pageZoom", factor:scale}); } catch (_) {}
});
const android = browser.runtime.connectNative("appscripttouch");
android.onMessage.addListener(msg => {
  if (!msg || msg.type !== "pageZoom") return;
  const factor = Number(msg.factor);
  if (!Number.isFinite(factor)) return;
  scale = Math.max(0.5, Math.min(2.25, factor));
  for (const port of frames) {
    try { port.postMessage({type:"pageZoom", factor:scale}); } catch (_) {}
  }
});
android.postMessage({type:"ready"});
