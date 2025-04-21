package fr.bdst.fastphotosrenamer.di

import fr.bdst.fastphotosrenamer.repository.implementations.FileSystemFolderRepository
import fr.bdst.fastphotosrenamer.repository.implementations.MediaStorePhotoRepository
import fr.bdst.fastphotosrenamer.repository.interfaces.FolderRepository
import fr.bdst.fastphotosrenamer.repository.interfaces.PhotoRepository
import fr.bdst.fastphotosrenamer.storage.FolderManager
import fr.bdst.fastphotosrenamer.storage.PhotoManager

/**
 * Gestionnaire des dépendances de l'application
 * Fournit les instances des repositories et des managers
 */
object AppDependencies {
    
    // Repositories
    val photoRepository: PhotoRepository by lazy { MediaStorePhotoRepository() }
    val folderRepository: FolderRepository by lazy { FileSystemFolderRepository() }
    
    // Managers
    val photoManager: PhotoManager by lazy { PhotoManager.getInstance(photoRepository) }
    val folderManager: FolderManager by lazy { FolderManager.getInstance(folderRepository) }
    
}