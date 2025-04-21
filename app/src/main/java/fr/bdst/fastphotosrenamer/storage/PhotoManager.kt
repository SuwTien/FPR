package fr.bdst.fastphotosrenamer.storage

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import fr.bdst.fastphotosrenamer.model.PhotoModel
import fr.bdst.fastphotosrenamer.utils.FilePathUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.attribute.FileTime

/**
 * Gestionnaire complet pour toutes les opérations liées aux photos
 */
object PhotoManager {

    const val PAGE_SIZE = 30 // Nombre de photos par page

    // États internes
    private val _isRenameOperationInProgress = MutableStateFlow(false)
    val isRenameOperationInProgress: StateFlow<Boolean> = _isRenameOperationInProgress
    
    /**
     * Charge une page de photos depuis un dossier spécifique
     * @param context Le contexte de l'application
     * @param folderPath Le chemin du dossier
     * @param offset L'offset pour la pagination (position de départ)
     * @param limit Le nombre maximum de photos à charger
     * @return Une paire contenant la liste des photos et un booléen indiquant s'il y a d'autres photos
     */
    fun loadPhotosPageFromFolder(
        context: Context, 
        folderPath: String,
        offset: Int,
        limit: Int
    ): Pair<List<PhotoModel>, Boolean> {
        val photosList = mutableListOf<PhotoModel>()
        var hasMore = false

        try {
            val normalizedFolderPath = FilePathUtils.normalizePath(folderPath)
            
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA
            )
            
            // Condition pour sélectionner les photos du dossier spécifié
            val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf("$normalizedFolderPath/%")
            
            // Tri des photos par date de capture décroissante (plus récentes d'abord)
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                
                // Vérifier s'il existe plus d'éléments que ceux demandés
                val totalCount = cursor.count
                hasMore = totalCount > offset + limit
                
                // Se positionner à l'offset demandé si possible
                if (offset < totalCount && cursor.moveToPosition(offset)) {
                    // Charger les photos jusqu'à atteindre la limite ou la fin
                    var loadedCount = 0
                    do {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn)
                        val data = cursor.getString(dataColumn)
                        
                        // Ignorer les fichiers de corbeille ou non-images
                        if (FilePathUtils.isTrashFile(name) || !FilePathUtils.isImageFile(name)) continue
                        
                        // Vérifier que le fichier existe réellement
                        val file = File(data)
                        if (!file.exists() || file.length() == 0L) continue
                        
                        // Créer l'URI de contenu pour ce média
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        
                        // Ajouter à la liste des photos
                        photosList.add(PhotoModel(
                            id = id.toString(),
                            uri = contentUri,
                            name = name,
                            path = data,
                            file = file
                        ))
                        
                        loadedCount++
                    } while (cursor.moveToNext() && loadedCount < limit)
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return Pair(photosList, hasMore)
    }
    
    /**
     * Charge les photos depuis un dossier spécifique ou le dossier par défaut
     * @param context Le contexte de l'application
     * @param folderPath Le chemin du dossier à charger. Si vide, utilise le dossier par défaut
     * @return Une paire contenant la liste des photos et un booléen indiquant s'il y a d'autres photos
     */
    suspend fun loadPhotos(context: Context, folderPath: String): Pair<List<PhotoModel>, Boolean> = withContext(Dispatchers.IO) {
        // Si un chemin de dossier est fourni
        if (folderPath.isNotEmpty()) {
            // Cas spécial pour DCIM/Camera (utiliser le chemin exact)
            if (FilePathUtils.isCameraFolder(folderPath)) {
                val cameraFolderPath = FilePathUtils.getCameraFolderPath()
                return@withContext loadPhotosPageFromFolder(context, cameraFolderPath, 0, PAGE_SIZE)
            }
            
            // Pour les autres dossiers, utiliser le chemin fourni
            return@withContext loadPhotosPageFromFolder(context, folderPath, 0, PAGE_SIZE)
        }
        
        // Si aucun dossier n'est spécifié, essayer d'abord DCIM/Camera
        val cameraFolderPath = FilePathUtils.getCameraFolderPath()
        val cameraFolder = File(cameraFolderPath)
        
        if (cameraFolder.exists() && cameraFolder.isDirectory) {
            return@withContext loadPhotosPageFromFolder(context, cameraFolderPath, 0, PAGE_SIZE)
        }
        
        // Fallback au dossier de l'application
        val appFolderPath = FilePathUtils.getAppFolderPath()
        val appFolder = File(appFolderPath)
        if (!appFolder.exists()) {
            appFolder.mkdirs()
        }
        
        return@withContext loadPhotosPageFromFolder(context, appFolderPath, 0, PAGE_SIZE)
    }
    
