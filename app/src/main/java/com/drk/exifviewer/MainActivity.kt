package com.drk.exifviewer

import android.annotation.SuppressLint
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var scrollView: ScrollView
    private lateinit var imagePreview: ImageView
    private lateinit var resultCard: View
    private lateinit var statusText: TextView
    private lateinit var locationText: TextView
    private lateinit var mapTitle: TextView
    private lateinit var mapWebView: WebView
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

    private var current: ExifData? = null
    private var currentMapsUrl: String? = null
    private var rawVisible = false

    /** ACTION_GET_CONTENT via the Storage Access Framework — no permissions needed. */
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handleImage(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scrollView = findViewById(R.id.scrollView)
        imagePreview = findViewById(R.id.imagePreview)
        resultCard = findViewById(R.id.resultCard)
        statusText = findViewById(R.id.statusText)
        locationText = findViewById(R.id.locationText)
        mapTitle = findViewById(R.id.mapTitle)
        mapWebView = findViewById(R.id.mapWebView)
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

        findViewById<Button>(R.id.uploadButton).setOnClickListener {
            pickImage.launch("image/*")
        }

        setupEmbeddedMap()

        // Direct Google Maps: exact pin in the Maps app, with fallbacks
        mapButton.setOnClickListener {
            current?.let { d ->
                if (d.hasGps && d.gpsLatitude != null && d.gpsLongitude != null) {
                    LocationHelper.openInGoogleMaps(
                        this, d.gpsLatitude, d.gpsLongitude,
                        d.model ?: "photo location"
                    )
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

        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                ExifExtractor.extract(contentResolver, uri)
            }
            render(data)
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
            Toast.makeText(this, "Preview unavailable", Toast.LENGTH_SHORT).show()
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
            fileText.text = if (d.error.isNullOrBlank()) "" else "⚠ ${d.error}"
            rawToggle.isEnabled = false
            currentMapsUrl = null
            return
        }

        statusText.text = getString(R.string.exif_found, d.raw.size)

        if (d.hasGps && d.gpsLatitude != null && d.gpsLongitude != null) {
            val sb = StringBuilder()
            sb.append("%.6f, %.6f".format(d.gpsLatitude, d.gpsLongitude))
            sb.append('\n')
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

        val device = listOfNotNull(d.make, d.model)
            .joinToString(" ")
            .takeIf { it.isNotBlank() } ?: getString(R.string.unknown)
        val deviceFull = if (d.software != null) "$device  ·  sw: ${d.software}" else device
        deviceText.text = getString(R.string.fmt_device, deviceFull)

        timeText.text = getString(R.string.fmt_taken, d.dateTimeOriginal ?: getString(R.string.unknown))

        val settings = listOfNotNull(
            d.iso?.let { "ISO $it" }, d.fNumber, d.exposureTime, d.focalLength
        ).joinToString("   ")
        val extras = listOfNotNull(d.orientationText, d.flashText, d.embeddedThumbnailInfo?.let { "thumb: $it" })
            .joinToString("  ·  ")
        settingsText.text = getString(
            R.string.fmt_camera,
            if (settings.isBlank()) getString(R.string.unknown)
            else if (extras.isBlank()) settings else "$settings\n$extras"
        )

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

    /** In-app Google Maps panel: the pin loads the instant a GPS photo is picked. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupEmbeddedMap() {
        val s = mapWebView.settings
        s.javaScriptEnabled = true          // Google Maps is JS-driven
        s.domStorageEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        mapWebView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                // No connectivity (or blocked): fall back to coordinates + links
                if (request?.isForMainFrame == true) {
                    mapTitle.visibility = View.GONE
                    mapWebView.visibility = View.GONE
                    mapOfflineText.visibility = View.VISIBLE
                }
            }
        }
        // Let the user pan/zoom the map even though it sits inside the ScrollView
        mapWebView.setOnTouchListener { v, _ ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }
    }

    private fun resetMap() {
        mapWebView.stopLoading()
        mapWebView.loadUrl("about:blank")
        mapTitle.visibility = View.GONE
        mapWebView.visibility = View.GONE
        mapOfflineText.visibility = View.GONE
    }

    private fun showMap(lat: Double, lon: Double) {
        mapOfflineText.visibility = View.GONE
        mapTitle.visibility = View.VISIBLE
        mapWebView.visibility = View.VISIBLE
        mapWebView.loadUrl(LocationHelper.googleMapsEmbedUrl(lat, lon))
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
        (mapWebView.parent as? ViewGroup)?.removeView(mapWebView)
        mapWebView.destroy()
        super.onDestroy()
    }

    private fun buildRawDump(d: ExifData?): String {
        if (d == null) return ""
        if (d.raw.isEmpty()) return "(no EXIF tags present)"
        val pad = d.raw.keys.maxOf { it.length }.coerceAtMost(40)
        return d.raw.entries.joinToString("\n") { (k, v) ->
            k.padEnd(pad) + "  " + v
        }
    }
}
