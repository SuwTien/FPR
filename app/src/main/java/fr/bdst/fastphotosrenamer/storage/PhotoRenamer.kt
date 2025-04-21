package fr.bdst.fastphotosrenamer.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import fr.bdst.fastphotosrenamer.model.PhotoModel
import fr.bdst.fastphotosrenamer.repository.interfaces.PhotoRepository
import fr.bdst.fastphotosrenamer.utils.FilePathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Utilitaire pour renommer les photos
 */
object PhotoRenamer {

    /**
     * Interface de callback pour les opérations de renommage de photos
     * @deprecated Utilisez plutôt les Flows retournés par les méthodes
     */
    @Deprecated("Utilisez plutôt les Flows retournés par les méthodes, cette interface sera supprimée dans une future version")
    interface RenameCallback {
        fun onRenameSuccess(newName: String)
        fun onRenameInProgress(inProgress: Boolean)
        fun onRenameComplete(reloadPhotos: Boolean)
    }

    /**
     * Renomme une photo et met à jour MediaStore
     * @param context Le contexte de l'application
     * @param photo La photo à renommer
     * @param newName Le nouveau nom à appliquer
     * @param callback Callback pour informer de la progression et du résultat (deprecated)
     * @return true si l'opération a été initiée avec succès, false sinon
     */
    fun renamePhoto(
        context: Context,
        photo: PhotoModel,
        newName: String,
        callback: RenameCallback
    ): Boolean {
        // Vérifier que les paramètres sont valides
        if (newName.isBlank() || photo.path.isBlank()) {
            return false
        }
        
        // Callback pour indiquer que l'opération est en cours
        callback.onRenameInProgress(true)
        
        // Lancer l'opération asynchrone
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val parentPath = FilePathUtils.getParentFolderPath(photo.path)
                if (parentPath == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Chemin parent non trouvé", Toast.LENGTH_SHORT).show()
                        callback.onRenameInProgress(false)
                        callback.onRenameComplete(false)
                    }
                    return@launch
                }
                
