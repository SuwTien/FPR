package fr.bdst.fastphotosrenamer.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import fr.bdst.fastphotosrenamer.model.PhotoModel
import java.io.File

/**
 * Classe utilitaire pour charger les photos par pages pour de meilleures performances
 */
class PhotoPaginator {
    companion object {
        const val PAGE_SIZE = 30 // Nombre de photos par page

        /**
         * Charge une page de photos d'un dossier spécifique
         * @param context Le contexte de l'application
         * @param folderPath Le chemin du dossier
         * @param offset L'index de départ pour le chargement
         * @param limit Le nombre maximum de photos à charger
         * @return Une paire contenant la liste des photos chargées et un booléen indiquant s'il y a plus de photos
         */
        fun loadPhotosPageFromFolder(
            context: Context,
            folderPath: String,
            offset: Int,
            limit: Int
        ): Pair<List<PhotoModel>, Boolean> {
            val photosList = mutableListOf<PhotoModel>()
            val folder = File(folderPath)
            
            if (!folder.exists() || !folder.isDirectory) {
                return Pair(emptyList(), false)
            }
            
            // Améliorer la détection du dossier DCIM/Camera pour être plus robuste
            // en normalisant le chemin et en vérifiant la présence de DCIM et Camera indépendamment du séparateur
            val normalizedPath = folderPath.replace('\\', '/') // Normaliser les backslashes en forward slashes
            val isCameraFolder = normalizedPath.contains("/DCIM/Camera") || 
                                normalizedPath.endsWith("/DCIM/Camera")
            
            android.util.Log.d("FPR_DEBUG", "Chargement dossier: $folderPath, est dossier Camera: $isCameraFolder")
            
            if (isCameraFolder) {
                // Pour DCIM/Camera, utilisons l'approche directe par système de fichiers
                // car elle a prouvé être plus fiable dans le passé
                val cameraDirPhotos = loadCameraPhotosDirectly(folder, offset, limit)
                
                // Si l'approche directe échoue ou ne retourne pas de photos, essayer MediaStore comme fallback
                if (cameraDirPhotos.first.isEmpty()) {
                    android.util.Log.d("FPR_DEBUG", "Fallback à MediaStore pour DCIM/Camera")
                    return loadCameraPhotosPage(context, offset, limit)
                }
                
                return cameraDirPhotos
            } else {
                // Pour les autres dossiers, utiliser le système de fichiers
                val allFiles = folder.listFiles()?.filter { file ->
                    file.isFile && isImageFile(file.name) && !isTrashFile(file.name)
                }?.sortedByDescending { it.lastModified() } ?: emptyList()
                
                // Vérifier s'il y a plus de photos
                val hasMorePhotos = allFiles.size > offset + limit
                
                // Prendre seulement la page demandée
                val pagedFiles = allFiles
                    .drop(offset)
                    .take(limit)
                
                // Convertir les fichiers en modèles de photos
                for (file in pagedFiles) {
                    try {
                        photosList.add(
                            PhotoModel(
                                id = file.absolutePath.hashCode().toString(),
                                uri = Uri.fromFile(file),
                                name = file.name,
                                path = file.absolutePath,
                                file = file
                            )
                        )
                    } catch (e: Exception) {
                        // Ignorer les fichiers problématiques
                    }
                }
                
                return Pair(photosList, hasMorePhotos)
            }
        }
        
        /**
         * Charge les photos du dossier Camera directement via le système de fichiers
         */
        private fun loadCameraPhotosDirectly(
            cameraFolder: File,
            offset: Int,
            limit: Int
        ): Pair<List<PhotoModel>, Boolean> {
            val photosList = mutableListOf<PhotoModel>()
            
            try {
                val allFiles = cameraFolder.listFiles()?.filter { file -> 
                    file.isFile && 
                    isImageFile(file.name) &&
                    !isTrashFile(file.name) &&
                    file.length() > 0  // Ignorer les fichiers vides
                }?.sortedByDescending { it.lastModified() } ?: emptyList()
                
                // Vérifier s'il y a plus de photos
                val hasMorePhotos = allFiles.size > offset + limit
                
                // Prendre seulement la page demandée
                val pagedFiles = allFiles
                    .drop(offset)
                    .take(limit)
                
                android.util.Log.d("FPR_DEBUG", "DCIM/Camera contient ${allFiles.size} photos, chargement de ${pagedFiles.size}")
                
                // Convertir les fichiers en modèles de photos
                for (file in pagedFiles) {
                    try {
                        photosList.add(
                            PhotoModel(
                                id = file.absolutePath.hashCode().toString(),
                                uri = Uri.fromFile(file),
                                name = file.name,
                                path = file.absolutePath,
                                file = file
                            )
                        )
                    } catch (e: Exception) {
                        // Ignorer les fichiers problématiques
                    }
                }
                
                return Pair(photosList, hasMorePhotos)
            } catch (e: Exception) {
                android.util.Log.e("FPR_DEBUG", "Erreur chargement direct Camera: ${e.message}")
                return Pair(emptyList(), false)
            }
        }
        
        /**
         * Charge une page de photos du dossier Camera en utilisant MediaStore
         */
        private fun loadCameraPhotosPage(
            context: Context,
            offset: Int,
            limit: Int
        ): Pair<List<PhotoModel>, Boolean> {
            val photosList = mutableListOf<PhotoModel>()
            
            try {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATA
                )
                
                // Améliorer la sélection pour être plus précise
                val selection = "${MediaStore.Images.Media.DATA} LIKE ? AND ${MediaStore.Images.Media.DATA} NOT LIKE ?"
                val selectionArgs = arrayOf("%/DCIM/Camera/%", "%/DCIM/Camera/%/%") // Ne pas inclure les sous-dossiers
                val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC LIMIT $limit OFFSET $offset"
                
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
                        if (isTrashFile(name)) continue
                        
                        // Vérifier que le fichier existe réellement
                        val file = File(data)
                        if (!file.exists() || file.length() == 0L) continue
                        
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        
                        photosList.add(
                            PhotoModel(
                                id = id.toString(),
                                uri = contentUri,
                                name = name,
                                path = data,
                                file = file
                            )
                        )
                    }
                    
                    android.util.Log.d("FPR_DEBUG", "MediaStore a trouvé ${photosList.size} photos dans DCIM/Camera")
                    
                    // Vérifier s'il y a plus de photos en comptant le total
                    val countCursor = context.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        arrayOf("count(*) AS count"),
                        selection,
                        selectionArgs,
                        null
                    )
                    
