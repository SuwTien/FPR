package fr.bdst.fastphotosrenamer.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.bdst.fastphotosrenamer.MainActivity
import fr.bdst.fastphotosrenamer.di.AppDependencies
import fr.bdst.fastphotosrenamer.model.PhotoModel
import fr.bdst.fastphotosrenamer.storage.FolderManager
import fr.bdst.fastphotosrenamer.storage.PhotoManager
import fr.bdst.fastphotosrenamer.storage.PhotoRenamer
import fr.bdst.fastphotosrenamer.utils.FilePathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel pour la gestion des photos et des dossiers
 * Implémentation avec StateFlow pour la Phase 3
 */
class PhotosViewModel : ViewModel() {
    
    // Instances des managers injectées via AppDependencies
    private val photoManager: PhotoManager = AppDependencies.photoManager
    private val folderManager: FolderManager = AppDependencies.folderManager
    
    // StateFlows pour les photos
    private val _photos = MutableStateFlow<List<PhotoModel>>(emptyList())
    val photos: StateFlow<List<PhotoModel>> = _photos.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _hasMorePhotos = MutableStateFlow(false)
    val hasMorePhotos: StateFlow<Boolean> = _hasMorePhotos.asStateFlow()

    // StateFlows pour la photo sélectionnée
    private val _selectedPhoto = MutableStateFlow<PhotoModel?>(null)
    val selectedPhoto: StateFlow<PhotoModel?> = _selectedPhoto.asStateFlow()

    // StateFlow pour l'opération de renommage en cours
    private val _isRenameOperationInProgress = MutableStateFlow(false)
    val isRenameOperationInProgress: StateFlow<Boolean> = _isRenameOperationInProgress.asStateFlow()

    // StateFlows pour le mode plein écran
    private val _fullscreenMode = MutableStateFlow(false)
    val fullscreenMode: StateFlow<Boolean> = _fullscreenMode.asStateFlow()

    // StateFlow pour l'index de la photo en plein écran
    private val _fullscreenPhotoIndex = MutableStateFlow(0)
    val fullscreenPhotoIndex: StateFlow<Int> = _fullscreenPhotoIndex.asStateFlow()

    // StateFlow pour indiquer si on vient du mode plein écran
    private val _wasInFullscreenMode = MutableStateFlow(false)
    val wasInFullscreenMode: StateFlow<Boolean> = _wasInFullscreenMode.asStateFlow()

    // StateFlow pour indiquer si on charge plus de photos
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    
    // StateFlows pour les dossiers
    private val _availableFolders = MutableStateFlow<List<String>>(emptyList())
    val availableFolders: StateFlow<List<String>> = _availableFolders.asStateFlow()
    
    private val _currentFolder = MutableStateFlow("")
    val currentFolder: StateFlow<String> = _currentFolder.asStateFlow()
    
    // StateFlow pour l'état du FolderPicker
    private val _shouldOpenFolderDropdown = MutableStateFlow(false)
    val shouldOpenFolderDropdown: StateFlow<Boolean> = _shouldOpenFolderDropdown.asStateFlow()
    
    // État pour la pagination
    private var photosOffset = 0
    
    /**
     * Initialise le ViewModel avec le contexte de l'application
     */
    fun initialize(context: Context) {
        // Charge les dossiers disponibles
        loadAvailableFolders(context)
        
        // S'assurer qu'un dossier par défaut est défini
        if (_currentFolder.value.isEmpty()) {
            viewModelScope.launch {
                val defaultFolder = folderManager.getDefaultFolder()
                _currentFolder.value = defaultFolder
                loadPhotos(context, defaultFolder)
            }
        }
    }
    
    /**
     * Charge les photos depuis un dossier spécifique
     */
    fun loadPhotos(context: Context, folderPath: String = "") {
        _isLoading.value = true
        photosOffset = 0 // Réinitialiser l'offset pour un nouveau chargement
        
        viewModelScope.launch {
            // Si un dossier spécifique est fourni, l'utiliser
            val folder = if (folderPath.isNotEmpty()) {
                folderPath
            } else {
                _currentFolder.value.takeIf { it.isNotEmpty() } ?: folderManager.getDefaultFolder()
            }
            
            // Mettre à jour le dossier courant si nécessaire
            if (_currentFolder.value != folder) {
                _currentFolder.value = folder
            }
            
            // Charger les photos
            photoManager.loadPhotos(context, folder).collect { (photos, hasMore) ->
                _photos.value = photos
                _hasMorePhotos.value = hasMore
                _isLoading.value = false
                photosOffset = photos.size
            }
        }
    }
    
