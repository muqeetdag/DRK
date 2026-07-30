package com.drk.exifviewer

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri

/**
 * Offline EXIF extractor built on the framework [ExifInterface]
 * (android.media.ExifInterface, InputStream constructor requires API 24+).
 *
 * Reads the image twice at most: once for EXIF, once (bounds-only) for
 * pixel dimensions. Nothing ever leaves the device.
 */
object ExifExtractor {

    fun extract(contentResolver: ContentResolver, uri: Uri): ExifData {
        val notes = mutableListOf<String>()

        // ---- open stream & parse EXIF container ----
        val exif: ExifInterface = try {
            contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
                ?: return ExifData(hasExif = false, error = "Could not open the image stream.")
        } catch (e: Exception) {
            return ExifData(
                hasExif = false,
                error = "No readable EXIF container (${e.javaClass.simpleName}: ${e.message})"
            )
        }

        // ---- typed headline fields ----
        val latLong: DoubleArray? = run {
            val out = FloatArray(2)
            val ok = try { exif.getLatLong(out) } catch (e: Exception) { false }
            if (ok) doubleArrayOf(out[0].toDouble(), out[1].toDouble()) else null
        }
        val hasGps = latLong != null && !(latLong[0] == 0.0 && latLong[1] == 0.0)
        val altitude = if (hasGps) {
            exif.getAltitude(Double.NaN).takeUnless { it.isNaN() }
        } else null
        if (!hasGps) notes.add("GPS block absent or zeroed — many messengers strip it.")

        val make = attr(exif, ExifInterface.TAG_MAKE)
        val model = attr(exif, ExifInterface.TAG_MODEL)
        val software = attr(exif, ExifInterface.TAG_SOFTWARE)
        val dateTime = attr(exif, ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: attr(exif, ExifInterface.TAG_DATETIME)

        val iso = firstNonNull(
            attr(exif, ExifInterface.TAG_ISO_SPEED_RATINGS),
            attr(exif, "PhotographicSensitivity")
        )

        val fNumber = dbl(exif, ExifInterface.TAG_F_NUMBER)?.let { "f/${trimNum(it)}" }
            ?: attr(exif, ExifInterface.TAG_F_NUMBER)

        val exposure = dbl(exif, ExifInterface.TAG_EXPOSURE_TIME)?.let { sec ->
            if (sec >= 1.0) "${trimNum(sec)} s" else "1/${Math.round(1.0 / sec)} s"
        } ?: attr(exif, ExifInterface.TAG_EXPOSURE_TIME)

        val focal = dbl(exif, ExifInterface.TAG_FOCAL_LENGTH)?.let { "${trimNum(it)} mm" }
            ?: attr(exif, ExifInterface.TAG_FOCAL_LENGTH)

        val orientationText = orientationText(exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))

        val flashText = attr(exif, ExifInterface.TAG_FLASH)?.let { v ->
            val fired = (v.toIntOrNull() ?: 0) and 0x1 != 0
            if (fired) "fired (0x$v)" else "did not fire (0x$v)"
        }

        // ---- embedded thumbnail (the preview cameras store inside the file) ----
        val thumbInfo = try {
            if (exif.hasThumbnail()) {
                val bmp = exif.thumbnailBitmap
                if (bmp != null) "present (${bmp.width}×${bmp.height})" else "present (undecodable)"
            } else null
        } catch (e: Exception) { null }

        // ---- pixel size + file size (nice context for an evidence sheet) ----
        var w = 0
        var h = 0
        var size = 0L
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                size = pfd.statSize
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, opts)
                w = opts.outWidth
                h = opts.outHeight
            }
        } catch (e: Exception) { /* non-fatal */ }

        // ---- full raw dump: every android.media.ExifInterface TAG_* that has a value ----
        val raw = linkedMapOf<String, String>()
        for (field in ExifInterface::class.java.fields) {
            if (!field.name.startsWith("TAG_") || field.type != String::class.java) continue
            val tag = try { field.get(null) as? String } catch (e: Exception) { null } ?: continue
            try {
                exif.getAttribute(tag)?.let { v -> if (v.isNotBlank()) raw[tag] = v.trim() }
            } catch (e: Exception) { /* ignore */ }
        }
        if (hasGps) raw["Computed · GPSPosition"] = "%.6f, %.6f".format(latLong!![0], latLong[1])

        // A few well-known tags the framework stores but doesn't always expose cleanly.
        val extra = listOf(
            "LensModel", "ApertureValue", "ShutterSpeedValue", "BrightnessValue",
            "ExposureBiasValue", "MeteringMode", "WhiteBalance", "DigitalZoomRatio",
            "FocalLengthIn35mmFilm", "SceneCaptureType", "ColorSpace",
            "DateTimeDigitized", "OffsetTimeOriginal", "UserComment",
            "ImageUniqueID", "CameraOwnerName", "BodySerialNumber"
        )
        for (tag in extra) {
            try {
                exif.getAttribute(tag)?.let { v -> if (v.isNotBlank()) raw["Extra · $tag"] = v.trim() }
            } catch (e: Exception) { /* ignore */ }
        }

        if (raw.size < 3) notes.add("Very few EXIF tags — file may have been re-exported or stripped.")

        return ExifData(
            hasExif = true,
            hasGps = hasGps,
            gpsLatitude = latLong?.get(0),
            gpsLongitude = latLong?.get(1),
            altitudeMeters = altitude,
            make = make,
            model = model,
            software = software,
            dateTimeOriginal = dateTime,
            iso = iso,
            fNumber = fNumber,
            exposureTime = exposure,
            focalLength = focal,
            orientationText = orientationText,
            flashText = flashText,
            imageWidth = w,
            imageHeight = h,
            fileSizeBytes = size,
            embeddedThumbnailInfo = thumbInfo,
            raw = raw,
            notes = notes
        )
    }

    private fun attr(exif: ExifInterface, tag: String): String? = try {
        exif.getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) { null }

    private fun dbl(exif: ExifInterface, tag: String): Double? = try {
        val v = exif.getAttributeDouble(tag, Double.NaN)
        if (v.isNaN()) null else v
    } catch (e: Exception) { null }

    private fun firstNonNull(vararg v: String?): String? = v.firstOrNull { it != null }

    private fun trimNum(x: Double): String =
        if (x == Math.rint(x)) x.toLong().toString() else "%.2f".format(x).trimEnd('0').trimEnd('.')

    private fun orientationText(o: Int): String = when (o) {
        ExifInterface.ORIENTATION_NORMAL -> "normal (1)"
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "flip horizontal (2)"
        ExifInterface.ORIENTATION_ROTATE_180 -> "rotate 180° (3)"
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> "flip vertical (4)"
        ExifInterface.ORIENTATION_TRANSPOSE -> "transpose (5)"
        ExifInterface.ORIENTATION_ROTATE_90 -> "rotate 90° (6)"
        ExifInterface.ORIENTATION_TRANSVERSE -> "transverse (7)"
        ExifInterface.ORIENTATION_ROTATE_270 -> "rotate 270° (8)"
        else -> "unknown ($o)"
    }
}
