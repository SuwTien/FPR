package fr.bdst.fastphotosrenamer.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.bdst.fastphotosrenamer.storage.FolderManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel pour la gestion des dossiers
 */
class FolderViewModel(private val folderManager: FolderManager) : ViewModel() {

    // État de la liste des dossiers disponibles
    private val _availableFolders = MutableStateFlow<List<String>>(emptyList())
    val availableFolders: StateFlow<List<String>> = _availableFolders

    // État du dossier sélectionné
    private val _selectedFolder = MutableStateFlow("")
    val selectedFolder: StateFlow<String> = _selectedFolder

    // État d'erreur
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // État d'ouverture du menu déroulant
    val shouldOpenFolderDropdown = folderManager.shouldOpenFolderDropdown

    /**
     * Initialise le ViewModel
     */
    init {
        // Définir le dossier par défaut
        _selectedFolder.value = folderManager.getDefaultFolder()
    }

    /**
     * Charge la liste des dossiers disponibles
     */
    fun loadAvailableFolders(context: Context) {
        viewModelScope.launch {
            try {
                folderManager.loadAvailableFolders(context)
                    .catch { e ->
                        _error.value = "Erreur de chargement des dossiers: ${e.message}"
                    }
                    .collectLatest { folders ->
                        _availableFolders.value = folders
                        
                        // Si aucun dossier n'est sélectionné mais que des dossiers sont disponibles
                        if (_selectedFolder.value.isEmpty() && folders.isNotEmpty()) {
                            _selectedFolder.value = folders[0]
                        }
                    }
            } catch (e: Exception) {
                _error.value = "Exception: ${e.message}"
            }
        }
    }

    /**
     * Définit le dossier sélectionné
     */
    fun setSelectedFolder(folderPath: String) {
        _selectedFolder.value = folderPath
    }

    /**
     * Crée un nouveau dossier
     */
    fun createNewFolder(context: Context, folderName: String, onComplete: (String?) -> Unit) {
        if (folderName.isEmpty()) {
            _error.value = "Le nom du dossier ne peut pas être vide"
            onComplete(null)
            return
        }
        
        viewModelScope.launch {
            try {
                folderManager.createNewFolder(context, folderName)
                    .catch { e ->
                        _error.value = "Erreur lors de la création du dossier: ${e.message}"
                        onComplete(null)
                    }
                    .collectLatest { newFolderPath ->
                        if (newFolderPath != null) {
                            // Sélectionner automatiquement le nouveau dossier
                            _selectedFolder.value = newFolderPath
                            onComplete(newFolderPath)
                        } else {
                            _error.value = "Impossible de créer le dossier"
                            onComplete(null)
                        }
                    }
            } catch (e: Exception) {
                _error.value = "Exception: ${e.message}"
                onComplete(null)
            }
        }
    }

    /**
     * Définit l'état d'ouverture du menu déroulant
     */
    fun setShouldOpenFolderDropdown(shouldOpen: Boolean) {
        folderManager.setShouldOpenFolderDropdown(shouldOpen)
    }

    /**
     * Réinitialise l'état du menu déroulant
     */
    fun resetShouldOpenFolderDropdown() {
        folderManager.resetShouldOpenFolderDropdown()
    }

    /**
     * Réinitialise le message d'erreur
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Factory pour la création du ViewModel
     */
    class Factory(private val folderManager: FolderManager) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FolderViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return FolderViewModel(folderManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}