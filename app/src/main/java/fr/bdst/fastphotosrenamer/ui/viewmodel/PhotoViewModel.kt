package fr.bdst.fastphotosrenamer.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.bdst.fastphotosrenamer.di.AppDependencies
import fr.bdst.fastphotosrenamer.model.PhotoModel
import fr.bdst.fastphotosrenamer.repository.interfaces.PhotoRepository
import fr.bdst.fastphotosrenamer.utils.FilePathUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel pour la gestion des photos
 */
class PhotoViewModel(private val photoRepository: PhotoRepository) : ViewModel() {

    // État de la liste des photos
    private val _photos = MutableStateFlow<List<PhotoModel>>(emptyList())
    val photos: StateFlow<List<PhotoModel>> = _photos

    // État de chargement des photos
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // État d'erreur
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // État de pagination
    private var currentOffset = 0
    private var hasMorePhotos = false
    private var currentFolder = ""

    // État d'opération de renommage en cours
    private val _isRenaming = MutableStateFlow(false)
    val isRenaming: StateFlow<Boolean> = _isRenaming

    // État de message de succès pour le renommage
    private val _renameSuccess = MutableStateFlow<String?>(null)
    val renameSuccess: StateFlow<String?> = _renameSuccess

    /**
     * Charge les photos depuis un dossier spécifique
     */
    fun loadPhotosFromFolder(context: Context, folderPath: String, forceRefresh: Boolean = false) {
        if (folderPath.isEmpty()) return
        
        if (forceRefresh || folderPath != currentFolder) {
            // Réinitialiser si on change de dossier ou force le rafraîchissement
            _photos.value = emptyList()
            currentOffset = 0
            currentFolder = folderPath
        }
        
        // Indiquer que le chargement est en cours
        _isLoading.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                photoRepository.loadPhotosPageFromFolder(context, folderPath, currentOffset, PAGE_SIZE)
                    .catch { e ->
                        _error.value = "Erreur de chargement: ${e.message}"
                        _isLoading.value = false
                    }
                    .collectLatest { (newPhotos, hasMore) ->
                        // Mettre à jour l'état des photos
                        if (currentOffset == 0) {
                            _photos.value = newPhotos
                        } else {
                            // Ajouter les nouvelles photos à la liste existante
                            val currentPhotosList = _photos.value.toMutableList()
                            currentPhotosList.addAll(newPhotos)
                            _photos.value = currentPhotosList
                        }
                        
                        // Mettre à jour l'état de pagination
                        hasMorePhotos = hasMore
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                _error.value = "Exception: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Charge les photos depuis le dossier sélectionné ou par défaut
     */
    fun loadPhotos(context: Context, selectedFolderPath: String) {
        // Réinitialiser l'état
        _photos.value = emptyList()
        _isLoading.value = true
        _error.value = null
        currentOffset = 0
        
        viewModelScope.launch {
            try {
                photoRepository.loadPhotos(context, selectedFolderPath)
                    .catch { e ->
                        _error.value = "Erreur de chargement: ${e.message}"
                        _isLoading.value = false
                    }
                    .collectLatest { (loadedPhotos, hasMore) ->
                        _photos.value = loadedPhotos
                        hasMorePhotos = hasMore
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                _error.value = "Exception: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Charge plus de photos (pagination)
     */
    fun loadMorePhotos(context: Context) {
        // Ne rien faire s'il n'y a pas plus de photos ou si chargement en cours
        if (!hasMorePhotos || _isLoading.value) return
        
        // Avancer l'offset pour la pagination
        currentOffset += PAGE_SIZE
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                photoRepository.loadPhotosPageFromFolder(context, currentFolder, currentOffset, PAGE_SIZE)
                    .catch { e ->
                        _error.value = "Erreur de chargement: ${e.message}"
                        _isLoading.value = false
                    }
                    .collectLatest { (newPhotos, hasMore) ->
                        // Ajouter les nouvelles photos à la liste existante
                        val currentPhotosList = _photos.value.toMutableList()
                        currentPhotosList.addAll(newPhotos)
                        _photos.value = currentPhotosList
                        
                        // Mettre à jour l'état de pagination
                        hasMorePhotos = hasMore
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                _error.value = "Exception: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Supprime une photo
     */
    fun deletePhoto(context: Context, photo: PhotoModel, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val success = photoRepository.deletePhoto(context, photo)
                    .catch { e ->
                        _error.value = "Erreur lors de la suppression: ${e.message}"
                        onComplete(false)
                    }
                    .first()
                
                if (success) {
                    // Supprimer la photo de la liste
                    val updatedPhotos = _photos.value.toMutableList()
                    updatedPhotos.removeIf { it.id == photo.id }
                    _photos.value = updatedPhotos
                    
                    onComplete(true)
                } else {
                    _error.value = "Échec de la suppression"
                    onComplete(false)
                }
            } catch (e: Exception) {
                _error.value = "Exception: ${e.message}"
                onComplete(false)
            }
        }
    }
    
    /**
     * Renomme une photo
     */
    fun renamePhoto(context: Context, photo: PhotoModel, newName: String, onComplete: (Boolean) -> Unit) {
        // Vérifier si le nom est valide
        if (newName.isEmpty() || newName == photo.name) {
            onComplete(false)
            return
        }
        
        // Vérifier si le nom existe déjà
        if (photoRepository.checkIfNameExists(photo.path, newName)) {
            _error.value = "Un fichier avec ce nom existe déjà"
            onComplete(false)
            return
        }
        
        _isRenaming.value = true
        
        viewModelScope.launch {
            try {
                photoRepository.renamePhoto(context, photo, newName)
                    .catch { e ->
                        _error.value = "Erreur de renommage: ${e.message}"
                        _isRenaming.value = false
                        onComplete(false)
                    }
                    .collectLatest { result ->
                        if (result.inProgress) {
                            // Opération en cours, attendre
                        } else if (result.success) {
                            _renameSuccess.value = "Photo renommée avec succès"
                            _isRenaming.value = false
                            
                            // Recharger les photos si nécessaire
                            if (result.reloadNeeded) {
                                loadPhotosFromFolder(context, currentFolder, true)
                            }
                            
                            onComplete(true)
                        } else {
                            _error.value = result.error ?: "Échec du renommage"
                            _isRenaming.value = false
                            onComplete(false)
                        }
                    }
            } catch (e: Exception) {
                _error.value = "Exception: ${e.message}"
                _isRenaming.value = false
                onComplete(false)
            }
        }
    }
    
    /**
     * Importe les photos depuis le dossier DCIM/Camera
     */
    fun importPhotosFromCamera(context: Context, destinationFolder: String, onComplete: (Int, Int) -> Unit) {
        viewModelScope.launch {
            try {
                photoRepository.importPhotosFromCamera(context, destinationFolder)
                    .catch { e ->
                        _error.value = "Erreur d'importation: ${e.message}"
                        onComplete(0, 0)
                    }
                    .collectLatest { (imported, skipped) ->
                        if (imported > 0) {
                            // Recharger les photos si on importe dans le dossier actuel
                            if (FilePathUtils.normalizePath(destinationFolder) == FilePathUtils.normalizePath(currentFolder)) {
                                loadPhotosFromFolder(context, currentFolder, true)
                            }
                        }
                        
                        onComplete(imported, skipped)
                    }
            } catch (e: Exception) {
                _error.value = "Exception: ${e.message}"
                onComplete(0, 0)
            }
        }
    }
    
    /**
     * Réinitialise le message d'erreur
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Réinitialise le message de succès
     */
    fun clearSuccessMessage() {
        _renameSuccess.value = null
    }
    
    companion object {
        const val PAGE_SIZE = 30 // Nombre de photos par page
        
        /**
         * Factory pour la création du ViewModel
         */
        class Factory : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(PhotoViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return PhotoViewModel(AppDependencies.photoRepository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}