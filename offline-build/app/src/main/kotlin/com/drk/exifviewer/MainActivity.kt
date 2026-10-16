package com.drk.exifviewer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()

    private lateinit var scrollView: ScrollView
    private lateinit var imagePreview: ImageView
    private lateinit var resultCard: View
    private lateinit var statusText: TextView
    private lateinit var locationText: TextView
    private lateinit var mapTitle: TextView
    private lateinit var mapContainer: View
    private lateinit var mapOfflineText: TextView
    private lateinit var mapsLinkText: TextView
    private lateinit var deviceText: TextView
    private lateinit var timeText: TextView
    private lateinit var settingsText: TextView
    private lateinit var fileText: TextView
    private lateinit var mapButton: Button
    private lateinit var copyMapLinkButton: Button
    private lateinit var rawToggle: Button
    private lateinit var rawText: TextView

    /** Created lazily in code — a broken System WebView must never crash the app. */
    private var mapWebView: WebView? = null

    private var current: ExifData? = null
    private var currentMapsUrl: String? = null
    private var rawVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scrollView = findViewById(R.id.scrollView)
        imagePreview = findViewById(R.id.imagePreview)
        resultCard = findViewById(R.id.resultCard)
        statusText = findViewById(R.id.statusText)
        locationText = findViewById(R.id.locationText)
        mapTitle = findViewById(R.id.mapTitle)
        mapContainer = findViewById(R.id.mapContainer)
        mapOfflineText = findViewById(R.id.mapOfflineText)
        mapsLinkText = findViewById(R.id.mapsLinkText)
        deviceText = findViewById(R.id.deviceText)
        timeText = findViewById(R.id.timeText)
        settingsText = findViewById(R.id.settingsText)
        fileText = findViewById(R.id.fileText)
        mapButton = findViewById(R.id.mapButton)
        copyMapLinkButton = findViewById(R.id.copyMapLinkButton)
        rawToggle = findViewById(R.id.rawToggle)
        rawText = findViewById(R.id.rawText)

        findViewById<Button>(R.id.uploadButton).setOnClickListener { pickImage() }

        setupEmbeddedMap()

        // Direct Google Maps: exact pin in the Maps app, with fallbacks
        mapButton.setOnClickListener {
            current?.let { d ->
                if (d.hasGps && d.gpsLatitude != null && d.gpsLongitude != null) {
                    LocationHelper.openInGoogleMaps(this, d.gpsLatitude, d.gpsLongitude,
                        d.model ?: "photo location")
                }
            }
        }

        copyMapLinkButton.setOnClickListener {
            currentMapsUrl?.let { url ->
                LocationHelper.copyToClipboard(this, "Google Maps location", url)
                Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show()
            }
        }

        rawToggle.setOnClickListener {
            rawVisible = !rawVisible
            rawText.visibility = if (rawVisible) View.VISIBLE else View.GONE
            rawToggle.text = getString(if (rawVisible) R.string.hide_raw else R.string.show_raw)
            if (rawVisible) rawText.text = buildRawDump(current)
        }

        // "Share to DRK EXIF Viewer" from a gallery app
        if (intent?.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) handleImage(uri)
        }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent.createChooser(intent, getString(R.string.pick_image)), REQ_PICK)
        } catch (e: Exception) {
            Toast.makeText(this, "No file picker available", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Platform pick flow; SAF contract needs no permission")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK && resultCode == RESULT_OK) {
            data?.data?.let { handleImage(it) }
        }
    }

    private fun handleImage(uri: Uri) {
        loadPreview(uri)
        resultCard.visibility = View.VISIBLE
        statusText.text = getString(R.string.parsing)
        mapButton.visibility = View.GONE
        copyMapLinkButton.visibility = View.GONE
        mapsLinkText.visibility = View.GONE
        resetMap()
        rawText.visibility = View.GONE
        rawVisible = false
        rawToggle.text = getString(R.string.show_raw)

        io.execute {
            // A malformed/edge-case file must never kill the app — report instead
            val data = try {
                ExifExtractor.extract(contentResolver, uri)
            } catch (t: Throwable) {
                ExifData(error = "Parse failed: ${t.javaClass.simpleName} — ${t.message}")
            }
            runOnUiThread {
                try {
                    render(data)
                } catch (t: Throwable) {
                    statusText.text = "⚠ Render error: ${t.message}"
                }
            }
        }
    }

    /** Down-sampled decode so a 24 MP JPEG doesn't blow the heap. */
    private fun loadPreview(uri: Uri) {
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 900 || bounds.outHeight / sample > 900) sample *= 2
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd2 ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bmp = BitmapFactory.decodeFileDescriptor(pfd2.fileDescriptor, null, opts)
                    imagePreview.setImageBitmap(bmp)
                    imagePreview.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            imagePreview.visibility = View.GONE
        }
    }

    private fun render(d: ExifData) {
        current = d
        scrollToResults()   // bring the map + location straight into view
        if (!d.hasExif) {
            statusText.text = getString(R.string.no_exif)
            locationText.text = getString(R.string.fmt_location, "—")
            deviceText.text = getString(R.string.fmt_device, "—")
            timeText.text = getString(R.string.fmt_taken, "—")
            settingsText.text = getString(R.string.fmt_camera, "—")
            fileText.text = ""
            if (!d.error.isNullOrBlank()) fileText.text = "⚠ ${d.error}"
            rawToggle.isEnabled = false
            return
        }

        statusText.text = getString(R.string.exif_found, d.raw.size)

        // location: coordinates + DIRECT Google Maps link
        if (d.hasGps && d.gpsLatitude != null && d.gpsLongitude != null) {
            val sb = StringBuilder()
            sb.append("%.6f, %.6f".format(d.gpsLatitude, d.gpsLongitude))
            sb.append("\n")
            sb.append(LocationHelper.formatCoordinate(d.gpsLatitude, true))
            sb.append("  ")
            sb.append(LocationHelper.formatCoordinate(d.gpsLongitude, false))
            d.altitudeMeters?.let { sb.append("  ·  alt %.0f m".format(it)) }
            locationText.text = getString(R.string.fmt_location, sb.toString())

            val url = LocationHelper.googleMapsUrl(d.gpsLatitude, d.gpsLongitude)
            currentMapsUrl = url
            mapsLinkText.text = url
            mapsLinkText.visibility = View.VISIBLE
            mapButton.visibility = View.VISIBLE
            copyMapLinkButton.visibility = View.VISIBLE

            // BUILT-IN map: the pin on Google Maps shows up directly on upload
            showMap(d.gpsLatitude, d.gpsLongitude)
        } else {
            locationText.text = getString(R.string.fmt_location, getString(R.string.no_location))
            currentMapsUrl = null
        }

        // device
        val device = listOfNotNull(d.make, d.model)
            .joinToString(" ")
            .takeIf { it.isNotBlank() } ?: getString(R.string.unknown)
        val deviceFull = if (d.software != null) "$device  ·  sw: ${d.software}" else device
        deviceText.text = getString(R.string.fmt_device, deviceFull)

        // time
        timeText.text = getString(R.string.fmt_taken, d.dateTimeOriginal ?: getString(R.string.unknown))

        // camera settings
        val settings = listOfNotNull(
            d.iso?.let { "ISO $it" }, d.fNumber, d.exposureTime, d.focalLength
        ).joinToString("   ")
        val extras = listOfNotNull(d.orientationText, d.flashText, d.embeddedThumbnailInfo?.let { "thumb: $it" })
            .joinToString("  ·  ")
        settingsText.text = getString(R.string.fmt_camera,
            if (settings.isBlank()) getString(R.string.unknown) else
                if (extras.isBlank()) settings else "$settings\n$extras")

        // file facts
        fileText.text = buildString {
            if (d.imageWidth > 0) append("🖼 ${d.imageWidth}×${d.imageHeight}px")
            if (d.fileSizeBytes > 0) {
                if (isNotEmpty()) append("   ")
                append("💾 ${"%.1f".format(d.fileSizeBytes / 1048576.0)} MB")
            }
            for (n in d.notes) append("\nℹ $n")
        }

        rawToggle.isEnabled = d.raw.isNotEmpty()
        if (rawVisible) rawText.text = buildRawDump(d)
    }

    /** In-app Google Maps panel: the pin loads the instant a GPS photo is picked.
     *  The WebView is constructed in code; any failure (broken/missing System
     *  WebView provider, ROM quirks) disables the map instead of crashing. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupEmbeddedMap() {
        try {
            val wv = WebView(this)
            val s = wv.settings
            s.javaScriptEnabled = true          // Google Maps is JS-driven
            s.domStorageEnabled = true
            s.loadWithOverviewMode = true
            s.useWideViewPort = true
            s.builtInZoomControls = true
            s.displayZoomControls = false
            wv.webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?, request: WebResourceRequest?, error: WebResourceError?
                ) {
                    // No connectivity (or blocked): fall back to coordinates + links
                    if (request?.isForMainFrame == true) showMapFallback()
                }
            }
            // Let the user pan/zoom the map even though it sits inside the ScrollView
            wv.setOnTouchListener { v, _ ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
            (mapContainer as ViewGroup).addView(
                wv,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            mapWebView = wv
        } catch (t: Throwable) {
            android.util.Log.w("DRK", "embedded map disabled: ${t.message}")
            mapWebView = null
        }
    }

    private fun showMapFallback() {
        mapTitle.visibility = View.GONE
        mapContainer.visibility = View.GONE
        mapOfflineText.visibility = View.VISIBLE
    }

    private fun resetMap() {
        try {
            mapWebView?.stopLoading()
            mapWebView?.loadUrl("about:blank")
        } catch (t: Throwable) { /* WebView already unhealthy — ignore */ }
        mapTitle.visibility = View.GONE
        mapContainer.visibility = View.GONE
        mapOfflineText.visibility = View.GONE
    }

    private fun showMap(lat: Double, lon: Double) {
        val wv = mapWebView
        if (wv == null) { showMapFallback(); return }
        mapOfflineText.visibility = View.GONE
        mapTitle.visibility = View.VISIBLE
        mapContainer.visibility = View.VISIBLE
        try {
            wv.loadUrl(LocationHelper.googleMapsEmbedUrl(lat, lon))
        } catch (t: Throwable) {
            showMapFallback()
        }
    }

    /** Bring the evidence card (map + location) on-screen right after parsing. */
    private fun scrollToResults() {
        scrollView.post {
            val child = scrollView.getChildAt(0) ?: return@post
            val y = (resultCard.top + child.top
                - (12 * resources.displayMetrics.density).toInt()).coerceAtLeast(0)
            scrollView.smoothScrollTo(0, y)
        }
    }

    override fun onDestroy() {
        try {
            mapWebView?.let { wv ->
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            }
        } catch (t: Throwable) { /* shutting down anyway */ }
        mapWebView = null
        io.shutdown()
        super.onDestroy()
    }

    private fun buildRawDump(d: ExifData?): String {
        if (d == null) return ""
        if (d.raw.isEmpty()) return "(no EXIF tags present)"
        val pad = d.raw.keys.maxOf { it.length }.coerceAtMost(34)
        return d.raw.entries.joinToString("\n") { (k, v) ->
            k.padEnd(pad) + "  " + v
        }
    }

    companion object {
        private const val REQ_PICK = 4101
    }
}
