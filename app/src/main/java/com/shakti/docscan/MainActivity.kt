package com.shakti.docscan

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { ScannerScreen() } } }
    }
}

@Composable
fun ScannerScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pages by remember { mutableStateOf(listOf<Uri>()) }
    var pdf by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(r.data)?.let { res ->
                pages = res.pages?.map { it.imageUri } ?: emptyList()
                pdf = res.pdf?.uri
            }
        }
    }

    fun startScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(30)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(ctx as Activity)
            .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { toast(ctx, "Gagal membuka scanner: ${it.message}") }
    }

    // Jalankan ekspor di background lalu tampilkan hasilnya
    fun export(block: suspend () -> String) {
        scope.launch {
            busy = true
            val msg = withContext(Dispatchers.IO) {
                try { block() } catch (e: Exception) { "Gagal: ${e.message}" }
            }
            busy = false
            toast(ctx, msg)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("DocScan", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = { startScan() }, modifier = Modifier.fillMaxWidth()) {
            Text(if (pages.isEmpty()) "Scan Dokumen" else "Scan Ulang")
        }

        if (pages.isNotEmpty()) {
            Text("${pages.size} halaman", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(pages) { _, uri ->
                    AsyncImage(
                        model = uri, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.height(180.dp).width(130.dp)
                    )
                }
            }
            Text("Ekspor ke:", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(modifier = Modifier.weight(1f), enabled = !busy, onClick = {
                    export {
                        pages.forEachIndexed { i, u ->
                            saveFile(ctx, "scan_${stamp()}_${i + 1}.jpg", "image/jpeg") { out ->
                                ctx.contentResolver.openInputStream(u)?.use { it.copyTo(out) }
                            }
                        }
                        "JPG tersimpan di Pictures/DocScan"
                    }
                }) { Text("JPG") }
                OutlinedButton(modifier = Modifier.weight(1f), enabled = !busy, onClick = {
                    export {
                        pages.forEachIndexed { i, u ->
                            val bmp = ctx.contentResolver.openInputStream(u)
                                ?.use { BitmapFactory.decodeStream(it) } ?: return@forEachIndexed
                            saveFile(ctx, "scan_${stamp()}_${i + 1}.png", "image/png") { out ->
                                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                        }
                        "PNG tersimpan di Pictures/DocScan"
                    }
                }) { Text("PNG") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(modifier = Modifier.weight(1f), enabled = !busy && pdf != null, onClick = {
                    export {
                        saveFile(ctx, "scan_${stamp()}.pdf", "application/pdf") { out ->
                            ctx.contentResolver.openInputStream(pdf!!)?.use { it.copyTo(out) }
                        }
                        "PDF tersimpan di Download/DocScan"
                    }
                }) { Text("PDF") }
                OutlinedButton(modifier = Modifier.weight(1f), enabled = !busy, onClick = {
                    export {
                        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        val sb = StringBuilder()
                        pages.forEachIndexed { i, u ->
                            val t = rec.process(InputImage.fromFilePath(ctx, u)).await()
                            sb.append("--- Halaman ${i + 1} ---\n").append(t.text).append("\n\n")
                        }
                        saveFile(ctx, "scan_${stamp()}.txt", "text/plain") { out ->
                            out.write(sb.toString().toByteArray())
                        }
                        "Teks (OCR) tersimpan di Download/DocScan"
                    }
                }) { Text("TXT (OCR)") }
            }
            Button(modifier = Modifier.fillMaxWidth(), enabled = !busy && pdf != null, onClick = {
                export {
                    val uri = saveFile(ctx, "scan_${stamp()}.pdf", "application/pdf") { out ->
                        ctx.contentResolver.openInputStream(pdf!!)?.use { it.copyTo(out) }
                    }
                    withContext(Dispatchers.Main) {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(send, "Bagikan PDF"))
                    }
                    "Membuka menu bagikan..."
                }
            }) { Text("Bagikan PDF") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Belum ada dokumen. Tekan Scan Dokumen untuk mulai.")
            }
        }
    }
}

private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

private fun toast(ctx: Context, msg: String) =
    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

/** Simpan file ke MediaStore (tanpa izin storage, Android 10+). Gambar -> Pictures, lainnya -> Download. */
private fun saveFile(ctx: Context, name: String, mime: String, write: (OutputStream) -> Unit): Uri? {
    val isImage = mime.startsWith("image/")
    val collection = if (isImage) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    else MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val dir = if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/DocScan")
    }
    val uri = ctx.contentResolver.insert(collection, values) ?: return null
    ctx.contentResolver.openOutputStream(uri)?.use(write)
    return uri
}
