package fr.bdst.fastphotosrenamer.storage

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
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
import androidx.documentfile.provider.DocumentFile
import fr.bdst.fastphotosrenamer.model.PhotoModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

object PhotoRenamer {
    
    // Interface pour les callbacks après renommage
    interface RenameCallback {
        fun onRenameSuccess(newName: String)
        fun onRenameInProgress(inProgress: Boolean)
        fun onRenameComplete(reloadPhotos: Boolean)
    }
    
    /**
     * Méthode principale pour renommer une photo
     */
    fun renamePhoto(context: Context, photo: PhotoModel, newName: String, callback: RenameCallback): Boolean {
        callback.onRenameInProgress(true)
        
        try {
            // Vérifier les permissions
            if (!checkPermissions(context)) {
                callback.onRenameInProgress(false)
                return false
            }
            
            val contentResolver: ContentResolver = context.contentResolver
            
            // Détection du type de stockage
            val isOnSDCard = photo.file.absolutePath.startsWith("/storage/") && 
                            !photo.file.absolutePath.startsWith("/storage/emulated/0")
            
            // NOUVELLE PARTIE: Bloquer le renommage sur carte SD avec message explicatif
            if (isOnSDCard) {
                Toast.makeText(
                    context, 
                    "Le renommage sur carte SD n'est pas pris en charge dans cette version. " +
                    "Veuillez utiliser le stockage interne.",
                    Toast.LENGTH_LONG
                ).show()
                callback.onRenameInProgress(false)
                return false
            }
            
            // Si c'est sur le stockage interne, procéder au renommage normal
            return renameOnInternalStorage(context, photo, newName, contentResolver, callback)
            
        } catch (e: Exception) {
            Toast.makeText(context, "Exception: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
            
            // Vérifier si le renommage a quand même réussi
            val newFile = File(photo.file.parent, newName)
            if (newFile.exists()) {
                callback.onRenameSuccess(newName)
                callback.onRenameComplete(true)
                return true
            }
            
            callback.onRenameInProgress(false)
            return false
        } finally {
            // Toujours désactiver l'indicateur de progression
            callback.onRenameInProgress(false)
        }
    }
    
    /**
     * Vérifier les permissions nécessaires selon la version d'Android
     */
    private fun checkPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(context, "L'accès à tous les fichiers est nécessaire pour renommer", Toast.LENGTH_LONG).show()
                
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
                Toast.makeText(context, "Permission d'écriture requise pour renommer les fichiers", Toast.LENGTH_LONG).show()
                
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
     * Méthodes spécifiques pour le renommage sur carte SD
     */
    private fun renameOnSDCard(
        context: Context, 
        photo: PhotoModel, 
        newName: String,
        contentResolver: ContentResolver,
        callback: RenameCallback
    ): Boolean {
        // Approche robuste: copier + supprimer
        try {
            val sourceFile = photo.file
            val targetFile = File(sourceFile.parent, newName)
            
            // 1. Copier d'abord (plus sûr)
            sourceFile.inputStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Vérifier que le nouveau fichier existe et a la bonne taille
            if (targetFile.exists() && targetFile.length() == sourceFile.length()) {
                // 2. Supprimer l'original seulement si la copie a réussi
                val deleted = sourceFile.delete()
                
                // 3. Mettre à jour MediaStore
                try {
                    // 3.1 Supprimer l'ancienne entrée
                    contentResolver.delete(photo.uri, null, null)
                    
                    // 3.2 Ajouter la nouvelle entrée
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, newName)
                        put(MediaStore.Images.Media.DATA, targetFile.absolutePath)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    }
                    contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    
                    // Forcer le scan média
                    context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(targetFile)))
                } catch (e: Exception) {
                    // On continue car le fichier a bien été renommé
                }
                