    /**
     * Supprime une photo du système
     * @param context Le contexte de l'application
     * @param photo La photo à supprimer
     * @return true si la suppression a réussi, false sinon
     */
    fun deletePhoto(context: Context, photo: PhotoModel): Boolean {
        // Vérifier d'abord si on a la permission complète
        if (!checkDeletePermissions(context)) {
            return false
        }
        
        return try {
            var success = false
            
            // Essai 1: Suppression directe du fichier
            try {
                if (photo.file.exists() && photo.file.delete()) {
                    // Si suppression du fichier réussit, notifier le système
                    context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(photo.file)))
                    
                    try {
                        // Supprimer l'entrée MediaStore
                        context.contentResolver.delete(photo.uri, null, null)
                    } catch (e: Exception) {
                        // Ignorer l'erreur MediaStore si la suppression de fichier a réussi
                        e.printStackTrace()
                    }
                    
                    success = true
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Méthode spécifique pour Android 8.0
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O && !success) {
                try {
                    // Essayer de supprimer via ContentResolver mais préserver l'URI
                    val uriBackup = photo.uri
                    val deletedRows = context.contentResolver.delete(photo.uri, null, null)
                    
                    if (deletedRows > 0) {
                        // Forcer une mise à jour du système
                        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uriBackup))
                        return true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            // Essai 3: Méthode standard MediaStore
            val deletedRows = context.contentResolver.delete(photo.uri, null, null)
            
            if (deletedRows > 0) {
                return true
            } else {
                // Message d'erreur pour debug
                Toast.makeText(context, "Échec de la suppression via MediaStore: URI=${photo.uri}", Toast.LENGTH_LONG).show()
                return false
            }
        } catch (e: Exception) {
            // Message d'erreur pour debug
            Toast.makeText(context, "Exception lors de la suppression: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Vérifie si l'application a les permissions nécessaires pour supprimer des fichiers
     * @param context Le contexte de l'application
     * @return true si les permissions sont accordées, false sinon
     */
    private fun checkDeletePermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(context, "L'accès à tous les fichiers est nécessaire pour supprimer", Toast.LENGTH_LONG).show()
                
                // Rediriger vers les paramètres
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = Uri.fromParts("package", context.packageName, null)
                intent.data = uri
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                context.startActivity(intent)
                return false
            }
        }
        else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "Permission d'écriture requise pour supprimer les fichiers", Toast.LENGTH_LONG).show()
                
