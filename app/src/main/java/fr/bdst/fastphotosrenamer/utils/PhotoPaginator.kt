package fr.bdst.fastphotosrenamer.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import fr.bdst.fastphotosrenamer.model.PhotoModel
import java.io.File

/**
 * Classe utilitaire pour gérer la pagination des photos
 */
object PhotoPaginator {

    const val PAGE_SIZE = 30 // Nombre de photos par page

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
}