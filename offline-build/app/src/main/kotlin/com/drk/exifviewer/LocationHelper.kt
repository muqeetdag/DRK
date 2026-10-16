package com.drk.exifviewer

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object LocationHelper {

    /** Direct Google Maps URL for the coordinate (open anywhere, copy freely). */
    fun googleMapsUrl(lat: Double, lon: Double): String =
        "https://www.google.com/maps?q=$lat,$lon"

    /**
     * Key-less embeddable Google Maps URL — loaded in the in-app WebView so the
     * photo's pin appears on the map directly when the image is uploaded.
     */
    fun googleMapsEmbedUrl(lat: Double, lon: Double): String =
        "https://maps.google.com/maps?q=$lat,$lon&z=16&hl=en&output=embed"

    /**
     * Opens the coordinate directly in the Google Maps app (exact pin).
     * Falls back to any geo-capable app, then a plain browser link.
     */
    fun openInGoogleMaps(context: Context, lat: Double, lon: Double, label: String? = null) {
        val url = googleMapsUrl(lat, lon)
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setPackage("com.google.android.apps.maps")
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) { /* Maps app not installed */ }

        val query = if (label.isNullOrBlank()) "$lat,$lon" else "$lat,$lon($label)"
        try {
            val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$query&z=16"))
            context.startActivity(Intent.createChooser(geo, "Open location"))
            return
        } catch (e: Exception) { /* no geo handler either */ }

        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(context, "No maps application found", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun formatCoordinate(value: Double, isLatitude: Boolean): String {
        val dir = if (isLatitude) {
            if (value >= 0) "N" else "S"
        } else {
            if (value >= 0) "E" else "W"
        }
        val v = Math.abs(value)
        val deg = v.toInt()
        val minFull = (v - deg) * 60.0
        val min = minFull.toInt()
        val sec = (minFull - min) * 60.0
        return "%d°%02d'%05.2f\"%s".format(deg, min, sec, dir)
    }
}
