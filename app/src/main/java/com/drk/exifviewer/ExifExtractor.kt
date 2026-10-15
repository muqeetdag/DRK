package com.drk.exifviewer

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory

/**
 * Two-pass EXIF extractor:
 *
 *  1. androidx.exifinterface — stable typed access to the headline tags
 *     (GPS, device, shooting settings, orientation, thumbnail).
 *  2. drew-noakes metadata-extractor — a full directory/tag walk for the raw
 *     forensic dump (EXIF, GPS, IPTC, XMP, JFIF, …) plus gap-filling for the
 *     fields pass one may have missed.
 *
 * Everything runs locally; nothing leaves the device.
 */
object ExifExtractor {

    fun extract(contentResolver: ContentResolver, uri: Uri): ExifData {
        val base = passOne(contentResolver, uri)
        if (!base.hasExif) return base
        return passTwo(contentResolver, uri, base)
    }

    // ------------------------------------------------ pass 1: androidx ----

    private fun passOne(contentResolver: ContentResolver, uri: Uri): ExifData {
        val notes = mutableListOf<String>()

        val exif: ExifInterface = try {
            contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
                ?: return ExifData(hasExif = false, error = "Could not open the image stream.")
        } catch (e: Exception) {
            return ExifData(
                hasExif = false,
                error = "No readable EXIF container (${e.javaClass.simpleName}: ${e.message})"
            )
        }

        val latLong = exif.latLong
        val hasGps = latLong != null && !(latLong[0] == 0.0 && latLong[1] == 0.0)
        val altitude = if (hasGps) {
            exif.getAltitude(Double.NaN).takeUnless { it.isNaN() }
        } else null
        if (!hasGps) notes.add("GPS block absent or zeroed — many messengers strip it.")

        val fNumber = dblAttr(exif, ExifInterface.TAG_F_NUMBER)?.let { "f/${trimNum(it)}" }
            ?: strAttr(exif, ExifInterface.TAG_F_NUMBER)
        val exposure = dblAttr(exif, ExifInterface.TAG_EXPOSURE_TIME)?.let { sec ->
            if (sec >= 1.0) "${trimNum(sec)} s" else "1/${Math.round(1.0 / sec)} s"
        } ?: strAttr(exif, ExifInterface.TAG_EXPOSURE_TIME)
        val focal = dblAttr(exif, ExifInterface.TAG_FOCAL_LENGTH)?.let { "${trimNum(it)} mm" }
            ?: strAttr(exif, ExifInterface.TAG_FOCAL_LENGTH)

        val orientationText = orientationText(exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))

        val flashText = strAttr(exif, ExifInterface.TAG_FLASH)?.let { v ->
            val fired = (v.toIntOrNull() ?: 0) and 0x1 != 0
            if (fired) "fired (0x$v)" else "did not fire (0x$v)"
        }

        val thumbInfo = try {
            if (exif.hasThumbnail) {
                val bmp = exif.thumbnailBitmap
                if (bmp != null) "present (${bmp.width}×${bmp.height})" else "present (undecodable)"
            } else null
        } catch (e: Exception) { null }

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

        val raw = linkedMapOf<String, String>()
        for (field in ExifInterface::class.java.fields) {
            if (!field.name.startsWith("TAG_") || field.type != String::class.java) continue
            val tag = try { field.get(null) as? String } catch (e: Exception) { null } ?: continue
            try {
                exif.getAttribute(tag)?.let { v -> if (v.isNotBlank()) raw[tag] = v.trim() }
            } catch (e: Exception) { /* ignore */ }
        }
        if (hasGps && latLong != null) {
            raw["Computed · GPSPosition"] = "%.6f, %.6f".format(latLong[0], latLong[1])
        }

        return ExifData(
            hasExif = true,
            hasGps = hasGps,
            gpsLatitude = latLong?.get(0),
            gpsLongitude = latLong?.get(1),
            altitudeMeters = altitude,
            make = strAttr(exif, ExifInterface.TAG_MAKE),
            model = strAttr(exif, ExifInterface.TAG_MODEL),
            software = strAttr(exif, ExifInterface.TAG_SOFTWARE),
            dateTimeOriginal = strAttr(exif, ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: strAttr(exif, ExifInterface.TAG_DATETIME),
            iso = strAttr(exif, ExifInterface.TAG_ISO_SPEED_RATINGS),
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

    // --------------------------------------- pass 2: metadata-extractor ----

    private fun passTwo(contentResolver: ContentResolver, uri: Uri, base: ExifData): ExifData {
        return try {
            contentResolver.openInputStream(uri)?.use { ins ->
                val metadata = ImageMetadataReader.readMetadata(ins)

                val raw = LinkedHashMap(base.raw)
                val errors = mutableListOf<String>()
                for (dir in metadata.directories) {
                    for (tag in dir.tags) {
                        val key = "${dir.name} · ${tag.tagName}"
                        raw.putIfAbsent(key, tag.description ?: tag.toString())
                    }
                    dir.errors?.forEach { errors.add(it) }
                }

                val gps = metadata.getFirstDirectoryOfType(GpsDirectory::class.java)
                val geo = gps?.geoLocation
                val geoOk = geo != null && !geo.isZero

                val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
                val sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)

                base.copy(
                    hasGps = base.hasGps || geoOk,
                    gpsLatitude = base.gpsLatitude ?: geo?.latitude,
                    gpsLongitude = base.gpsLongitude ?: geo?.longitude,
                    altitudeMeters = base.altitudeMeters ?: safeGpsAltitude(gps),
                    make = base.make ?: ifd0?.getString(ExifIFD0Directory.TAG_MAKE),
                    model = base.model ?: ifd0?.getString(ExifIFD0Directory.TAG_MODEL),
                    software = base.software ?: ifd0?.getString(ExifIFD0Directory.TAG_SOFTWARE),
                    dateTimeOriginal = base.dateTimeOriginal
                        ?: sub?.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
                        ?: ifd0?.getString(ExifIFD0Directory.TAG_DATETIME),
                    iso = base.iso ?: sub?.getString(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT),
                    fNumber = base.fNumber ?: sub?.getDescription(ExifSubIFDDirectory.TAG_FNUMBER),
                    exposureTime = base.exposureTime
                        ?: sub?.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME),
                    focalLength = base.focalLength
                        ?: sub?.getDescription(ExifSubIFDDirectory.TAG_FOCAL_LENGTH),
                    orientationText = base.orientationText,
                    raw = raw,
                    error = errors.firstOrNull()
                )
            } ?: base
        } catch (e: Exception) {
            // metadata-extractor can't read this container — framework pass already won
            if (base.hasExif) base
            else base.copy(error = "No EXIF data found (${e.javaClass.simpleName}).")
        }
    }

    private fun safeGpsAltitude(gps: GpsDirectory?): Double? = try {
        gps?.getDouble(GpsDirectory.TAG_ALTITUDE)
    } catch (e: Exception) { null }

    // ------------------------------------------------------- small helpers ----

    private fun strAttr(exif: ExifInterface, tag: String): String? = try {
        exif.getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) { null }

    private fun dblAttr(exif: ExifInterface, tag: String): Double? = try {
        val v = exif.getAttributeDouble(tag, Double.NaN)
        if (v.isNaN()) null else v
    } catch (e: Exception) { null }

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
