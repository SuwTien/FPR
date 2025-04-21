package fr.bdst.fastphotosrenamer.repository.interfaces

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Interface définissant les opérations pour accéder et manipuler les dossiers
 */
interface FolderRepository {

    /**
     * Charge la liste des dossiers disponibles
     * @param context Le contexte de l'application
     * @return Un Flow émettant la liste des chemins de dossiers disponibles
     */
    fun loadAvailableFolders(
        context: Context
    ): Flow<List<String>>
    
    /**
     * Crée un nouveau dossier dans l'application
     * @param context Le contexte de l'application
     * @param folderName Le nom du dossier à créer
     * @return Un Flow émettant le chemin du nouveau dossier créé ou null en cas d'échec
     */
    fun createNewFolder(
        context: Context, 
        folderName: String
    ): Flow<String?>
    
    /**
     * Vérifie si un dossier existe
     * @param folderPath Le chemin du dossier à vérifier
     * @return true si le dossier existe, false sinon
     */
    fun folderExists(folderPath: String): Boolean
    
    /**
     * Obtient le dossier DCIM/Camera s'il existe, sinon retourne le dossier de l'application
     * @return Le chemin du dossier par défaut
     */
    fun getDefaultFolder(): String
    
    /**
     * Obtient des informations sur un dossier
     * @param folderPath Le chemin du dossier
     * @return Un Flow émettant les informations du dossier
     */
    fun getFolderInfo(folderPath: String): Flow<FolderInfo>
    
    /**
     * Liste les sous-dossiers d'un dossier parent
     * @param parentFolderPath Le chemin du dossier parent
     * @return Un Flow émettant la liste des sous-dossiers
     */
    fun getSubFolders(parentFolderPath: String): Flow<List<String>>
    
    /**
     * Classe data pour représenter les informations d'un dossier
     */
    data class FolderInfo(
        val path: String,
        val name: String,
        val photosCount: Int,
        val lastModified: Long,
        val isWritable: Boolean,
        val hasSubfolders: Boolean
    )
}