// Native Android messages are limited to the zoom percentage. No code or tokens leave the page.
let zoom = 1;
const subscribers = new Set();
browser.runtime.onConnect.addListener(port => {
  if (port.name !== "ast-code-frame") return;
  subscribers.add(port);
  port.onDisconnect.addListener(() => subscribers.delete(port));
  try { port.postMessage({type:"zoom", factor:zoom}); } catch (_) {}
});
const nativePort = browser.runtime.connectNative("appscripttouch");
nativePort.onMessage.addListener(msg => {
  if (!msg || msg.type !== "codeZoom") return;
  const next = Number(msg.factor);
  if (!Number.isFinite(next)) return;
  zoom = Math.max(0.65, Math.min(1.8, next));
  for (const port of subscribers) {
    try { port.postMessage({type:"zoom", factor:zoom}); } catch (_) {}
  }
});
nativePort.postMessage({type:"ready"});
