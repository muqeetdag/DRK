package com.drk.exifviewer

/**
 * Extracted EXIF summary + full raw tag dump.
 */
data class ExifData(
    val hasExif: Boolean = false,
    val hasGps: Boolean = false,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val altitudeMeters: Double? = null,
    val make: String? = null,
    val model: String? = null,
    val software: String? = null,
    val dateTimeOriginal: String? = null,
    val iso: String? = null,
    val fNumber: String? = null,
    val exposureTime: String? = null,
    val focalLength: String? = null,
    val orientationText: String? = null,
    val flashText: String? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val fileSizeBytes: Long = 0,
    val embeddedThumbnailInfo: String? = null,
    /** Raw tag dump: display name -> value string. Insertion-ordered. */
    val raw: Map<String, String> = emptyMap(),
    val notes: List<String> = emptyList(),
    val error: String? = null
)
