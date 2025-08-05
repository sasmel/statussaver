package com.sasmel.statussaver

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import coil.compose.rememberAsyncImagePainter
import com.sasmel.statussaver.ui.theme.StatusSaverTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private lateinit var openFolderLauncher: ActivityResultLauncher<Intent>
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("status_saver_prefs", Context.MODE_PRIVATE)

        openFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    sharedPreferences.edit().putString("custom_folder_uri", uri.toString()).apply()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setContent {
            StatusSaverTheme {
                val context = LocalContext.current
                var showDialog by remember { mutableStateOf(false) }

                val hasPermission = hasStoragePermission()
                val customUri = sharedPreferences.getString("custom_folder_uri", null)
                showDialog = !hasPermission && customUri == null

                if (showDialog) {
                    PermissionChoiceDialog(
                        onDismiss = { showDialog = false },
                        onPermissionChosen = {
                            requestStoragePermission()
                        },
                        onSelectFolder = {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                            }
                            openFolderLauncher.launch(intent)
                        }
                    )
                }

                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text("Status Saver") })
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(8.dp)
                    ) {
                        val mediaFiles = remember(hasPermission, customUri) {
                            when {
                                customUri != null -> getFilesFromCustomFolder(Uri.parse(customUri))
                                hasPermission -> getWhatsappStatuses()
                                else -> emptyList()
                            }
                        }

                        if (mediaFiles.isEmpty()) {
                            Text("Nenhum status encontrado", modifier = Modifier.padding(16.dp))
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(mediaFiles) { file ->
                                    StatusThumbnail(uri = file) {
                                        openWithGallery(context, file)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
        }
    }

    private fun getWhatsappStatuses(): List<Uri> {
        val statusDir = File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.Statuses")
        if (!statusDir.exists()) return emptyList()
        return statusDir.listFiles()?.filter {
            it.isFile && (
                    it.name.endsWith(".jpg", true) ||
                            it.name.endsWith(".png", true) ||
                            it.name.endsWith(".mp4", true))
        }?.map { FileProvider.getUriForFile(this, "$packageName.fileprovider", it) } ?: emptyList()
    }

    private fun getFilesFromCustomFolder(uri: Uri): List<Uri> {
        val pickedDir = DocumentFile.fromTreeUri(this, uri) ?: return emptyList()
        return pickedDir.listFiles().filter {
            it.isFile && (
                    it.name?.endsWith(".jpg", true) == true ||
                            it.name?.endsWith(".png", true) == true ||
                            it.name?.endsWith(".mp4", true) == true
                    )
        }.map { it.uri }
    }

    private fun openWithGallery(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, if (uri.toString().endsWith(".mp4")) "video/*" else "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(Intent.createChooser(intent, "Abrir com"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Nenhum app encontrado para abrir o arquivo", Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    fun StatusThumbnail(uri: Uri, onClick: () -> Unit) {
        val isVideo = uri.toString().endsWith(".mp4")

        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clickable { onClick() }
        ) {
            if (isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text("Vídeo", color = Color.White)
                }
            } else {
                Image(
                    painter = rememberAsyncImagePainter(model = uri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }

    @Composable
    fun PermissionChoiceDialog(
        onDismiss: () -> Unit,
        onPermissionChosen: () -> Unit,
        onSelectFolder: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Escolha de Acesso") },
            text = {
                Text("Deseja conceder acesso ao armazenamento ou selecionar uma pasta manualmente?")
            },
            confirmButton = {
                Button(onClick = {
                    onPermissionChosen()
                }) {
                    Text("Permitir acesso")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    onSelectFolder()
                }) {
                    Text("Selecionar pasta")
                }
            }
        )
    }
}
