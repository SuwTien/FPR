package fr.bdst.fastphotosrenamer.storage

import android.content.Context
import android.widget.Toast
import fr.bdst.fastphotosrenamer.utils.FilePathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gestionnaire centralisé pour les opérations liées aux dossiers
 */
object FolderManager {
    
    // États internes
    private val _availableFolders = MutableStateFlow<List<String>>(emptyList())
    val availableFolders: StateFlow<List<String>> = _availableFolders
    
    private val _shouldOpenFolderDropdown = MutableStateFlow(false)
    val shouldOpenFolderDropdown: StateFlow<Boolean> = _shouldOpenFolderDropdown
    
    /**
     * Charge la liste des dossiers disponibles
     * @param context Le contexte de l'application
     */
    suspend fun loadAvailableFolders(context: Context) = withContext(Dispatchers.IO) {
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
        if (cameraFolder.exists() && cameraFolder.isDirectory) {
            _availableFolders.value = listOf(cameraFolderPath) + sortedFolders.map { it.first }
        } else {
            _availableFolders.value = sortedFolders.map { it.first }
        }
        
        return@withContext _availableFolders.value
    }
    
    /**
     * Crée un nouveau dossier dans l'application
     * @param context Le contexte de l'application
     * @param folderName Le nom du dossier à créer
     * @return Le chemin du nouveau dossier créé ou null en cas d'échec
     */
    suspend fun createNewFolder(context: Context, folderName: String): String? = withContext(Dispatchers.IO) {
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
                return@withContext null
            }
            
            // Créer le dossier
            if (newFolder.mkdir()) {
                // Recharger la liste des dossiers
                loadAvailableFolders(context)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Dossier créé: $folderName", Toast.LENGTH_SHORT).show()
                }
                
                return@withContext newFolder.absolutePath
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Impossible de créer le dossier", Toast.LENGTH_SHORT).show()
                }
                return@withContext null
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return@withContext null
        }
    }
    
    /**
     * Vérifie si un dossier existe
     * @param folderPath Le chemin du dossier à vérifier
     * @return true si le dossier existe, false sinon
     */
    fun folderExists(folderPath: String): Boolean {
        val folder = File(folderPath)
        return folder.exists() && folder.isDirectory
    }
    
    /**
     * Obtient le dossier DCIM/Camera s'il existe, sinon retourne le dossier de l'application
     * @return Le chemin du dossier par défaut
     */
    fun getDefaultFolder(): String {
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
     * Définit l'état d'ouverture du menu déroulant
     * @param shouldOpen true pour ouvrir le menu déroulant, false pour le fermer
     */
    fun setShouldOpenFolderDropdown(shouldOpen: Boolean) {
        _shouldOpenFolderDropdown.value = shouldOpen
    }
    
    /**
     * Réinitialise l'état d'ouverture du menu déroulant (le ferme)
     */
    fun resetShouldOpenFolderDropdown() {
        _shouldOpenFolderDropdown.value = false
    }
}