                // Redirection vers paramètres pour Android 8.0
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.fromParts("package", context.packageName, null)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                return false
            }
        }
        
        return true
    }
    
    /**
     * Renomme une photo
     * @param context Le contexte de l'application
     * @param photo La photo à renommer
     * @param newName Le nouveau nom à appliquer
     * @param callback Le callback pour les événements de renommage
     * @return true si le renommage a été lancé, false sinon
     */
    fun renamePhoto(context: Context, photo: PhotoModel, newName: String, callback: PhotoRenamer.RenameCallback): Boolean {
        return PhotoRenamer.renamePhoto(context, photo, newName, callback)
    }
    
    /**
     * Vérifie si un nom de fichier existe déjà dans le dossier parent d'un fichier
     * @param currentPhotoPath Le chemin de la photo actuelle
     * @param newFullName Le nouveau nom complet à vérifier
     * @return true si le nom existe déjà, false sinon
     */
    fun checkIfNameExists(currentPhotoPath: String, newFullName: String): Boolean {
        val parentPath = FilePathUtils.getParentFolderPath(currentPhotoPath) ?: return false
        return FilePathUtils.fileNameExistsInFolder(parentPath, newFullName)
    }
    
    /**
     * Importe les photos du dossier DCIM/Camera vers un dossier spécifié
     * @param context Le contexte de l'application
     * @param destinationFolder Le dossier de destination
     * @param callback Le callback pour les événements d'import (optionnel)
     */
    suspend fun importPhotosFromCamera(
        context: Context, 
        destinationFolder: String,
        callback: ((imported: Int, skipped: Int) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val cameraFolderPath = FilePathUtils.getCameraFolderPath()
            val cameraFolder = File(cameraFolderPath)
            
            val workingDir = File(destinationFolder)
            if (!workingDir.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Le dossier de destination n'existe pas", Toast.LENGTH_SHORT).show()
                }
                return@withContext Pair(0, 0)
            }
            
            // Récupérer toutes les photos du dossier Camera
            val cameraPhotos = cameraFolder.listFiles { file -> 
                file.isFile && 
                FilePathUtils.isImageFile(file.name) && 
                !FilePathUtils.isTrashFile(file.name)
            } ?: emptyArray()
            
            if (cameraPhotos.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Aucune photo à importer", Toast.LENGTH_SHORT).show()
                }
                return@withContext Pair(0, 0)
            }
            
            var imported = 0
            var skipped = 0
            
            // Copier chaque photo dans le dossier de travail
            for (photo in cameraPhotos) {
                val destFile = File(workingDir, photo.name)
                
                if (destFile.exists()) {
                    // Si déjà existant, ajout d'un suffixe
                    val baseName = photo.name.substringBeforeLast(".", "")
                    val extension = photo.name.substringAfterLast(".", "")
                    val newName = "${baseName}_${System.currentTimeMillis()}.${extension}"
                    val destFileUnique = File(workingDir, newName)
                    
                    try {
                        // Conserver la date originale
                        val originalLastModified = photo.lastModified()
                        
                        // Copier avec gestion des métadonnées
                        val input: InputStream = photo.inputStream()
                        val output: OutputStream = destFileUnique.outputStream()
                        input.use { inStream -> 
                            output.use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        
                        // Restaurer la date
                        destFileUnique.setLastModified(originalLastModified)
                        
                        imported++
                    } catch (e: Exception) {
                        skipped++
                    }
                } else {
                    try {
                        // Conserver la date originale
                        val originalLastModified = photo.lastModified()
                        
                        // Copier avec gestion des métadonnées
                        val input: InputStream = photo.inputStream()
                        val output: OutputStream = destFile.outputStream()
                        input.use { inStream -> 
                            output.use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        
                        // Restaurer la date
                        destFile.setLastModified(originalLastModified)
                        
                        imported++
                    } catch (e: Exception) {
                        skipped++
                    }
                }
            }
            
            // Appeler le callback s'il est fourni
            callback?.invoke(imported, skipped)
            
            // Afficher un message récapitulatif
            withContext(Dispatchers.Main) {
                if (imported > 0) {
                    Toast.makeText(
                        context, 
                        "$imported photos importées${if (skipped > 0) ", $skipped ignorées" else ""}", 
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(context, "Aucune photo importée", Toast.LENGTH_SHORT).show()
                }
            }
            
            Pair(imported, skipped)
            
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            Pair(0, 0)
        }
    }
    
    /**
     * Déplace les photos d'un dossier vers un autre
     * @param context Le contexte de l'application
     * @param sourceFolder Le dossier source
     * @param destinationFolder Le dossier de destination
     * @param callback Le callback pour les événements de déplacement (optionnel)
     */
    suspend fun movePhotosFromFolder(
        context: Context, 
        sourceFolder: String, 
        destinationFolder: String,
        callback: ((moved: Int, skipped: Int, failed: Int) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        var moved = 0
        var skipped = 0
        var failed = 0
        
        try {
            val sourceDir = File(sourceFolder)
            val destinationDir = File(destinationFolder)
            
            if (!sourceDir.exists() || !destinationDir.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Dossier source ou destination invalide", Toast.LENGTH_SHORT).show()
                }
                return@withContext Triple(0, 0, 0)
            }
            
            // Récupérer la liste des photos du dossier source
            val sourcePhotos = sourceDir.listFiles { file -> 
                file.isFile && FilePathUtils.isImageFile(file.name) && !FilePathUtils.isTrashFile(file.name)
            } ?: emptyArray()
            
            // Pour chaque photo
            for (photo in sourcePhotos) {
                val destFile = File(destinationDir, photo.name)
                
                if (destFile.exists()) {
                    skipped++
                    continue
                }
                
                try {
                    // NOUVELLE APPROCHE: Essayer d'abord un renommage direct
                    var success = photo.renameTo(destFile)
                    
                    // Si le premier essai échoue, attendre un peu et réessayer
                    if (!success) {
                        delay(200) // Attendre pour libérer d'éventuels verrous
                        success = photo.renameTo(destFile)
                    }
                    
                    // Si le renommage direct réussit (meilleure préservation des métadonnées)
                    if (success) {
                        moved++
                        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(destFile)))
                        continue // Passer à la photo suivante
                    }
                    
                    // Si le renommage direct a échoué, fallback au copier-puis-supprimer
                    
                    // 1. Conserver l'horodatage et autres métadonnées importantes
                    val originalLastModified = photo.lastModified()
                    val originalLastAccessed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Files.getAttribute(photo.toPath(), "lastAccessTime") as? FileTime
                    } else null
                    val originalCreationTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Files.getAttribute(photo.toPath(), "creationTime") as? FileTime
                    } else null
                    
                    // 2. Copier le fichier
                    val input: InputStream = photo.inputStream()
                    val output: OutputStream = destFile.outputStream()
                    input.use { inStream -> 
                        output.use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    
                    // 3. Vérifier la copie et restaurer les métadonnées
                    if (destFile.exists() && destFile.length() == photo.length()) {
                        // Restaurer la date de dernière modification
                        destFile.setLastModified(originalLastModified)
                        
                        // Restaurer les autres métadonnées si disponibles (API 26+)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            try {
                                originalLastAccessed?.let {
                                    Files.setAttribute(destFile.toPath(), "lastAccessTime", it)
                                }
                                originalCreationTime?.let {
                                    Files.setAttribute(destFile.toPath(), "creationTime", it)
                                }
                            } catch (e: Exception) {
                                // Ignorer les erreurs de métadonnées
                            }
                        }
                        
                        // 4. Supprimer l'original
                        if (photo.delete()) {
                            moved++
                        } else {
                            moved++ // On considère comme déplacé car l'utilisateur a la copie
                        }
                        
                        // 5. Notifier MediaStore
                        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(destFile)))
                        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(photo)))
                    } else {
                        // Copie incomplète
                        if (destFile.exists()) destFile.delete()
                        failed++
                    }
                } catch (e: Exception) {
                    failed++
                    if (destFile.exists()) destFile.delete()
                }
            }
            
            // Appeler le callback s'il est fourni
            callback?.invoke(moved, skipped, failed)
            
            // Message de résultat
            withContext(Dispatchers.Main) {
                if (moved > 0) {
                    Toast.makeText(
                        context,
                        "$moved photos déplacées${if (skipped > 0) ", $skipped ignorées" else ""}${if (failed > 0) ", $failed échecs" else ""}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(context, "Aucune photo déplacée", Toast.LENGTH_SHORT).show()
                }
            }
            
            Triple(moved, skipped, failed)
            
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            Triple(0, 0, 0)
        }
    }
    
    /**
     * Obtient les photos du dossier DCIM/Camera
     * @param context Le contexte de l'application
     * @return La liste des photos trouvées dans le dossier DCIM/Camera
     */
    fun getCameraPhotos(context: Context): List<PhotoModel> {
        val photosList = mutableListOf<PhotoModel>()
        
        try {
            // Utilisation de MediaStore pour identifier spécifiquement les photos prises avec l'appareil
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA
            )
            
            val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf("%DCIM/Camera/%")
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val data = cursor.getString(dataColumn)
                    
                    // Ignorer les fichiers "trashed"
                    if (FilePathUtils.isTrashFile(name)) continue
                    
                    // Vérifier que le fichier existe réellement
                    val file = File(data)
                    if (!file.exists() || file.length() == 0L) continue
                    
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    
                    photosList.add(PhotoModel(
                        id = id.toString(),
                        uri = contentUri,
                        name = name,
                        path = data,
                        file = file
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return photosList
    }
}