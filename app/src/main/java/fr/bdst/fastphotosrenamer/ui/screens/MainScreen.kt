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
    
    // Nouveaux états pour le mode plein écran
    val fullscreenMode by viewModel.fullscreenMode.collectAsStateWithLifecycle(initialValue = false)
    val fullscreenPhotoIndex by viewModel.fullscreenPhotoIndex.collectAsStateWithLifecycle(initialValue = 0)
    
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
    
    // Mode plein écran prioritaire sur tout le reste
    if (fullscreenMode && photos.isNotEmpty()) {
        PhotoFullscreenScreen(
            photos = photos,
            initialPhotoIndex = fullscreenPhotoIndex,
            onBack = { 
                // Correction du retour : désactiver le mode plein écran sans sélectionner de photo
                viewModel.setFullscreenMode(false) 
                // Garantir qu'aucune photo n'est sélectionnée pour revenir à la grille
                viewModel.clearSelectedPhoto()
            },
            onRename = { photo, ctx ->
                // Sauvegarder l'état du mode plein écran et l'index actuel
                val currentIndex = viewModel.fullscreenPhotoIndex.value
                // Indiquer qu'on vient du mode plein écran pour pouvoir y revenir
                viewModel.setWasInFullscreenMode(true)
                // Temporairement désactiver le mode plein écran pour renommer
                viewModel.setFullscreenMode(false)
                viewModel.selectPhoto(photo)
            },
            onLaunchCamera = onCameraClick,
            viewModel = viewModel
        )
    }
    // Mode détail d'une photo sélectionnée
    else if (selectedPhoto != null) {
        // Utiliser la nouvelle approche par index au lieu de la référence directe
        val photoIndex = viewModel.getPhotoIndex(selectedPhoto!!)
        
        // Vérifier que l'index est valide avant de continuer
        if (photoIndex != -1 && photos.isNotEmpty()) {
            PhotoDetailScreen(
                // Passage de la liste complète des photos et l'index
                photos = photos,
                photoIndex = photoIndex,
                onBack = { 
                    // Si on vient du mode plein écran, y retourner après renommage
                    if (viewModel.wasInFullscreenMode) {
                        viewModel.clearSelectedPhoto()
                        viewModel.setFullscreenMode(true)
                        viewModel.resetWasInFullscreenMode()
                    } else {
                        // Comportement normal : retour à la grille
                        viewModel.clearSelectedPhoto()
                    }
                },
                onRename = { photo, newName, ctx ->
                    val result = viewModel.renamePhoto(ctx, photo, newName)
                    // Si on vient du mode plein écran, retourner en mode plein écran après le renommage
                    if (viewModel.wasInFullscreenMode) {
                        viewModel.clearSelectedPhoto()
                        viewModel.setFullscreenMode(true)
                        viewModel.resetWasInFullscreenMode()
                    }
                    result
                },
                onDelete = { photo, ctx ->
                    val result = viewModel.deletePhoto(ctx, photo)
                    viewModel.clearSelectedPhoto()
                    result
                },
                viewModel = viewModel,
                startInRenamingMode = true // Activer le mode renommage direct
            )
        } else {
            // Si l'index est invalide, revenir à la grille
            viewModel.clearSelectedPhoto()
        }
    } 
    // Mode grille normal
    else {
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
                onPhotoZoom = { photo ->
                    // Activer le mode plein écran avec l'index de la photo
                    val index = viewModel.getPhotoIndex(photo)
                    if (index != -1) {
                        viewModel.setFullscreenPhotoIndex(index)
                        viewModel.setFullscreenMode(true)
                    }
                },
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