                val oldFile = File(photo.path)
                if (!oldFile.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Fichier non trouvé", Toast.LENGTH_SHORT).show()
                        callback.onRenameInProgress(false)
                        callback.onRenameComplete(false)
                    }
                    return@launch
                }
                
                // Créer le fichier avec le nouveau nom
                val newFile = File(parentPath, newName)
                
                // Vérifier si un fichier avec ce nom existe déjà
                if (newFile.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Un fichier avec ce nom existe déjà", Toast.LENGTH_SHORT).show()
                        callback.onRenameInProgress(false)
                        callback.onRenameComplete(false)
                    }
                    return@launch
                }
                
                // Tentative de renommage direct du fichier
                val success = oldFile.renameTo(newFile)
                if (success) {
                    // Mettre à jour MediaStore
                    updateMediaStore(context, photo, newFile)
                    
                    withContext(Dispatchers.Main) {
                        // Informer du succès via callback
                        callback.onRenameSuccess(newName)
                        callback.onRenameInProgress(false)
                        callback.onRenameComplete(true)
                        
                        // Afficher message de confirmation
                        Toast.makeText(context, "Photo renommée en $newName", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Échec du renommage", Toast.LENGTH_SHORT).show()
                        callback.onRenameInProgress(false)
                        callback.onRenameComplete(false)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
                    callback.onRenameInProgress(false)
                    callback.onRenameComplete(false)
                }
            }
        }
        
        return true
    }
    
    /**
     * Version moderne qui utilise les Flow au lieu des callbacks
     * @param context Le contexte de l'application
     * @param photo La photo à renommer
     * @param newName Le nouveau nom à appliquer
     * @return Un Flow émettant les résultats du processus de renommage
     */
    fun renamePhotoFlow(
        context: Context,
        photo: PhotoModel,
        newName: String
    ): Flow<PhotoRepository.RenameResult> = flow {
        // Émettre l'état "en cours"
        emit(PhotoRepository.RenameResult(
            success = false,
            inProgress = true
        ))
        
        try {
            // Vérifier que les paramètres sont valides
            if (newName.isBlank() || photo.path.isBlank()) {
                val errorMsg = "Paramètres invalides: nom='$newName', chemin='${photo.path}'"
                android.util.Log.e("PhotoRenamer", errorMsg)
                emit(PhotoRepository.RenameResult(
                    success = false,
                    inProgress = false,
                    error = errorMsg
                ))
                return@flow
            }
            
            val parentPath = FilePathUtils.getParentFolderPath(photo.path)
            if (parentPath == null) {
                val errorMsg = "Chemin parent non trouvé pour: ${photo.path}"
                android.util.Log.e("PhotoRenamer", errorMsg)
                emit(PhotoRepository.RenameResult(
                    success = false,
                    inProgress = false,
                    error = errorMsg
                ))
                return@flow
            }
            
            val oldFile = File(photo.path)
            if (!oldFile.exists()) {
                val errorMsg = "Fichier non trouvé: ${photo.path}"
                android.util.Log.e("PhotoRenamer", errorMsg)
                emit(PhotoRepository.RenameResult(
                    success = false,
                    inProgress = false,
                    error = errorMsg
                ))
                return@flow
            }
            
            // Vérifier que le fichier source est accessible en lecture
            if (!oldFile.canRead()) {
                val errorMsg = "Fichier non lisible: ${photo.path}"
                android.util.Log.e("PhotoRenamer", errorMsg)
                emit(PhotoRepository.RenameResult(
                    success = false,
                    inProgress = false,
                    error = errorMsg
                ))
                return@flow
            }
            
            // Vérifier que le répertoire parent est accessible en écriture
            val parentDir = File(parentPath)
            if (!parentDir.canWrite()) {
                val errorMsg = "Dossier parent non accessible en écriture: $parentPath"
                android.util.Log.e("PhotoRenamer", errorMsg)
                emit(PhotoRepository.RenameResult(
                    success = false,
                    inProgress = false,
                    error = errorMsg
                ))
                return@flow
            }
            
            // Créer le fichier avec le nouveau nom
            val newFile = File(parentPath, newName)
            
            // Vérifier si un fichier avec ce nom existe déjà
            if (newFile.exists()) {
                val errorMsg = "Un fichier avec ce nom existe déjà: ${newFile.absolutePath}"
                android.util.Log.e("PhotoRenamer", errorMsg)
                emit(PhotoRepository.RenameResult(
                    success = false,
                    inProgress = false,
                    error = errorMsg
                ))
                return@flow
            }
            
            android.util.Log.d("PhotoRenamer", "Tentative de renommage: ${oldFile.absolutePath} -> ${newFile.absolutePath}")
            
            // Méthode alternative avec copie explicite puis suppression si renameTo échoue
            var success = oldFile.renameTo(newFile)
            
            if (!success) {
                android.util.Log.w("PhotoRenamer", "renameTo a échoué, tentative de copie/suppression")
                
                try {
                    // Tenter une copie suivie d'une suppression
                    oldFile.inputStream().use { input ->
                        newFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    // Vérifier que la copie a réussi (taille et existence)
                    if (newFile.exists() && newFile.length() == oldFile.length()) {
                        // Suppression de l'ancien fichier
                        success = oldFile.delete()
                        if (!success) {
                            android.util.Log.w("PhotoRenamer", "La copie a réussi mais la suppression a échoué")
                            // On considère quand même que c'est un succès car le fichier est copié
                            success = true
                        }
                    } else {
                        android.util.Log.e("PhotoRenamer", "Échec de copie: nouveau fichier invalide")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PhotoRenamer", "Erreur pendant la copie: ${e.message}", e)
                    success = false
                }
            }
            
            if (success) {
                android.util.Log.i("PhotoRenamer", "Renommage réussi: ${newFile.absolutePath}")
                
                // Mettre à jour MediaStore
                updateMediaStoreFlow(context, photo, newFile)
                
                // Afficher message de confirmation dans le thread principal
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Photo renommée en $newName", Toast.LENGTH_SHORT).show()
                }
                
                emit(PhotoRepository.RenameResult(
                    success = true,
                    newName = newName,
                    inProgress = false,
                    reloadNeeded = true
                ))
            } else {
                val errorMsg = "Échec du renommage après plusieurs tentatives"
                android.util.Log.e("PhotoRenamer", errorMsg)
                
                // Afficher message d'erreur dans le thread principal
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                }
                
                emit(PhotoRepository.RenameResult(
                    success = false,
                    inProgress = false,
                    error = errorMsg
                ))
            }
        } catch (e: Exception) {
            val errorMsg = "Erreur: ${e.message}"
            android.util.Log.e("PhotoRenamer", errorMsg, e)
            
            // Afficher message d'erreur dans le thread principal
            withContext(Dispatchers.Main) {
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            }
            
            emit(PhotoRepository.RenameResult(
                success = false,
                inProgress = false,
                error = errorMsg
            ))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Version Flow-compatible pour mettre à jour MediaStore
     */
    private fun updateMediaStoreFlow(context: Context, oldPhoto: PhotoModel, newFile: File) {
        // Approche différente selon la version d'Android
        when {
            // Android 10+ (Q) - méthode moderne
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                updateMediaStoreModernFlow(context, oldPhoto, newFile)
            }
            // Android 9- (P et avant) - méthode classique
            else -> {
                updateMediaStoreLegacyFlow(context, oldPhoto, newFile)
            }
        }
        
        // Notifier le système du changement
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.data = Uri.fromFile(newFile)
        context.sendBroadcast(mediaScanIntent)
        
        // Forcer une mise à jour du fichier pour éviter les problèmes de cache
        MediaScannerConnection.scanFile(
            context,
            arrayOf(newFile.absolutePath),
            null
        ) { _, _ -> 
            // Scan terminé, rien à faire ici
        }
    }
    
    /**
     * Met à jour MediaStore sur les versions modernes d'Android (10+) - compatible avec Flow
     */
    private fun updateMediaStoreModernFlow(context: Context, oldPhoto: PhotoModel, newFile: File) {
        val resolver = context.contentResolver
        
        try {
            // Créer des values pour la mise à jour
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, newFile.name)
                put(MediaStore.MediaColumns.DATA, newFile.absolutePath)
            }
            
            // Mettre à jour l'entrée existante
            resolver.update(oldPhoto.uri, values, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
            
            // Fallback : suppression et création d'une nouvelle entrée
            try {
                resolver.delete(oldPhoto.uri, null, null)
                
                // Rafraîchir la base de données MediaStore
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(newFile.absolutePath),
                    null,
                    null
                )
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }
    
    /**
     * Met à jour MediaStore sur les versions antérieures d'Android (avant 10) - compatible avec Flow
     */
    private fun updateMediaStoreLegacyFlow(context: Context, oldPhoto: PhotoModel, newFile: File) {
        val resolver = context.contentResolver
        
        try {
            // Supprimer l'ancienne entrée
            resolver.delete(oldPhoto.uri, null, null)
            
            // Créer une nouvelle entrée
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, newFile.name)
                put(MediaStore.Images.Media.DATA, newFile.absolutePath)
                put(MediaStore.Images.Media.MIME_TYPE, getMimeType(newFile.name))
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Images.Media.DATE_MODIFIED, newFile.lastModified() / 1000)
                put(MediaStore.Images.Media.SIZE, newFile.length())
            }
            
            val externalUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            resolver.insert(externalUri, values)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Méthodes de support pour la compatibilité avec l'ancienne approche
    
    /**
     * Met à jour les entrées MediaStore suite au renommage d'un fichier
     * @deprecated Utilisez updateMediaStoreFlow à la place
     */
    @Deprecated("Utilisez updateMediaStoreFlow à la place")
    private suspend fun updateMediaStore(context: Context, oldPhoto: PhotoModel, newFile: File) {
        // Déléguer à la version Flow-compatible
        updateMediaStoreFlow(context, oldPhoto, newFile)
    }
    
    /**
     * Met à jour MediaStore sur les versions modernes d'Android (10+)
     * @deprecated Utilisez updateMediaStoreModernFlow à la place
     */
    @Deprecated("Utilisez updateMediaStoreModernFlow à la place")
    private suspend fun updateMediaStoreModern(context: Context, oldPhoto: PhotoModel, newFile: File) {
        updateMediaStoreModernFlow(context, oldPhoto, newFile)
    }
    
    /**
     * Met à jour MediaStore sur les versions antérieures d'Android (avant 10)
     * @deprecated Utilisez updateMediaStoreLegacyFlow à la place
     */
    @Deprecated("Utilisez updateMediaStoreLegacyFlow à la place")
    private fun updateMediaStoreLegacy(context: Context, oldPhoto: PhotoModel, newFile: File) {
        updateMediaStoreLegacyFlow(context, oldPhoto, newFile)
    }
    
    /**
     * Obtient le type MIME d'un fichier à partir de son extension
     */
    private fun getMimeType(fileName: String): String {
        val extension = fileName.substring(fileName.lastIndexOf(".") + 1).lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            else -> "image/*"
        }
    }
}