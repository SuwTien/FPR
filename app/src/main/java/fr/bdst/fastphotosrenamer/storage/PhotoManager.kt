package fr.bdst.fastphotosrenamer.storage

import android.content.Context
import fr.bdst.fastphotosrenamer.model.PhotoModel
import fr.bdst.fastphotosrenamer.repository.interfaces.PhotoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Gestionnaire centralisé pour les opérations liées aux photos
 * Utilise PhotoRepository comme source de données
 */
class PhotoManager private constructor(private val photoRepository: PhotoRepository) {
    
    companion object {
        const val PAGE_SIZE = 30 // Nombre de photos par page
        
        @Volatile
        private var INSTANCE: PhotoManager? = null
        
        // Méthode pour obtenir l'instance unique du manager
        fun getInstance(photoRepository: PhotoRepository): PhotoManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PhotoManager(photoRepository)
                INSTANCE = instance
                instance
            }
        }
    }
    
    // État interne pour suivre si une opération de renommage est en cours
    private val _isRenameOperationInProgress = MutableStateFlow(false)
    val isRenameOperationInProgress: StateFlow<Boolean> = _isRenameOperationInProgress
    
    /**
     * Charge une page de photos depuis un dossier spécifique
     */
    fun loadPhotosPageFromFolder(
        context: Context,
        folderPath: String,
        offset: Int,
        limit: Int
    ): Flow<Pair<List<PhotoModel>, Boolean>> {
        return photoRepository.loadPhotosPageFromFolder(context, folderPath, offset, limit)
    }
    
    /**
     * Charge les photos depuis un dossier spécifique ou le dossier par défaut
     */
    fun loadPhotos(
        context: Context,
        folderPath: String
    ): Flow<Pair<List<PhotoModel>, Boolean>> {
        return photoRepository.loadPhotos(context, folderPath)
    }
    
    /**
     * Supprime une photo du système
     */
    fun deletePhoto(
        context: Context,
        photo: PhotoModel
    ): Flow<Boolean> {
        return photoRepository.deletePhoto(context, photo)
    }
    
    /**
     * Renomme une photo
     */
    fun renamePhoto(
        context: Context,
        photo: PhotoModel,
        newName: String,
        callback: PhotoRenamer.RenameCallback
    ): Flow<PhotoRepository.RenameResult> {
        _isRenameOperationInProgress.value = true
        
        // Lancer une observation du flow en background
        GlobalScope.launch(Dispatchers.Main) {
            photoRepository.renamePhoto(context, photo, newName).collect { result ->
                if (!result.inProgress) {
                    _isRenameOperationInProgress.value = false
                    if (result.success && result.newName != null) {
                        callback.onRenameSuccess(result.newName)
                    }
                    callback.onRenameComplete(result.reloadNeeded)
                } else {
                    callback.onRenameInProgress(true)
                }
            }
        }
        
        // Retourner le flow original pour que le ViewModel puisse aussi l'observer
        return photoRepository.renamePhoto(context, photo, newName)
    }
    
    /**
     * Vérifie si un nom de fichier existe déjà dans le dossier parent d'un fichier
     */
    fun checkIfNameExists(currentPhotoPath: String, newFullName: String): Boolean {
        return photoRepository.checkIfNameExists(currentPhotoPath, newFullName)
    }
    
    /**
     * Importe les photos du dossier DCIM/Camera vers un dossier spécifié
     */
    fun importPhotosFromCamera(
        context: Context,
        destinationFolder: String,
        onComplete: (imported: Int, skipped: Int) -> Unit
    ): Flow<Pair<Int, Int>> {
        // Lancer une observation du flow en background
        GlobalScope.launch(Dispatchers.Main) {
            photoRepository.importPhotosFromCamera(context, destinationFolder).collect { (imported, skipped) ->
                onComplete(imported, skipped)
            }
        }
        
        // Retourner le flow original
        return photoRepository.importPhotosFromCamera(context, destinationFolder)
    }
    
    /**
     * Déplace les photos d'un dossier vers un autre
     */
    fun movePhotosFromFolder(
        context: Context,
        sourceFolder: String,
        destinationFolder: String,
        onComplete: (moved: Int, skipped: Int, failed: Int) -> Unit
    ): Flow<Triple<Int, Int, Int>> {
        // Lancer une observation du flow en background
        GlobalScope.launch(Dispatchers.Main) {
            photoRepository.movePhotosFromFolder(context, sourceFolder, destinationFolder).collect { (moved, skipped, failed) ->
                onComplete(moved, skipped, failed)
            }
        }
        
        // Retourner le flow original
        return photoRepository.movePhotosFromFolder(context, sourceFolder, destinationFolder)
    }
    
    /**
     * Obtient les photos du dossier DCIM/Camera
     */
    fun getCameraPhotos(context: Context): Flow<List<PhotoModel>> {
        return photoRepository.getCameraPhotos(context)
    }
}