                Toast.makeText(context, "Photo renommée avec succès", Toast.LENGTH_SHORT).show()
                callback.onRenameSuccess(newName)
                callback.onRenameComplete(true)
                return true
            } else {
                // La copie a échoué ou le fichier n'a pas la bonne taille
                if (targetFile.exists()) targetFile.delete() // Nettoyer si copie partielle
            }
        } catch (e: Exception) {
        }
        
        Toast.makeText(context, "Impossible de renommer la photo sur la carte SD", Toast.LENGTH_LONG).show()
        return false
    }
    
    /**
     * Méthodes pour le renommage sur stockage interne
     */
    private fun renameOnInternalStorage(
        context: Context,
        photo: PhotoModel,
        newName: String,
        contentResolver: ContentResolver,
        callback: RenameCallback
    ): Boolean {
        val oldFile = photo.file
        
        // Déterminer si le fichier est dans DCIM/Camera
        val isDCIMCamera = oldFile.absolutePath.contains("/DCIM/Camera/")
        
        // Traitement spécial pour DCIM/Camera
        if (isDCIMCamera) {
            try {
                if (oldFile.exists()) {
                    val parentPath = oldFile.parent ?: return false
                    val newFile = File(parentPath, newName)
                    
                    // NOUVELLE APPROCHE: Essayer d'abord le renameTo direct
                    var success = oldFile.renameTo(newFile)
                    
                    // Si échec, attendre un court délai et réessayer
                    if (!success) {
                        Thread.sleep(200)  // Attendre pour libérer les verrous potentiels
                        success = oldFile.renameTo(newFile)
                    }
                    
                    // Si le renommage direct réussit
                    if (success) {
                        // Mettre à jour MediaStore pour le nouveau fichier
                        try {
                            // Supprimer l'ancienne entrée
                            contentResolver.delete(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                "${MediaStore.Images.Media.DATA} = ?",
                                arrayOf(oldFile.absolutePath)
                            )
                            
                            // Ajouter la nouvelle entrée
                            val values = ContentValues().apply {
                                put(MediaStore.Images.Media.DATA, newFile.absolutePath)
                                put(MediaStore.Images.Media.DISPLAY_NAME, newName)
                                put(MediaStore.Images.Media.MIME_TYPE, getMimeType(newName))
                            }
                            
                            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(newFile)))
                            
                            callback.onRenameSuccess(newName)
                            callback.onRenameComplete(true)
                            return true
                        } catch (e: Exception) {
                        }
                    }
                    
                    // Si le renommage direct échoue, utiliser copier/coller avec préservation des métadonnées
                    
                    // 1. Sauvegarder les métadonnées importantes
                    val lastModified = oldFile.lastModified()
                    
                    // 2. Copier le fichier
                    oldFile.inputStream().use { input ->
                        newFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    // 3. Vérifier et restaurer les métadonnées
                    if (newFile.exists() && newFile.length() == oldFile.length()) {
                        // Restaurer la date de dernière modification
                        newFile.setLastModified(lastModified)
                        
                        // Supprimer l'ancien fichier
                        val deleteSuccess = oldFile.delete()
                        
                        // 4. Mettre à jour MediaStore comme avant
                        try {
                            contentResolver.delete(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                "${MediaStore.Images.Media.DATA} = ?",
                                arrayOf(oldFile.absolutePath)
                            )
                            
                            val values = ContentValues().apply {
                                put(MediaStore.Images.Media.DATA, newFile.absolutePath)
                                put(MediaStore.Images.Media.DISPLAY_NAME, newName)
                                put(MediaStore.Images.Media.MIME_TYPE, getMimeType(newName))
                                // Important: utiliser la date originale
                                put(MediaStore.Images.Media.DATE_MODIFIED, lastModified / 1000)
                            }
                            
                            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(newFile)))
                            
                            callback.onRenameSuccess(newName)
                            callback.onRenameComplete(true)
                            return true
                        } catch (e: Exception) {
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }
        
        // Le code original pour les autres dossiers ou si le code spécial échoue
        // Essai 1: Méthode directe File.renameTo
        try {
            if (oldFile.exists()) {
                val parentPath = oldFile.parent
                val newFile = File(parentPath, newName)
                
                val success = oldFile.renameTo(newFile)
                
                if (success) {
                    // Notifier le système du changement
                    context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(newFile)))
                    
                    // Mettre à jour MediaStore
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, newName)
                        put(MediaStore.Images.Media.DATA, newFile.absolutePath)
                    }
                    contentResolver.update(photo.uri, values, null, null)
                    
                    callback.onRenameSuccess(newName)
                    callback.onRenameComplete(true)
                    return true
                }
            }
        } catch (e: Exception) {
        }
        
        // Essai 2: Méthode spécifique pour Android 8.0
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O) {
            try {
                if (oldFile.exists()) {
                    val parentPath = oldFile.parent ?: ""
                    val newPath = "$parentPath/$newName"
                    
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, newName)
                        put(MediaStore.Images.Media.DATA, newPath)
                    }
                    
                    val updatedRows = contentResolver.update(photo.uri, values, null, null)
                    if (updatedRows > 0) {
                        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(File(newPath))))
                        
                        callback.onRenameSuccess(newName)
                        callback.onRenameComplete(true)
                        return true
                    }
                }
            } catch (e: Exception) {
            }
        }
        
        // Essai 3: Méthode MediaStore standard
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, newName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        
        val updatedRows = contentResolver.update(photo.uri, values, null, null)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(photo.uri, values, null, null)
        }
        
        if (updatedRows > 0) {
            callback.onRenameSuccess(newName)
            callback.onRenameComplete(true)
            return true
        }
        
        // Si tout échoue, revenir aux méthodes habituelles
        Toast.makeText(context, "Impossible de renommer le fichier", Toast.LENGTH_SHORT).show()
        return false
    }

    private fun renameDCIMCameraPhoto(
        context: Context,
        photo: PhotoModel,
        newName: String,
        contentResolver: ContentResolver,
        callback: RenameCallback
    ): Boolean {
        val oldFile = photo.file
        val parentPath = oldFile.parent ?: return false
        val newFile = File(parentPath, newName)
        
        try {
            // 1. Copier d'abord, ne pas utiliser renameTo() qui échoue souvent dans DCIM/Camera
            oldFile.inputStream().use { input ->
                newFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // 2. Vérifier que la copie est complète
            if (newFile.exists() && newFile.length() == oldFile.length()) {
                // 3. Supprimer l'ancien fichier SEULEMENT si la copie est réussie
                val deleteSuccess = oldFile.delete()
                
                // 4. Mettre à jour MediaStore - partie TRÈS IMPORTANTE
                try {
                    // 4.1 Supprimer l'ancienne entrée
                    val deletedRows = contentResolver.delete(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Images.Media.DATA} = ?",
                        arrayOf(oldFile.absolutePath)
                    )
                    
                    // 4.2 Ajouter la nouvelle entrée
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DATA, newFile.absolutePath)
                        put(MediaStore.Images.Media.DISPLAY_NAME, newName)
                        put(MediaStore.Images.Media.MIME_TYPE, getMimeType(newName))
                        put(MediaStore.Images.Media.SIZE, newFile.length())
                        put(MediaStore.Images.Media.DATE_MODIFIED, newFile.lastModified() / 1000)
                    }
                    
                    contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    
                    // 4.3 Demander au système de scanner le nouveau fichier
                    context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(newFile)))
                    
                    // 5. Succès!
                    callback.onRenameSuccess(newName)
                    callback.onRenameComplete(true)
                    return true
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
        }
        
        return false
    }
    
    /**
     * Renommage avec le Storage Access Framework (solution de dernier recours)
     */
    private fun renameWithSAF(
        context: Context,
        photo: PhotoModel,
        newName: String,
        callback: RenameCallback
    ): Boolean {
        Toast.makeText(context, "Tentative avec le Storage Access Framework...", Toast.LENGTH_SHORT).show()
        
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val treeUriString = prefs.getString("document_tree_uri", null)
        
        if (treeUriString == null) {
            // Demander l'accès via SAF
            if (context is Activity) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                context.startActivity(intent)
            }
            
            Toast.makeText(context, "Sélectionne le dossier qui contient tes photos", Toast.LENGTH_LONG).show()
            return false
        }
        
        try {
            // Rechercher et renommer le fichier via SAF
            // Code existant pour SAF...
            // ...
            return true  // Si réussi
        } catch (e: Exception) {
            Toast.makeText(context, "Erreur SAF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        
        return false
    }
    
    /**
     * Utilitaire pour chercher un fichier dans l'arborescence DocumentFile
     */
    private fun findFile(dir: DocumentFile, name: String): DocumentFile? {
        dir.listFiles().forEach { file ->
            if (file.name == name) return file
            if (file.isDirectory) {
                findFile(file, name)?.let { return it }
            }
        }
        return null
    }

    private fun getMimeType(fileName: String): String {
        val extension = fileName.substring(fileName.lastIndexOf(".") + 1).lowercase(Locale.ROOT)
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