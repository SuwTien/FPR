package fr.bdst.fastphotosrenamer.repository.implementations

import android.content.Context
import android.widget.Toast
import fr.bdst.fastphotosrenamer.repository.interfaces.FolderRepository
import fr.bdst.fastphotosrenamer.utils.FilePathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Implémentation concrète de FolderRepository utilisant le système de fichiers comme source de données
 */
class FileSystemFolderRepository : FolderRepository {

    /**
     * Charge la liste des dossiers disponibles
     */
    override fun loadAvailableFolders(context: Context): Flow<List<String>> = flow {
        val folders = mutableListOf<String>()
        
        // 1. Ajouter DCIM/Camera en premier (dossier par défaut)
        val cameraFolderPath = FilePathUtils.getCameraFolderPath()
        val cameraFolder = File(cameraFolderPath)
        if (cameraFolder.exists() && cameraFolder.isDirectory) {
            folders.add(cameraFolderPath)
        }
        
        // 2. Ajouter les sous-dossiers de FPR
        val appFolderPath = FilePathUtils.getAppFolderPath()
        val appFolder = File(appFolderPath)
        if (!appFolder.exists()) {
            appFolder.mkdir()
        }
        
        // Collecte tous les sous-dossiers avec leur timestamp de dernière modification
        val foldersWithTimestamp = mutableListOf<Pair<String, Long>>()
        
        appFolder.listFiles()?.filter { it.isDirectory }?.forEach {
            foldersWithTimestamp.add(Pair(it.absolutePath, it.lastModified()))
        }
        
        // Tri des dossiers par date de dernière modification (du plus récent au plus ancien)
        val sortedFolders = foldersWithTimestamp.sortedByDescending { it.second }
        
        // Ajouter DCIM/Camera en premier, puis les autres dossiers triés
        val result = if (cameraFolder.exists() && cameraFolder.isDirectory) {
            listOf(cameraFolderPath) + sortedFolders.map { it.first }
        } else {
            sortedFolders.map { it.first }
        }
        
        emit(result)
    }.flowOn(Dispatchers.IO)
    
    /**
     * Crée un nouveau dossier dans l'application
     */
    override fun createNewFolder(context: Context, folderName: String): Flow<String?> = flow {
        try {
            // Toujours créer dans DCIM/FPR
            val appFolderPath = FilePathUtils.getAppFolderPath()
            val appFolder = File(appFolderPath)
            
            // S'assurer que le dossier parent existe
            if (!appFolder.exists()) {
                appFolder.mkdir()
            }
            
            // Créer le nouveau dossier dans FPR
            val newFolder = File(appFolder, folderName)
            
            // Vérifier si le dossier existe déjà
            if (newFolder.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Ce dossier existe déjà", Toast.LENGTH_SHORT).show()
                }
                emit(null)
                return@flow
            }
            
            // Créer le dossier
            if (newFolder.mkdir()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Dossier créé: $folderName", Toast.LENGTH_SHORT).show()
                }
                
                emit(newFolder.absolutePath)
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Impossible de créer le dossier", Toast.LENGTH_SHORT).show()
                }
                emit(null)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            emit(null)
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Vérifie si un dossier existe
     */
    override fun folderExists(folderPath: String): Boolean {
        val folder = File(folderPath)
        return folder.exists() && folder.isDirectory
    }
    
    /**
     * Obtient le dossier DCIM/Camera s'il existe, sinon retourne le dossier de l'application
     */
    override fun getDefaultFolder(): String {
        val cameraFolderPath = FilePathUtils.getCameraFolderPath()
        val cameraFolder = File(cameraFolderPath)
        
        if (cameraFolder.exists() && cameraFolder.isDirectory) {
            return cameraFolderPath
        }
        
        val appFolderPath = FilePathUtils.getAppFolderPath()
        val appFolder = File(appFolderPath)
        if (!appFolder.exists()) {
            appFolder.mkdir()
        }
        
        return appFolderPath
    }
    
    /**
     * Obtient des informations sur un dossier
     */
    override fun getFolderInfo(folderPath: String): Flow<FolderRepository.FolderInfo> = flow {
        val folder = File(folderPath)
        
        if (!folder.exists() || !folder.isDirectory) {
            throw IllegalArgumentException("Le chemin ne correspond pas à un dossier valide")
        }
        
        // Compter les photos dans le dossier
        val photosCount = folder.listFiles { file ->
            file.isFile && FilePathUtils.isImageFile(file.name) && !FilePathUtils.isTrashFile(file.name)
        }?.size ?: 0
        
        // Vérifier s'il y a des sous-dossiers
        val hasSubfolders = folder.listFiles { file -> file.isDirectory }?.isNotEmpty() ?: false
        
        // Créer et émettre l'objet FolderInfo
        val folderInfo = FolderRepository.FolderInfo(
            path = folder.absolutePath,
            name = folder.name,
            photosCount = photosCount,
            lastModified = folder.lastModified(),
            isWritable = folder.canWrite(),
            hasSubfolders = hasSubfolders
        )
        
        emit(folderInfo)
    }.flowOn(Dispatchers.IO)
    
    /**
     * Liste les sous-dossiers d'un dossier parent
     */
    override fun getSubFolders(parentFolderPath: String): Flow<List<String>> = flow {
        val parentFolder = File(parentFolderPath)
        val subFolders = mutableListOf<String>()
        
        if (parentFolder.exists() && parentFolder.isDirectory) {
            parentFolder.listFiles { file -> file.isDirectory }?.forEach {
                subFolders.add(it.absolutePath)
            }
        }
        
        emit(subFolders)
    }.flowOn(Dispatchers.IO)
}