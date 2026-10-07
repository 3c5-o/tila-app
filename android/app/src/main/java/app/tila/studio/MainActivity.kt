package app.tila.studio

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingSave: Pair<String, String>? = null

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val result = if (uri != null) arrayOf(uri) else null
        fileCallback?.onReceiveValue(result)
        fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.setBackgroundColor(0xFF07100C.toInt())
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = false
            userAgentString = userAgentString + " TilaAndroid/1.0"
        }

        webView.webViewClient = object : WebViewClient() {}
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.fileCallback?.onReceiveValue(null)
                this@MainActivity.fileCallback = filePathCallback
                imagePicker.launch("image/*")
                return true
            }
        }

        webView.addJavascriptInterface(TilaBridge(), "AndroidBridge")
        webView.loadUrl("https://3c5-o.github.io/tila-app/")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    inner class TilaBridge {
        @JavascriptInterface
        fun saveImage(dataUrl: String, fileName: String) {
            runOnUiThread {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingSave = dataUrl to fileName
                    requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 41)
                    return@runOnUiThread
                }
                saveImageNow(dataUrl, fileName)
            }
        }
    }

    private fun saveImageNow(dataUrl: String, requestedName: String) {
        try {
            val fallbackName = "Tila_" + System.currentTimeMillis() + ".png"
            val cleanName = requestedName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { fallbackName }
            val payload = dataUrl.substringAfter(",", dataUrl)
            val bytes = Base64.decode(payload, Base64.DEFAULT)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, cleanName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Tila")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("MediaStore insert failed")
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IllegalStateException("Output stream failed")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Tila")
                if (!dir.exists()) dir.mkdirs()
                val out = File(dir, cleanName)
                FileOutputStream(out).use { it.write(bytes) }
                MediaScannerConnection.scanFile(this, arrayOf(out.absolutePath), arrayOf("image/png"), null)
            }

            Toast.makeText(this, "تم الحفظ داخل Pictures/Tila", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر حفظ الصورة", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                pendingSave?.let { saveImageNow(it.first, it.second) }
            } else {
                Toast.makeText(this, "صلاحية الحفظ مطلوبة في هذا الإصدار من Android", Toast.LENGTH_LONG).show()
            }
            pendingSave = null
        }
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        webView.removeJavascriptInterface("AndroidBridge")
        webView.destroy()
        super.onDestroy()
    }
}
