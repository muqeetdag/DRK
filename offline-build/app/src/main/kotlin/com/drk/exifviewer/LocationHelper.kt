package com.drk.exifviewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object LocationHelper {

    /**
     * Opens the given coordinate in Google Maps if installed, otherwise falls
     * back to any app that can handle a geo: URI (or shows a toast).
     */
    fun openInMaps(context: Context, lat: Double, lon: Double, label: String? = null) {
        val query = if (label.isNullOrBlank()) "$lat,$lon" else "$lat,$lon($label)"
        val geoUri = Uri.parse("geo:$lat,$lon?q=$query&z=16")
        val intent = Intent(Intent.ACTION_VIEW, geoUri)

        try {
            intent.setPackage("com.google.android.apps.maps")
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            intent.setPackage(null)
        }

        try {
            context.startActivity(Intent.createChooser(intent, "Open location"))
        } catch (e: Exception) {
            Toast.makeText(context, "No maps application found", Toast.LENGTH_SHORT).show()
        }
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