    /**
     * Charge plus de photos (pagination)
     */
    fun loadMorePhotos(context: Context) {
        if (_isLoading.value || !_hasMorePhotos.value) {
            return
        }
        
        _isLoadingMore.value = true
        
        viewModelScope.launch {
            val folderPath = _currentFolder.value.takeIf { it.isNotEmpty() } ?: return@launch
            
            photoManager.loadPhotosPageFromFolder(
                context, folderPath, photosOffset, PhotoManager.PAGE_SIZE
            ).collect { (newPhotos, hasMore) ->
                // Combiner les nouvelles photos avec celles existantes
                val currentList = _photos.value
                val updatedList = currentList + newPhotos
                
                _photos.value = updatedList
                _hasMorePhotos.value = hasMore
                _isLoading.value = false
                _isLoadingMore.value = false
                photosOffset = updatedList.size
            }
        }
    }
    
    /**
     * Supprime une photo
     */
    fun deletePhoto(context: Context, photo: PhotoModel): Boolean {
        var success = false
        viewModelScope.launch {
            photoManager.deletePhoto(context, photo).collect { result ->
                if (result) {
                    // Mettre à jour la liste des photos
                    val currentList = _photos.value.toMutableList()
                    currentList.remove(photo)
                    _photos.value = currentList
                    success = true
                }
            }
        }
        return success
    }
    
    /**
     * Renomme une photo
     * @param onComplete Callback appelé lorsque l'opération est terminée, avec un booléen indiquant le succès
     */
    fun renamePhoto(context: Context, photo: PhotoModel, newName: String, onComplete: (Boolean) -> Unit) {
        // Vérifier d'abord si nous avons l'autorisation de gérer les fichiers sur Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            // Demander la permission
            if (context is MainActivity) {
                context.requestAllFilesPermission()
            }
            // Indiquer que le renommage n'a pas pu être effectué par manque de permissions
            onComplete(false)
            return
        }
        
        _isRenameOperationInProgress.value = true
        
