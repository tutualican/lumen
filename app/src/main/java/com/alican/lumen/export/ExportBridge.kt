package com.alican.lumen.export

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.alican.lumen.model.FilterParams
import java.util.concurrent.atomic.AtomicBoolean

/** Thin façade so the UI never touches codec/GL details. */
object ExportBridge {

    suspend fun photo(
        context: Context,
        uri: Uri,
        params: FilterParams,
        stamp: Bitmap?,
        format: PhotoOutputFormat,
        onProgress: (Float) -> Unit,
    ): PhotoExportResult = PhotoExporter(context).export(uri, params, format, stamp, onProgress)

    suspend fun video(
        context: Context,
        uri: Uri,
        params: FilterParams,
        stamp: Bitmap?,
        onProgress: (Float) -> Unit,
    ): Uri = VideoExporter(context, AtomicBoolean(false))
        .export(uri, params, stamp, onProgress)
}
