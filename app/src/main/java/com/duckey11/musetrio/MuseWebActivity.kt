package com.duckey11.musetrio

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast

/**
 * Satu WebView yang membuka https://muse.ai.
 *
 * Activity ini jalan di prosesnya sendiri (lihat manifest:
 * :muse_p1 / :muse_p2 / :muse_p3). WAJIB memanggil
 * WebView.setDataDirectorySuffix() SEBELUM WebView pertama dibuat
 * di proses ini, supaya tiap profil punya cookie/storage sendiri
 * dan bisa login akun Meta yang berbeda.
 */
open class MuseWebActivity : Activity() {

    companion object {
        private const val MUSE_URL = "https://muse.ai"
        private const val REQ_FILE_CHOOSER = 2001
        private const val REQ_PERMISSIONS = 2002
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Profil dari Intent extra ("p1" / "p2" / "p3").
        // HARUS sebelum WebView pertama dibuat di proses ini.
        val profile = intent.getStringExtra(ProfilePickerActivity.EXTRA_PROFILE)
            ?: ProfilePickerActivity.PROFILE_1
        WebView.setDataDirectorySuffix(profile)

        setContentView(R.layout.activity_web)
        webView = findViewById(R.id.webview)
        progressBar = findViewById(R.id.progress_bar)

        // Login Meta sering lewat domain pihak ketiga, jadi cookie
        // pihak ketiga harus diizinkan supaya login bisa selesai.
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        CookieManager.getInstance().setAcceptCookie(true)

        // Tema gelap (v1.0.1): latar WebView gelap supaya tidak ada
        // kilatan putih saat halaman dimuat.
        webView.setBackgroundColor(Color.parseColor("#121212"))

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
            // Paksa mode gelap WebView (API 29+): halaman melihat
            // prefers-color-scheme: dark, jadi muse.ai merender tema
            // gelap aslinya; konten yang tidak mendukung tema gelap
            // ikut digelapkan secara algoritmik. Di Android 9
            // (API 28) API ini belum ada, WebView tetap terang.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                forceDark = WebSettings.FORCE_DARK_ON
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                // Semua navigasi (termasuk alur login Meta) tetap
                // di dalam WebView, jangan lempar ke browser luar.
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) {
                this@MuseWebActivity.title = title ?: "Muse"
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility =
                    if (newProgress >= 100) android.view.View.GONE
                    else android.view.View.VISIBLE
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                // Batalkan callback lama yang belum selesai.
                this@MuseWebActivity.filePathCallback?.onReceiveValue(null)
                this@MuseWebActivity.filePathCallback = filePathCallback
                return try {
                    startActivityForResult(
                        fileChooserParams.createIntent(),
                        REQ_FILE_CHOOSER
                    )
                    true
                } catch (e: ActivityNotFoundException) {
                    this@MuseWebActivity.filePathCallback = null
                    Toast.makeText(
                        this@MuseWebActivity,
                        "Tidak ada aplikasi pemilih file.",
                        Toast.LENGTH_SHORT
                    ).show()
                    false
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                // Izinkan mic/kamera yang diminta halaman, tapi hanya
                // yang sudah diizinkan user untuk aplikasi ini.
                val granted = request.resources.filter {
                    (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE &&
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED) ||
                        (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE &&
                            checkSelfPermission(Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED)
                }.toTypedArray()
                if (granted.isNotEmpty()) request.grant(granted)
                else request.deny()
            }
        }

        // Minta izin mic/kamera di awal supaya voice dan kamera
        // di muse.ai langsung bisa dipakai tanpa dialog berulang.
        val needed = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), REQ_PERMISSIONS)
        }

        if (savedInstanceState == null) {
            webView.loadUrl(MUSE_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) webView.saveState(outState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FILE_CHOOSER) {
            val callback = filePathCallback
            filePathCallback = null
            if (resultCode == Activity.RESULT_OK && data != null) {
                val uris = ArrayList<Uri>()
                data.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) {
                        uris.add(clip.getItemAt(i).uri)
                    }
                } ?: data.data?.let { uris.add(it) }
                callback?.onReceiveValue(uris.toTypedArray())
            } else {
                callback?.onReceiveValue(null)
            }
        }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            // Kembali ke pemilih profil.
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}

/** Profil 2: sama seperti MuseWebActivity, tapi di proses :muse_p2. */
class MuseWebActivityP2 : MuseWebActivity()

/** Profil 3: sama seperti MuseWebActivity, tapi di proses :muse_p3. */
class MuseWebActivityP3 : MuseWebActivity()