        viewModelScope.launch {
            try {
                // Capturer l'état du mode plein écran avant le renommage
                val wasInFullscreen = _wasInFullscreenMode.value
                val fullscreenIndex = _fullscreenPhotoIndex.value
                
                PhotoRenamer.renamePhotoFlow(context, photo, newName)
                    .collect { result ->
                        if (!result.inProgress) {
                            _isRenameOperationInProgress.value = false
                            
                            if (result.success) {
                                // Recharger les photos si nécessaire
                                if (result.reloadNeeded) {
                                    loadPhotos(context, _currentFolder.value)
                                }
                                
                                // Utiliser withContext(Dispatchers.Main) pour s'assurer que le callback
                                // est exécuté sur le thread principal
                                withContext(Dispatchers.Main) {
                                    // Appeler le callback pour indiquer le succès
                                    onComplete(true)
                                }
                            } else {
                                // Utiliser withContext(Dispatchers.Main) pour s'assurer que le callback
                                // est exécuté sur le thread principal
                                withContext(Dispatchers.Main) {
                                    onComplete(false)
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                // Gérer les erreurs et appeler le callback avec false
                _isRenameOperationInProgress.value = false
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }
    
    /**
     * Charge la liste des dossiers disponibles
     */
    fun loadAvailableFolders(context: Context) {
        viewModelScope.launch {
            folderManager.loadAvailableFolders(context).collect { folders ->
                _availableFolders.value = folders
            }
        }
    }
    
    /**
     * Crée un nouveau dossier
     */
    fun createNewFolder(context: Context, folderName: String) {
        viewModelScope.launch {
            folderManager.createNewFolder(context, folderName).collect { newFolderPath ->
                if (newFolderPath != null) {
                    // Si la création a réussi, naviguer vers ce dossier
                    _currentFolder.value = newFolderPath
                    loadPhotos(context, newFolderPath)
                }
            }
        }
    }
    
    /**
     * Importe les photos de l'appareil photo
     */
    fun importPhotosFromCamera(context: Context) {
        val currentFolderPath = _currentFolder.value.takeIf { it.isNotEmpty() } ?: folderManager.getDefaultFolder()
        
        viewModelScope.launch {
            photoManager.importPhotosFromCamera(context, currentFolderPath) { imported, skipped ->
                if (imported > 0) {
                    // Recharger les photos après l'importation
                    loadPhotos(context, currentFolderPath)
                }
            }.collect() // Collecte le Flow pour déclencher l'opération
        }
    }
    
    /**
     * Déplace les photos d'un dossier vers un autre
     */
    fun movePhotosFromFolder(context: Context, sourceFolder: String, destinationFolder: String) {
        viewModelScope.launch {
            photoManager.movePhotosFromFolder(context, sourceFolder, destinationFolder) { moved, skipped, failed ->
                if (moved > 0) {
                    // Si des photos ont été déplacées, recharger le dossier actuel
                    loadPhotos(context, _currentFolder.value)
                }
            }.collect() // Collecte le Flow pour déclencher l'opération
        }
    }

    /**
     * Déplace les photos du dossier DCIM/Camera vers un dossier de destination
     */
    fun movePhotosFromCamera(context: Context, sourceFolder: String, destinationFolder: String) {
        movePhotosFromFolder(context, sourceFolder, destinationFolder)
    }
    
    /**
     * Définit l'état d'ouverture du menu déroulant des dossiers
     */
    fun setShouldOpenFolderDropdown(shouldOpen: Boolean) {
        _shouldOpenFolderDropdown.value = shouldOpen
        folderManager.setShouldOpenFolderDropdown(shouldOpen)
    }
    
    /**
     * Réinitialise l'état du menu déroulant des dossiers
     */
    fun resetShouldOpenFolderDropdown() {
        _shouldOpenFolderDropdown.value = false
        folderManager.resetShouldOpenFolderDropdown()
    }
    
    /**
     * Vérifie si un nom de fichier existe déjà dans le dossier parent d'un fichier
     */
    fun checkIfNameExists(currentPhotoPath: String, newFullName: String): Boolean {
        return photoManager.checkIfNameExists(currentPhotoPath, newFullName)
    }
    
    /**
     * Vérifie si un dossier existe
     */
    fun folderExists(folderPath: String): Boolean {
        return folderManager.folderExists(folderPath)
    }

    /**
     * Sélectionne une photo pour l'affichage ou l'édition
     */
    fun selectPhoto(photo: PhotoModel) {
        _selectedPhoto.value = photo
    }

    /**
     * Efface la photo sélectionnée
     */
    fun clearSelectedPhoto() {
        _selectedPhoto.value = null
    }

    /**
     * Charge les photos depuis un dossier spécifique
     */
    fun loadPhotosFromFolder(context: Context, folderPath: String) {
        loadPhotos(context, folderPath)
    }

    /**
     * Lance l'appareil photo
     */
    fun launchCamera(context: Context, cameraLauncher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }

    /**
     * Définit le dossier courant
     */
    fun setCurrentFolder(folderPath: String) {
        _currentFolder.value = folderPath
    }

    /**
     * Active ou désactive le mode plein écran
     */
    fun setFullscreenMode(enabled: Boolean) {
        _fullscreenMode.value = enabled
    }

    /**
     * Définit l'index de la photo en mode plein écran
     */
    fun setFullscreenPhotoIndex(index: Int) {
        _fullscreenPhotoIndex.value = index
    }

    /**
     * Passe à la photo suivante en mode plein écran
     */
    fun nextFullscreenPhoto() {
        val currentIndex = _fullscreenPhotoIndex.value
        val maxIndex = _photos.value.size - 1
        if (currentIndex < maxIndex) {
            _fullscreenPhotoIndex.value = currentIndex + 1
        }
    }

    /**
     * Passe à la photo précédente en mode plein écran
     */
    fun previousFullscreenPhoto() {
        val currentIndex = _fullscreenPhotoIndex.value
        if (currentIndex > 0) {
            _fullscreenPhotoIndex.value = currentIndex - 1
        }
    }

    /**
     * Obtient l'index d'une photo dans la liste des photos
     */
    fun getPhotoIndex(photo: PhotoModel): Int {
        return _photos.value.indexOf(photo)
    }

    /**
     * Indique qu'on vient du mode plein écran
     */
    fun setWasInFullscreenMode(wasInFullscreen: Boolean) {
        _wasInFullscreenMode.value = wasInFullscreen
    }

    /**
     * Réinitialise l'état indiquant qu'on vient du mode plein écran
     */
    fun resetWasInFullscreenMode() {
        _wasInFullscreenMode.value = false
    }

    /**
     * Met à jour la photo sélectionnée après un renommage
     */
    fun updateSelectedPhotoAfterRename(newPhoto: PhotoModel) {
        _selectedPhoto.value = newPhoto
    }
}