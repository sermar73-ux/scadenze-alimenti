// Ponte tra la pagina web e il telefono. Gira prima di ogni altro script della pagina
// e sostituisce le funzioni che dentro un'app Android non esistono o vanno su internet:
// notifiche, condivisione, lettura di codici a barre e date. index.html resta quello del sito.
(function () {
  var A = window.AndroidApp;
  if (!A || window.top !== window || window.__android) return;
  window.__android = true;

  var seq = 0, wait = {};
  // Il telefono risponde qui alle richieste che richiedono tempo (permessi, lettura immagini)
  window.__androidDone = function (id, err, val) {
    var w = wait[id];
    if (!w) return;
    delete wait[id];
    if (err) w.rej(new Error(err)); else w.res(val);
  };
  function ask(start) {
    return new Promise(function (res, rej) {
      var id = 'r' + (++seq);
      wait[id] = { res: res, rej: rej };
      try { start(id); } catch (e) { delete wait[id]; rej(e); }
    });
  }

  // Ridimensiona video, foto o canvas e li passa al telefono come JPEG
  async function jpeg(src, max) {
    if (src instanceof Blob) src = await createImageBitmap(src);
    var w = src.videoWidth || src.naturalWidth || src.width, h = src.videoHeight || src.naturalHeight || src.height;
    if (!w || !h) throw new Error('Immagine vuota');
    var k = Math.min(1, max / Math.max(w, h));
    var c = document.createElement('canvas');
    c.width = Math.round(w * k); c.height = Math.round(h * k);
    c.getContext('2d').drawImage(src, 0, 0, c.width, c.height);
    return { url: c.toDataURL('image/jpeg', 0.9), k: k };
  }

  // Codici a barre con ML Kit: niente download, funziona anche offline
  function Detector(opts) { this.formats = (opts && opts.formats) || []; }
  Detector.getSupportedFormats = function () {
    return Promise.resolve(['ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128', 'code_39', 'code_93', 'codabar', 'itf', 'qr_code', 'data_matrix', 'pdf417', 'aztec']);
  };
  Detector.prototype.detect = async function (src) {
    var img = await jpeg(src, 1280);
    var out = JSON.parse(await ask(function (id) { A.scanBarcode(img.url, id); }));
    var want = this.formats;
    return out
      .filter(function (b) { return !want.length || want.indexOf(b.f) >= 0; })
      .map(function (b) {
        var r = b.box.map(function (n) { return n / img.k; });
        return { rawValue: b.v, format: b.f, boundingBox: new DOMRect(r[0], r[1], r[2] - r[0], r[3] - r[1]), cornerPoints: [] };
      });
  };
  window.BarcodeDetector = Detector;

  // Lettura della data di scadenza con ML Kit al posto di Tesseract (che andava scaricato)
  window.Tesseract = {
    recognize: async function (src) {
      var img = await jpeg(src, 1600);
      var text = await ask(function (id) { A.readText(img.url, id); });
      return { data: { text: text || '' } };
    }
  };

  // Notifiche vere del telefono
  function Notif(title, opts) { A.notify(String(title || ''), String((opts && opts.body) || '')); }
  Object.defineProperty(Notif, 'permission', { get: function () { return A.notifPermission(); } });
  Notif.requestPermission = function (cb) {
    return ask(function (id) { A.askNotifications(id); }).then(function (p) { if (cb) cb(p); return p; });
  };
  window.Notification = Notif;

  // Condivisione: la lista della spesa come testo, la copia dei dati come file
  function b64(blob) {
    return new Promise(function (res, rej) {
      var r = new FileReader();
      r.onload = function () { res(String(r.result).split(',')[1] || ''); };
      r.onerror = function () { rej(r.error); };
      r.readAsDataURL(blob);
    });
  }
  function define(name, fn) { Object.defineProperty(navigator, name, { value: fn, configurable: true, writable: true }); }
  define('canShare', function (d) { return !!d; });
  define('share', async function (d) {
    d = d || {};
    if (d.files && d.files.length) {
      var f = d.files[0];
      A.shareFile(f.name || 'file', f.type || 'application/octet-stream', await b64(f), d.title || '');
      return;
    }
    A.shareText(String(d.text || d.url || ''), String(d.title || ''));
  });

  // Ogni salvataggio arriva anche al telefono, che controlla le scadenze ogni mattina ad app chiusa
  var KEY = 'casa.v1', setItem = Storage.prototype.setItem;
  Storage.prototype.setItem = function (k, v) {
    setItem.call(this, k, v);
    if (k === KEY && this === window.localStorage) { try { A.saveData(String(v)); } catch (e) {} }
  };
  try { var v = window.localStorage.getItem(KEY); if (v) A.saveData(v); } catch (e) {}
})();