                    val totalCount = countCursor?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getInt(0)
                        } else {
                            0
                        }
                    } ?: 0
                    
                    countCursor?.close()
                    
                    // Déterminer s'il y a plus de photos
                    val hasMorePhotos = totalCount > offset + photosList.size
                    
                    return Pair(photosList, hasMorePhotos)
                }
                
                // Si la requête a échoué
                return Pair(emptyList(), false)
                
            } catch (e: Exception) {
                android.util.Log.e("FPR_DEBUG", "Erreur MediaStore: ${e.message}")
                return Pair(emptyList(), false)
            }
        }

        /**
         * Vérifie si un fichier est une image basé sur son extension
         */
        private fun isImageFile(fileName: String): Boolean {
            val extension = fileName.substringAfterLast(".", "").lowercase()
            return extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        }
        
        /**
         * Vérifie si un fichier est dans la corbeille ou est un fichier temporaire
         */
        private fun isTrashFile(fileName: String): Boolean {
            val lowerName = fileName.lowercase()
            return lowerName.startsWith("trashed-") ||
                   lowerName.startsWith(".trashed") ||
                   lowerName.contains("trash") ||
                   lowerName.contains("deleted") ||
                   lowerName.startsWith(".") ||  // Fichiers cachés
                   lowerName.contains("~") ||    // Fichiers temporaires
                   lowerName.endsWith(".tmp")    // Autres fichiers temporaires
        }
    }
}