package it.sergio.scadenze

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : Activity() {

    private lateinit var web: WebView
    private var pageReady = false

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null
    private var pendingWebCamera: PermissionRequest? = null
    private var pendingNotif: String? = null

    private val scanner by lazy { BarcodeScanning.getClient() }
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // La pagina arriva da un indirizzo https interno all'app: per il telefono è un sito sicuro,
    // così fotocamera e salvataggi funzionano come su Chrome
    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.addJavascriptInterface(Bridge(), "AndroidApp")

        val bridgeJs = assets.open("android.js").bufferedReader().use { it.readText() }
        val earlyScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (earlyScript) WebViewCompat.addDocumentStartJavaScript(web, bridgeJs, setOf(ORIGIN))

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (!request.isForMainFrame || url.host == HOST) return false
                if (url.host == SITE) { shopFragment(url).takeIf { it.isNotEmpty() }?.let { openShopLink(it) }; return true }
                // Link esterni (per esempio una scheda prodotto) si aprono nel browser
                return try { startActivity(Intent(Intent.ACTION_VIEW, url)); true } catch (e: ActivityNotFoundException) { true }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                if (!earlyScript) view.evaluateJavascript(bridgeJs, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                pageReady = true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            // Fotocamera dal vivo per lo scanner dei codici a barre
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE !in request.resources) { request.deny(); return@runOnUiThread }
                    if (hasCamera()) request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                    else {
                        pendingWebCamera?.deny()
                        pendingWebCamera = request
                        requestPermissions(arrayOf(Manifest.permission.CAMERA), PERM_CAMERA_WEB)
                    }
                }
            }

            // Foto del codice o della data, e scelta del file di copia da ripristinare
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val images = params?.acceptTypes?.any { it.startsWith("image") } == true
                return when {
                    images && params?.isCaptureEnabled == true ->
                        if (hasCamera()) launch(cameraIntent())
                        else { requestPermissions(arrayOf(Manifest.permission.CAMERA), PERM_CAMERA_PHOTO); true }
                    images -> launch(pickIntent("image/*"))
                    else -> launch(pickIntent("*/*"))
                }
            }
        }

        Reminders.schedule(this)

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl(HOME + shopFragment(intent?.data))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val url = intent.data ?: return
        val frag = shopFragment(url)
        if (frag.isNotEmpty()) openShopLink(frag)
    }

    /** La lista della spesa arriva alla pagina come #spesa=… e la pagina la importa da sola, come sul sito. */
    private fun openShopLink(frag: String) {
        if (pageReady) web.evaluateJavascript("location.hash = " + JSONObject.quote(frag), null)
        else web.loadUrl(HOME + frag)
    }

    /** Dalla Tavola arriva scadenze://spesa?l=<lista>; dal link del sito https://…/#spesa=<lista>. */
    private fun shopFragment(url: Uri?): String = when {
        url == null -> ""
        url.scheme == "scadenze" -> url.getQueryParameter("l")?.let { "#spesa=" + Uri.encode(it) } ?: ""
        url.host == SITE -> url.encodedFragment?.takeIf { it.startsWith("spesa=") }?.let { "#$it" } ?: ""
        else -> ""
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    private fun hasCamera() = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun launch(intent: Intent): Boolean = try {
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_FILE)
        true
    } catch (e: Exception) {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        false
    }

    private fun pickIntent(type: String): Intent =
        Intent(Intent.ACTION_GET_CONTENT).apply {
            this.type = type
            addCategory(Intent.CATEGORY_OPENABLE)
        }

    private fun cameraIntent(): Intent {
        val dir = File(cacheDir, "foto").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "foto_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        cameraUri = uri
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQ_FILE) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        val cb = fileCallback ?: return
        fileCallback = null
        val picked = data?.data
        val result: Array<Uri>? = when {
            resultCode != RESULT_OK -> null
            picked != null -> arrayOf(picked)
            cameraUri != null -> arrayOf(cameraUri!!)
            else -> null
        }
        cameraUri = null
        cb.onReceiveValue(result)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            PERM_CAMERA_WEB -> {
                val req = pendingWebCamera ?: return
                pendingWebCamera = null
                if (ok) req.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) else req.deny()
            }
            // Senza permesso della fotocamera si sceglie una foto dalla galleria
            PERM_CAMERA_PHOTO -> if (fileCallback != null) launch(if (ok) cameraIntent() else pickIntent("image/*"))
            PERM_NOTIF -> {
                val id = pendingNotif ?: return
                pendingNotif = null
                // Rifiutate per sempre: l'unico modo è attivarle dalle impostazioni
                if (!ok && Build.VERSION.SDK_INT >= 33 &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                ) openNotificationSettings()
                done(id, null, if (Reminders.canNotify(this)) "granted" else "denied")
            }
        }
    }

    private fun openNotificationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        } catch (e: ActivityNotFoundException) {
        }
    }

    private fun done(id: String, err: String?, value: String?) {
        val js = "window.__androidDone && window.__androidDone(" +
            listOf(id, err, value).joinToString(",") { if (it == null) "null" else JSONObject.quote(it) } + ")"
        runOnUiThread { web.evaluateJavascript(js, null) }
    }

    private fun decode(dataUrl: String) =
        Base64.decode(dataUrl.substringAfter(","), Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size) }

    private fun formatName(f: Int) = when (f) {
        Barcode.FORMAT_EAN_13 -> "ean_13"
        Barcode.FORMAT_EAN_8 -> "ean_8"
        Barcode.FORMAT_UPC_A -> "upc_a"
        Barcode.FORMAT_UPC_E -> "upc_e"
        Barcode.FORMAT_CODE_128 -> "code_128"
        Barcode.FORMAT_CODE_39 -> "code_39"
        Barcode.FORMAT_CODE_93 -> "code_93"
        Barcode.FORMAT_CODABAR -> "codabar"
        Barcode.FORMAT_ITF -> "itf"
        Barcode.FORMAT_QR_CODE -> "qr_code"
        Barcode.FORMAT_DATA_MATRIX -> "data_matrix"
        Barcode.FORMAT_PDF417 -> "pdf417"
        Barcode.FORMAT_AZTEC -> "aztec"
        else -> "unknown"
    }

    inner class Bridge {
        @JavascriptInterface
        fun scanBarcode(dataUrl: String, id: String) {
            val bmp = try { decode(dataUrl) } catch (e: Exception) { null }
            if (bmp == null) { done(id, "Immagine non valida", null); return }
            scanner.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { list ->
                    val out = JSONArray()
                    for (b in list) {
                        val v = b.rawValue ?: continue
                        val r = b.boundingBox
                        val box = JSONArray()
                        if (r != null) { box.put(r.left); box.put(r.top); box.put(r.right); box.put(r.bottom) }
                        else repeat(4) { box.put(0) }
                        out.put(JSONObject().put("v", v).put("f", formatName(b.format)).put("box", box))
                    }
                    done(id, null, out.toString())
                }
                .addOnFailureListener { e -> done(id, e.message ?: "Errore di lettura", null) }
        }

        @JavascriptInterface
        fun readText(dataUrl: String, id: String) {
            val bmp = try { decode(dataUrl) } catch (e: Exception) { null }
            if (bmp == null) { done(id, "Immagine non valida", null); return }
            recognizer.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { t -> done(id, null, t.text) }
                .addOnFailureListener { e -> done(id, e.message ?: "Errore di lettura", null) }
        }

        /** "default" finché non sono attive: così il pulsante della pagina le richiede sempre. */
        @JavascriptInterface
        fun notifPermission(): String = if (Reminders.canNotify(this@MainActivity)) "granted" else "default"

        @JavascriptInterface
        fun askNotifications(id: String) {
            runOnUiThread {
                when {
                    Reminders.canNotify(this@MainActivity) -> done(id, null, "granted")
                    Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> {
                        pendingNotif?.let { done(it, null, "denied") }
                        pendingNotif = id
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERM_NOTIF)
                    }
                    else -> { openNotificationSettings(); done(id, null, "denied") }
                }
            }
        }

        /** Ad app aperta la pagina mostra già il suo avviso: quelle del telefono arrivano col controllo del mattino. */
        @JavascriptInterface
        fun notify(title: String, body: String) {
        }

        @JavascriptInterface
        fun saveData(json: String) = Reminders.saveData(this@MainActivity, json)

        @JavascriptInterface
        fun shareText(text: String, title: String) {
            runOnUiThread {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(send, title.ifBlank { "Condividi" }))
            }
        }

        /** La copia dei dati: si salva su Drive, in File o si manda a sé stessi. */
        @JavascriptInterface
        fun shareFile(name: String, mime: String, base64: String, title: String) {
            val dir = File(cacheDir, "copie").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            file.writeBytes(Base64.decode(base64, Base64.DEFAULT))
            val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
            runOnUiThread {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, title.ifBlank { "Salva la copia" }))
            }
        }
    }

    companion object {
        private const val HOST = "appassets.androidplatform.net"
        private const val ORIGIN = "https://$HOST"
        private const val HOME = "$ORIGIN/assets/index.html"
        private const val SITE = "dynamic-lollipop-3b2529.netlify.app"
        private const val REQ_FILE = 42
        private const val PERM_CAMERA_WEB = 1
        private const val PERM_CAMERA_PHOTO = 2
        private const val PERM_NOTIF = 3
    }
}
