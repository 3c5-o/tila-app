const CACHE="tila-v1";
const APP=["./","./index.html","./manifest.webmanifest","./assets/icon.svg"];
self.addEventListener("install",e=>{e.waitUntil(caches.open(CACHE).then(c=>c.addAll(APP)));self.skipWaiting()});
self.addEventListener("activate",e=>{e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)))));self.clients.claim()});
self.addEventListener("fetch",e=>{
  if(e.request.method!=="GET")return;
  e.respondWith(caches.match(e.request).then(hit=>hit||fetch(e.request).then(res=>{
    if(new URL(e.request.url).origin===location.origin){const clone=res.clone();caches.open(CACHE).then(c=>c.put(e.request,clone))}
    return res;
  }).catch(()=>caches.match("./index.html"))));
});