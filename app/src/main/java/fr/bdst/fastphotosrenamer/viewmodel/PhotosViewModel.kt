package fr.bdst.fastphotosrenamer.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.bdst.fastphotosrenamer.model.PhotoModel
import fr.bdst.fastphotosrenamer.storage.FolderManager
import fr.bdst.fastphotosrenamer.storage.PhotoManager
import fr.bdst.fastphotosrenamer.storage.PhotoRenamer
import fr.bdst.fastphotosrenamer.utils.FilePathUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class PhotosViewModel : ViewModel() {
    
    private val _photos = MutableStateFlow<List<PhotoModel>>(emptyList())
    val photos: StateFlow<List<PhotoModel>> = _photos
    
    private val _selectedPhoto = MutableStateFlow<PhotoModel?>(null)
    val selectedPhoto: StateFlow<PhotoModel?> = _selectedPhoto

    // Accès aux états des gestionnaires
    val isRenameOperationInProgress = PhotoManager.isRenameOperationInProgress
    val availableFolders = FolderManager.availableFolders
    val shouldOpenFolderDropdown = FolderManager.shouldOpenFolderDropdown

    // Variables d'état pour les dossiers
    private val _currentFolder = MutableStateFlow<String>("")
    val currentFolder: StateFlow<String> = _currentFolder

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    // Nouveaux états pour le mode plein écran
    private val _fullscreenMode = MutableStateFlow(false)
    val fullscreenMode: StateFlow<Boolean> = _fullscreenMode
    
    // Index de la photo actuellement affichée en mode plein écran
    private val _fullscreenPhotoIndex = MutableStateFlow(0)
    val fullscreenPhotoIndex: StateFlow<Int> = _fullscreenPhotoIndex
    
    // État pour suivre si on était en mode plein écran avant d'aller sur l'écran de renommage
    private val _wasInFullscreenMode = MutableStateFlow(false)
    val wasInFullscreenMode: Boolean
        get() = _wasInFullscreenMode.value

    // Nouveaux états pour la pagination
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage

    private val _hasMorePhotos = MutableStateFlow(false)
    val hasMorePhotos: StateFlow<Boolean> = _hasMorePhotos
    
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    // Initialiser le dossier courant dans init {}
    init {
        // Utiliser FolderManager pour obtenir le dossier par défaut
        _currentFolder.value = FolderManager.getDefaultFolder()
    }

    // Méthode pour charger les photos d'un dossier avec pagination
    fun loadPhotosFromFolderPaginated(context: Context, folderPath: String, reset: Boolean = true) {
        viewModelScope.launch {
            if (reset) {
                _isLoading.value = true
                _currentPage.value = 0
                _photos.value = emptyList()
            } else {
                _isLoadingMore.value = true
            }
            
            try {
                // Sauvegarder l'index de la photo en plein écran
                val savedFullscreenIndex = _fullscreenPhotoIndex.value
                
                // Calculer l'offset pour le chargement paginé
                val offset = _currentPage.value * PhotoManager.PAGE_SIZE
                
                // Charger une page de photos en utilisant PhotoManager
                val (newPhotos, hasMore) = PhotoManager.loadPhotosPageFromFolder(
                    context,
                    folderPath,
                    offset,
                    PhotoManager.PAGE_SIZE
                )
                
                // Mettre à jour l'état
                if (reset) {
                    _photos.value = newPhotos
                } else {
                    _photos.value = _photos.value + newPhotos
                }
                
                _hasMorePhotos.value = hasMore
                
                // Incrémenter la page pour le prochain chargement
                if (hasMore) {
                    _currentPage.value = _currentPage.value + 1
                }
                
                // Restaurer l'index en plein écran si nécessaire
                if (_fullscreenMode.value && savedFullscreenIndex < _photos.value.size) {
                    _fullscreenPhotoIndex.value = savedFullscreenIndex
                }
            } catch (e: Exception) {
                // En cas d'erreur, s'assurer qu'on a au moins une liste vide
                if (reset) {
                    _photos.value = emptyList()
                }
                android.util.Log.e("FPR_DEBUG", "Erreur de chargement: ${e.message}")
            } finally {
                if (reset) {
                    _isLoading.value = false
                } else {
                    _isLoadingMore.value = false
                }
            }
        }
    }
    
    // Méthode pour charger la page suivante
    fun loadMorePhotos(context: Context) {
        if (_isLoadingMore.value || !_hasMorePhotos.value) {
            return // Ne rien faire si déjà en train de charger ou s'il n'y a plus de photos
        }
        
        loadPhotosFromFolderPaginated(context, _currentFolder.value, false)
    }

    // Remplacer la méthode originale par la version utilisant PhotoManager
    fun loadPhotosFromFolder(context: Context, folderPath: String) {
        // Réinitialiser et charger la première page
        loadPhotosFromFolderPaginated(context, folderPath, true)
    }

    // Méthode pour charger la liste des dossiers disponibles - utilise FolderManager
    fun loadAvailableFolders(context: Context) {
        viewModelScope.launch {
            FolderManager.loadAvailableFolders(context)
        }
    }

    // Méthode pour définir le dossier courant
    fun setCurrentFolder(folderPath: String) {
        _currentFolder.value = folderPath
    }

    // Méthode pour créer un nouveau dossier - utilise FolderManager
    fun createNewFolder(context: Context, folderName: String) {
        viewModelScope.launch {
            // Utiliser FolderManager pour créer le dossier
            val newFolderPath = FolderManager.createNewFolder(context, folderName)
            
            if (newFolderPath != null) {
                // Trouver et définir DCIM/Camera comme dossier courant
                val cameraFolderPath = FilePathUtils.getCameraFolderPath()
                if (FolderManager.folderExists(cameraFolderPath)) {
                    _currentFolder.value = cameraFolderPath
                    loadPhotosFromFolder(context, cameraFolderPath)
                } else {
                    // Si DCIM/Camera n'existe pas, utiliser le dossier nouvellement créé
                    _currentFolder.value = newFolderPath
                    loadPhotosFromFolder(context, newFolderPath)
                }
                
                // Indiquer qu'il faut ouvrir le menu déroulant pour montrer le nouveau dossier
                FolderManager.setShouldOpenFolderDropdown(true)
            }
        }
    }
    
    // Méthode pour définir l'état d'ouverture du menu déroulant - utilise FolderManager
    fun setShouldOpenFolderDropdown(shouldOpen: Boolean) {
        FolderManager.setShouldOpenFolderDropdown(shouldOpen)
    }
    
    // Méthode pour réinitialiser l'état d'ouverture du menu déroulant - utilise FolderManager
    fun resetShouldOpenFolderDropdown() {
        FolderManager.resetShouldOpenFolderDropdown()
    }
    
    // FONCTION 1: Chargement des photos selon le contexte (dossier spécifique ou DCIM/Camera)
    fun loadPhotos(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            _photos.value = emptyList() // Vider la liste actuelle
            _currentPage.value = 0 // Réinitialiser la pagination
            
            try {
                // Utiliser PhotoManager pour charger les photos
                val (loadedPhotos, hasMore) = PhotoManager.loadPhotos(context, _currentFolder.value)
                
                _photos.value = loadedPhotos
                _hasMorePhotos.value = hasMore
                
                if (hasMore) {
                    _currentPage.value = 1 // Première page chargée
                }
            } catch (e: Exception) {
                android.util.Log.e("FPR_DEBUG", "Erreur lors du chargement des photos: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun selectPhoto(photo: PhotoModel) {
        android.util.Log.d("FPR_DEBUG", "Photo sélectionnée: ${photo.name}, path: ${photo.path}")
        
        // Vérifier d'abord si la photo existe dans la liste actuelle des photos
        val photoIndex = _photos.value.indexOf(photo)
        
        if (photoIndex != -1) {
            // La photo existe dans la liste courante des photos, on peut la sélectionner en toute sécurité
            _selectedPhoto.value = _photos.value[photoIndex]  // Utiliser la référence de la liste actuelle
            android.util.Log.d("FPR_DEBUG", "Photo trouvée dans la liste actuelle à l'index $photoIndex")
        } else {
            // La photo n'est pas dans la liste actuelle des photos
            // Cela peut arriver lors d'un changement de répertoire
            android.util.Log.d("FPR_DEBUG", "Photo non trouvée dans la liste actuelle, ignorer la sélection")
            
            // On ne met pas à jour _selectedPhoto.value pour éviter d'avoir une référence à une photo d'un autre dossier
            // On pourrait afficher un message Toast à l'utilisateur, mais ce n'est pas nécessaire car l'UI sera simplement rafraîchie
            
            // S'assurer qu'aucune photo n'est sélectionnée
            _selectedPhoto.value = null
        }
    }
    
    fun clearSelectedPhoto() {
        _selectedPhoto.value = null
    }
    
    fun updateSelectedPhotoAfterRename(newName: String) {
        _selectedPhoto.value?.let { currentPhoto -> 
            val updatedPhoto = currentPhoto.copy(name = newName)
            _selectedPhoto.value = updatedPhoto
        }
    }
    
    // Utiliser PhotoManager pour renommer la photo
    fun renamePhoto(context: Context, photo: PhotoModel, newName: String): Boolean {
        // Créer un callback pour gérer les événements de renommage
        val callback = object : PhotoRenamer.RenameCallback {
            override fun onRenameSuccess(newName: String) {
                updateSelectedPhotoAfterRename(newName)
            }
            
            override fun onRenameInProgress(inProgress: Boolean) {
                // Non nécessaire de gérer ici, le PhotoManager s'en occupe
            }
            
            override fun onRenameComplete(reloadPhotos: Boolean) {
                if (reloadPhotos) {
                    viewModelScope.launch {
                        delay(500)
                        loadPhotos(context)
                    }
                }
            }
        }
        
        return PhotoManager.renamePhoto(context, photo, newName, callback)
    }
    
    // Utiliser PhotoManager pour supprimer la photo
    fun deletePhoto(context: Context, photo: PhotoModel): Boolean {
        val success = PhotoManager.deletePhoto(context, photo)
        
        if (success) {
            viewModelScope.launch {
                delay(500)
                loadPhotos(context)
            }
        }
        
        return success
    }
    
    // Utiliser Activity pour lancer l'appareil photo
    fun launchCamera(activity: Activity, cameraLauncher: ActivityResultLauncher<Intent>) {
        // Utiliser INTENT_ACTION_STILL_IMAGE_CAMERA au lieu de ACTION_IMAGE_CAPTURE
        // Cela ouvre l'appareil photo en mode normal et permet de prendre plusieurs photos
        val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
    
        if (cameraIntent.resolveActivity(activity.packageManager) != null) {
            cameraLauncher.launch(cameraIntent)
        } else {
            Toast.makeText(activity, "Aucune application d'appareil photo trouvée", Toast.LENGTH_SHORT).show()
        }
    }

    // Déléguer la vérification du nom au PhotoManager
    fun checkIfNameExists(currentPhotoPath: String, newFullName: String): Boolean {
        return PhotoManager.checkIfNameExists(currentPhotoPath, newFullName)
    }

    // Utiliser PhotoManager pour importer des photos depuis le dossier Camera
    fun importPhotosFromCamera(context: Context) {
        viewModelScope.launch {
            PhotoManager.importPhotosFromCamera(context, _currentFolder.value) { imported, skipped -> 
                if (imported > 0) {
                    // Rafraîchir la liste des photos
                    loadPhotosFromFolder(context, _currentFolder.value)
                }
            }
        }
    }

    // Utiliser PhotoManager pour déplacer des photos entre dossiers
    fun movePhotosFromCamera(context: Context, sourceFolder: String, destinationFolder: String) {
        viewModelScope.launch {
            // Déplacer les photos et obtenir les résultats
            val (moved, skipped, failed) = PhotoManager.movePhotosFromFolder(context, sourceFolder, destinationFolder) { _, _, _ -> 
                // Déterminer quel dossier doit être rafraîchi
                when (_currentFolder.value) {
                    sourceFolder -> {
                        // Si on affiche le dossier source, le rafraîchir
                        loadPhotosFromFolder(context, sourceFolder)
                    }
                    destinationFolder -> {
                        // Si on affiche le dossier destination, le rafraîchir
                        loadPhotosFromFolder(context, destinationFolder)
                    }
                    else -> {
                        // Dans les autres cas, rafraîchir le dossier courant
                        loadPhotosFromFolder(context, _currentFolder.value)
                    }
                }
            }
        }
    }

    // Utiliser PhotoManager pour obtenir les photos de l'appareil photo
    fun getCameraPhotos(context: Context): List<PhotoModel> {
        return PhotoManager.getCameraPhotos(context)
    }
    
    // Méthodes pour le mode plein écran
    
    // Activer/désactiver le mode plein écran
    fun setFullscreenMode(enabled: Boolean) {
        _fullscreenMode.value = enabled
    }
    
    // Définir l'index de la photo actuellement affichée en plein écran
    fun setFullscreenPhotoIndex(index: Int) {
        if (index >= 0 && index < _photos.value.size) {
            _fullscreenPhotoIndex.value = index
            // Ne pas mettre à jour la photo sélectionnée, pour éviter la confusion
            // lors de la sélection dans la grille après navigation en plein écran
        }
    }
    
    // Passer à la photo suivante en mode plein écran
    fun nextFullscreenPhoto() {
        val currentIndex = _fullscreenPhotoIndex.value
        val photosSize = _photos.value.size
        
        if (photosSize > 0) {
            // Passer à la photo suivante avec retour au début si on atteint la fin
            val nextIndex = (currentIndex + 1) % photosSize
            setFullscreenPhotoIndex(nextIndex)
        }
    }
    
    // Passer à la photo précédente en mode plein écran
    fun previousFullscreenPhoto() {
        val currentIndex = _fullscreenPhotoIndex.value
        val photosSize = _photos.value.size
        
        if (photosSize > 0) {
            // Passer à la photo précédente avec retour à la fin si on est au début
            val prevIndex = (currentIndex - 1 + photosSize) % photosSize
            setFullscreenPhotoIndex(prevIndex)
        }
    }
    
    // Obtenir l'index de la photo correspondant à un modèle PhotoModel
    fun getPhotoIndex(photo: PhotoModel): Int {
        return _photos.value.indexOf(photo)
    }

    // Réinitialiser l'état wasInFullscreenMode
    fun resetWasInFullscreenMode() {
        _wasInFullscreenMode.value = false
    }
    
    // Mémoriser que l'on vient du mode plein écran
    fun setWasInFullscreenMode(value: Boolean) {
        _wasInFullscreenMode.value = value
    }
}