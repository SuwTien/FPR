package fr.bdst.fastphotosrenamer.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.bdst.fastphotosrenamer.viewmodel.PhotosViewModel
import fr.bdst.fastphotosrenamer.ui.components.FolderPickerDialog
import fr.bdst.fastphotosrenamer.ui.components.CreateFolderDialog
import fr.bdst.fastphotosrenamer.ui.components.AboutMenuButton
import fr.bdst.fastphotosrenamer.ui.components.FunctionalityDialog
import fr.bdst.fastphotosrenamer.ui.components.LicenseDialog
import fr.bdst.fastphotosrenamer.ui.components.CreditsDialog
import fr.bdst.fastphotosrenamer.ui.components.FullLicenseDialog
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton

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
    
    // États pour le chargement paginé
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle(initialValue = false)
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle(initialValue = false)
    val hasMorePhotos by viewModel.hasMorePhotos.collectAsStateWithLifecycle(initialValue = false)
    
    // Nouveaux états pour le mode plein écran
    val fullscreenMode by viewModel.fullscreenMode.collectAsStateWithLifecycle(initialValue = false)
    val fullscreenPhotoIndex by viewModel.fullscreenPhotoIndex.collectAsStateWithLifecycle(initialValue = 0)
    
    val context = LocalContext.current
    
    var showImages by remember { mutableStateOf(true) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    
    // Nouveaux états pour le menu "À propos"
    var showAboutMenu by remember { mutableStateOf(false) }
    var showFunctionalityDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }
    var showFullLicenseDialog by remember { mutableStateOf(false) } // Nouvel état pour la licence complète
    
    // Effet pour charger les photos une seule fois au démarrage
    LaunchedEffect(Unit) {
        viewModel.loadAvailableFolders(context)
        viewModel.loadPhotosFromFolder(context, viewModel.currentFolder.value)
    }
    
    // Effet pour recharger les photos quand le dossier change
    LaunchedEffect(currentFolder) {
        // Recharger les photos du nouveau dossier
        viewModel.loadPhotosFromFolder(context, currentFolder)
        
        // Sécurité supplémentaire : si une photo était sélectionnée, vérifier si elle est toujours valide
        // dans le nouveau répertoire, sinon effacer la sélection
        val currentSelectedPhoto = selectedPhoto // Capture locale de la valeur
        if (currentSelectedPhoto != null) {
            val photoIndex = viewModel.getPhotoIndex(currentSelectedPhoto)
            if (photoIndex == -1) {
                // La photo sélectionnée n'existe pas dans le nouveau répertoire
                viewModel.clearSelectedPhoto()
            }
        }
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
                    if (viewModel.wasInFullscreenMode.value) {
                        viewModel.clearSelectedPhoto()
                        viewModel.setFullscreenMode(true)
                        viewModel.resetWasInFullscreenMode()
                    } else {
                        // Comportement normal : retour à la grille
                        viewModel.clearSelectedPhoto()
                    }
                },
                onRename = { photo, newName, ctx ->
                    viewModel.renamePhoto(ctx, photo, newName) { success ->
                        // Si on vient du mode plein écran, retourner en mode plein écran après le renommage
                        if (viewModel.wasInFullscreenMode.value) {
                            viewModel.clearSelectedPhoto()
                            viewModel.setFullscreenMode(true)
                            viewModel.resetWasInFullscreenMode()
                        } else {
                            // Sinon, revenir à la grille
                            viewModel.clearSelectedPhoto()
                        }
                    }
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
                                }
                            }
                        }
                    },
                    actions = {
                        // Bouton avec les trois points pour le menu "À propos"
                        AboutMenuButton(
                            showAboutMenu = showAboutMenu,
                            onShowAboutMenuChange = { showAboutMenu = it },
                            onShowFunctionalityDialog = { showFunctionalityDialog = true },
                            onShowLicenseDialog = { showLicenseDialog = true },
                            onShowCreditsDialog = { showCreditsDialog = true }
                        )
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
                isLoading = isLoading,
                isLoadingMore = isLoadingMore,
                hasMorePhotos = hasMorePhotos,
                onLoadMore = { viewModel.loadMorePhotos(context) },
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
    
    // Dialogues du menu "À propos"
    
    // Dialogue Fonctionnement
    if (showFunctionalityDialog) {
        FunctionalityDialog(
            onDismiss = { showFunctionalityDialog = false }
        )
    }
    
    // Dialogue Licence
    if (showLicenseDialog) {
        LicenseDialog(
            onDismiss = { showLicenseDialog = false },
            onShowFullLicense = { 
                showLicenseDialog = false  // Fermer le dialogue de licence standard
                showFullLicenseDialog = true  // Ouvrir le dialogue de licence complète
            }
        )
    }
    
    // Dialogue de licence complète
    if (showFullLicenseDialog) {
        FullLicenseDialog(
            onDismiss = { showFullLicenseDialog = false }
        )
    }
    
    // Dialogue Crédits
    if (showCreditsDialog) {
        CreditsDialog(
            onDismiss = { showCreditsDialog = false }
        )
    }
}