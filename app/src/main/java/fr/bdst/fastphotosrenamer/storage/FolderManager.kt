package fr.bdst.fastphotosrenamer.storage

import android.content.Context
import fr.bdst.fastphotosrenamer.repository.interfaces.FolderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow

/**
 * Gestionnaire pour les dossiers de l'application
 */
class FolderManager private constructor(private val folderRepository: FolderRepository) {
    
    companion object {
        // Instance unique du singleton
        @Volatile
        private var INSTANCE: FolderManager? = null
        
        // Obtenir l'instance unique du FolderManager
        fun getInstance(folderRepository: FolderRepository): FolderManager {
            return INSTANCE ?: synchronized(this) {
                val instance = FolderManager(folderRepository)
                INSTANCE = instance
                instance
            }
        }
    }
    
    // État d'ouverture du menu déroulant des dossiers
    private val _shouldOpenFolderDropdown = MutableStateFlow(false)
    val shouldOpenFolderDropdown: StateFlow<Boolean> = _shouldOpenFolderDropdown
    
    /**
     * Retourne le dossier par défaut pour les photos
     */
    fun getDefaultFolder(): String {
        return folderRepository.getDefaultFolder()
    }
    
    /**
     * Charge la liste des dossiers disponibles pour stocker des photos
     */
    fun loadAvailableFolders(context: Context): Flow<List<String>> {
        return folderRepository.loadAvailableFolders(context)
    }
    
    /**
     * Crée un nouveau dossier pour stocker des photos
     */
    fun createNewFolder(context: Context, folderName: String): Flow<String?> {
        return folderRepository.createNewFolder(context, folderName)
    }
    
    /**
     * Vérifie si un dossier existe
     */
    fun folderExists(folderPath: String): Boolean {
        return folderRepository.folderExists(folderPath)
    }
    
    /**
     * Met à jour l'état d'ouverture du menu déroulant
     */
    fun setShouldOpenFolderDropdown(shouldOpen: Boolean) {
        _shouldOpenFolderDropdown.value = shouldOpen
    }
    
    /**
     * Réinitialise l'état d'ouverture du menu déroulant
     */
    fun resetShouldOpenFolderDropdown() {
        _shouldOpenFolderDropdown.value = false
    }
}