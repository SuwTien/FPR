package fr.bdst.fastphotosrenamer.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.bdst.fastphotosrenamer.viewmodel.PhotosViewModel
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import fr.bdst.fastphotosrenamer.ui.components.FolderPickerDialog
import fr.bdst.fastphotosrenamer.ui.components.CreateFolderDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: PhotosViewModel,
    onCameraClick: () -> Unit = {}
) {
    val photos by viewModel.photos.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedPhoto by viewModel.selectedPhoto.collectAsStateWithLifecycle(initialValue = null)
    val currentFolder by viewModel.currentFolder.collectAsStateWithLifecycle()
    val availableFolders by viewModel.availableFolders.collectAsStateWithLifecycle(initialValue = emptyList())
    val shouldOpenFolderDropdown by viewModel.shouldOpenFolderDropdown.collectAsStateWithLifecycle(initialValue = false)
    
    val context = LocalContext.current
    
    var showImages by remember { mutableStateOf(true) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    
    // Effet pour charger les photos une seule fois au démarrage
    LaunchedEffect(Unit) {
        viewModel.loadAvailableFolders(context)
        viewModel.loadPhotosFromFolder(context, viewModel.currentFolder.value)
    }
    
    // Effet pour recharger les photos quand le dossier change
    LaunchedEffect(currentFolder) {
        viewModel.loadPhotosFromFolder(context, currentFolder)
    }
    
    // Effet pour ouvrir automatiquement le menu déroulant après la création d'un dossier
    LaunchedEffect(shouldOpenFolderDropdown) {
        if (shouldOpenFolderDropdown) {
            showFolderPicker = true
            // Réinitialiser l'état après avoir ouvert le menu
            viewModel.resetShouldOpenFolderDropdown()
        }
    }
    
    if (selectedPhoto != null) {
        PhotoDetailScreen(
            photo = selectedPhoto!!,
            onBack = { viewModel.clearSelectedPhoto() },
            onRename = { photo, newName, ctx ->
                viewModel.renamePhoto(ctx, photo, newName)
            },
            onDelete = { photo, ctx ->
                val result = viewModel.deletePhoto(ctx, photo)
                viewModel.clearSelectedPhoto()
                result
            },
            viewModel = viewModel
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        // Box avec padding horizontal uniforme
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 0.dp, end = 16.dp)  // 0 à gauche, x3 à droite
                        ) {
                            ElevatedButton(
                                onClick = { showFolderPicker = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center, // Centrer le contenu
                                    modifier = Modifier.fillMaxWidth() // Sans padding asymétrique
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Paramètres dossier",
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    
                                    Text(
                                        text = currentFolder.split("/").lastOrNull() ?: "DCIM",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                    
                                    // Supprimer le Spacer qui causait le déséquilibre
                                }
                            }
                        }
                    }
                )
            },
            bottomBar = {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    content = { 
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            FloatingActionButton(
                                onClick = onCameraClick,
                                containerColor = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Camera,
                                    contentDescription = "Prendre une photo",
                                )
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            PhotoGridScreen(
                photos = photos,
                onPhotoClick = { viewModel.selectPhoto(it) },
                showImages = showImages,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
    
    // Dialogue de sélection de dossier
    if (showFolderPicker) {
        FolderPickerDialog(
            currentFolder = currentFolder,
            availableFolders = availableFolders,
            onFolderSelected = { folder ->
                viewModel.setCurrentFolder(folder)
                showFolderPicker = false
            },
            onMovePhotos = { sourceFolder, destFolder ->
                // Appelle la fonction renommée
                viewModel.movePhotosFromCamera(context, sourceFolder, destFolder)  
                showFolderPicker = false
            },
            onCreateFolder = {
                showFolderPicker = false
                showCreateFolderDialog = true
            },
            onDismiss = { showFolderPicker = false }
        )
    }               
    
    // Dialogue de création de dossier
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { folderName ->
                viewModel.createNewFolder(context, folderName)
                showCreateFolderDialog = false
            }
        )
    }
}