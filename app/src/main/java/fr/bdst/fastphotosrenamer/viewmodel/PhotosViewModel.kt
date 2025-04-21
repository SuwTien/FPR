package fr.bdst.fastphotosrenamer.viewmodel

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.bdst.fastphotosrenamer.model.PhotoModel
import fr.bdst.fastphotosrenamer.utils.FilePathUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import android.Manifest
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import fr.bdst.fastphotosrenamer.storage.PhotoRenamer
import android.content.ContentUris
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.io.InputStream
import java.io.OutputStream

class PhotosViewModel : ViewModel() {
    
    private val _photos = MutableStateFlow<List<PhotoModel>>(emptyList())
    val photos: StateFlow<List<PhotoModel>> = _photos
    
    private val _selectedPhoto = MutableStateFlow<PhotoModel?>(null)
    val selectedPhoto: StateFlow<PhotoModel?> = _selectedPhoto

    private val _isRenameOperationInProgress = MutableStateFlow(false)
    val isRenameOperationInProgress: StateFlow<Boolean> = _isRenameOperationInProgress

    // Variables d'état pour les dossiers
    private val _currentFolder = MutableStateFlow<String>("")
    val currentFolder: StateFlow<String> = _currentFolder

    private val _availableFolders = MutableStateFlow<List<String>>(emptyList())
    val availableFolders: StateFlow<List<String>> = _availableFolders

    // Nouvel état pour contrôler l'ouverture du menu déroulant des dossiers
    private val _shouldOpenFolderDropdown = MutableStateFlow(false)
    val shouldOpenFolderDropdown: StateFlow<Boolean> = _shouldOpenFolderDropdown

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    // Nouveaux états pour le mode plein écran
    private val _fullscreenMode = MutableStateFlow(false)
    val fullscreenMode: StateFlow<Boolean> = _fullscreenMode
    
    // Index de la photo actuellement affichée en mode plein écran
    private val _fullscreenPhotoIndex = MutableStateFlow(0)
    val fullscreenPhotoIndex: StateFlow<Int> = _fullscreenPhotoIndex
    
    // État pour suivre si on était en mode plein écran avant d'aller sur l'écran de renommage
    private val _wasInFullscreenMode = MutableStateFlow(false)
    val wasInFullscreenMode: Boolean
        get() = _wasInFullscreenMode.value

    // Nouveaux états pour la pagination
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage

    private val _hasMorePhotos = MutableStateFlow(false)
    val hasMorePhotos: StateFlow<Boolean> = _hasMorePhotos
    
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    // Initialiser le dossier courant dans init {}
    init {
        val cameraFolderPath = FilePathUtils.getCameraFolderPath()
        val cameraFolder = File(cameraFolderPath)
        
        if (cameraFolder.exists() && cameraFolder.isDirectory) {
            _currentFolder.value = cameraFolderPath
        } else {
            // Fallback au dossier FPR si Camera n'existe pas
            val appFolderPath = FilePathUtils.getAppFolderPath()
            val appFolder = File(appFolderPath)
            _currentFolder.value = if (appFolder.exists()) appFolderPath 
                                   else Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath
        }
    }

    // Méthode pour charger les photos d'un dossier avec pagination
    fun loadPhotosFromFolderPaginated(context: Context, folderPath: String, reset: Boolean = true) {
        viewModelScope.launch {
            if (reset) {
                _isLoading.value = true
                _currentPage.value = 0
                _photos.value = emptyList()
            } else {
                _isLoadingMore.value = true
            }
            
            try {
                // Sauvegarder l'index de la photo en plein écran
                val savedFullscreenIndex = _fullscreenPhotoIndex.value
                
                // Calculer l'offset pour le chargement paginé
                val offset = _currentPage.value * fr.bdst.fastphotosrenamer.utils.PhotoPaginator.PAGE_SIZE
                
                // Charger une page de photos
                val (newPhotos, hasMore) = fr.bdst.fastphotosrenamer.utils.PhotoPaginator.loadPhotosPageFromFolder(
                    context,
                    folderPath,
                    offset,
                    fr.bdst.fastphotosrenamer.utils.PhotoPaginator.PAGE_SIZE
                )
                
                // Mettre à jour l'état
                if (reset) {
                    _photos.value = newPhotos
                } else {
                    _photos.value = _photos.value + newPhotos
                }
                
                _hasMorePhotos.value = hasMore
                
                // Incrémenter la page pour le prochain chargement
                if (hasMore) {
                    _currentPage.value = _currentPage.value + 1
                }
                
                // Restaurer l'index en plein écran si nécessaire
                if (_fullscreenMode.value && savedFullscreenIndex < _photos.value.size) {
                    _fullscreenPhotoIndex.value = savedFullscreenIndex
                }
            } catch (e: Exception) {
                // En cas d'erreur, s'assurer qu'on a au moins une liste vide
                if (reset) {
                    _photos.value = emptyList()
                }
                android.util.Log.e("FPR_DEBUG", "Erreur de chargement: ${e.message}")
            } finally {
                if (reset) {
                    _isLoading.value = false
                } else {
                    _isLoadingMore.value = false
                }
            }
        }
    }
    
    // Méthode pour charger la page suivante
    fun loadMorePhotos(context: Context) {
        if (_isLoadingMore.value || !_hasMorePhotos.value) {
            return // Ne rien faire si déjà en train de charger ou s'il n'y a plus de photos
        }
        
        loadPhotosFromFolderPaginated(context, _currentFolder.value, false)
    }

    // Remplacer la méthode originale par la version paginée
    fun loadPhotosFromFolder(context: Context, folderPath: String) {
        // Réinitialiser et charger la première page
        loadPhotosFromFolderPaginated(context, folderPath, true)
    }

    // Méthode pour charger la liste des dossiers disponibles
    fun loadAvailableFolders(context: Context) {
        viewModelScope.launch {
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
        }
    }

    // Méthode pour définir le dossier courant
    fun setCurrentFolder(folderPath: String) {
        _currentFolder.value = folderPath
    }

    // Méthode pour créer un nouveau dossier
    fun createNewFolder(context: Context, folderName: String) {
        viewModelScope.launch {
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
                    Toast.makeText(context, "Ce dossier existe déjà", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Créer le dossier
                if (newFolder.mkdir()) {
                    // Recharger la liste des dossiers
                    loadAvailableFolders(context)
                    
                    // Stockage temporaire du nouveau dossier créé
                    val newlyCreatedFolder = newFolder.absolutePath
                    
                    // Trouver et définir DCIM/Camera comme dossier courant
                    val cameraFolderPath = FilePathUtils.getCameraFolderPath()
                    val cameraFolder = File(cameraFolderPath)
                    if (cameraFolder.exists() && cameraFolder.isDirectory) {
                        _currentFolder.value = cameraFolderPath
                        // Charger les photos du dossier DCIM/Camera
                        loadPhotosFromFolder(context, cameraFolderPath)
                    } else {
                        // Si DCIM/Camera n'existe pas, rester dans le dossier actuel
                        _currentFolder.value = newlyCreatedFolder
                        loadPhotosFromFolder(context, newlyCreatedFolder)
                    }
                    
                    // Indiquer qu'il faut ouvrir le menu déroulant pour montrer le nouveau dossier
                    setShouldOpenFolderDropdown(true)
                    
                    Toast.makeText(context, "Dossier créé: $folderName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Impossible de créer le dossier", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Méthode pour définir l'état d'ouverture du menu déroulant
    fun setShouldOpenFolderDropdown(shouldOpen: Boolean) {
        _shouldOpenFolderDropdown.value = shouldOpen
    }
    
    // Méthode pour réinitialiser l'état d'ouverture du menu déroulant
    fun resetShouldOpenFolderDropdown() {
        _shouldOpenFolderDropdown.value = false
    }
    
    // FONCTION 1: Chargement des photos selon le contexte (dossier spécifique ou DCIM/Camera)
    fun loadPhotos(context: Context) {
        val cameraFolderPath = FilePathUtils.getCameraFolderPath()
        val cameraFolder = File(cameraFolderPath)
        
        // Si un dossier spécifique est sélectionné
        if (_currentFolder.value.isNotEmpty()) {
            // Cas spécial pour DCIM/Camera (utiliser le chemin exact au lieu de la sélection)
            if (FilePathUtils.isCameraFolder(_currentFolder.value)) {
                android.util.Log.d("FPR_DEBUG", "Chargement spécifique DCIM/Camera")
                loadPhotosFromFolder(context, cameraFolderPath)
                return
            }
            
            // Pour les autres dossiers
            loadPhotosFromFolder(context, _currentFolder.value)
            return
        }
        
        // Par défaut, charger DCIM/Camera
        if (cameraFolder.exists() && cameraFolder.isDirectory) {
            android.util.Log.d("FPR_DEBUG", "Chargement par défaut DCIM/Camera")
            loadPhotosFromFolder(context, cameraFolderPath)
            return
        }
        
        // Fallback au dossier de l'application si DCIM/Camera n'existe pas
        val appFolderPath = FilePathUtils.getAppFolderPath()
        val appFolder = File(appFolderPath)
        if (appFolder.exists() && appFolder.isDirectory) {
            loadPhotosFromFolder(context, appFolderPath)
        } else {
            // Créer le dossier s'il n'existe pas
            if (!appFolder.exists()) {
                appFolder.mkdirs()
            }
            loadPhotosFromFolder(context, appFolderPath)
        }
    }
    
    fun selectPhoto(photo: PhotoModel) {
        android.util.Log.d("FPR_DEBUG", "Photo sélectionnée: ${photo.name}, path: ${photo.path}")
        
        // Vérifier d'abord si la photo existe dans la liste actuelle des photos
        val photoIndex = _photos.value.indexOf(photo)
        
        if (photoIndex != -1) {
            // La photo existe dans la liste courante des photos, on peut la sélectionner en toute sécurité
            _selectedPhoto.value = _photos.value[photoIndex]  // Utiliser la référence de la liste actuelle
            android.util.Log.d("FPR_DEBUG", "Photo trouvée dans la liste actuelle à l'index $photoIndex")
        } else {
            // La photo n'est pas dans la liste actuelle des photos
            // Cela peut arriver lors d'un changement de répertoire
            android.util.Log.d("FPR_DEBUG", "Photo non trouvée dans la liste actuelle, ignorer la sélection")
            
            // On ne met pas à jour _selectedPhoto.value pour éviter d'avoir une référence à une photo d'un autre dossier
            // On pourrait afficher un message Toast à l'utilisateur, mais ce n'est pas nécessaire car l'UI sera simplement rafraîchie
            
            // S'assurer qu'aucune photo n'est sélectionnée
            _selectedPhoto.value = null
        }
    }
    
    fun clearSelectedPhoto() {
        _selectedPhoto.value = null
    }
    
    fun updateSelectedPhotoAfterRename(newName: String) {
        _selectedPhoto.value?.let { currentPhoto -> 
            val updatedPhoto = currentPhoto.copy(name = newName)
            _selectedPhoto.value = updatedPhoto
        }
    }
    
    fun renamePhoto(context: Context, photo: PhotoModel, newName: String): Boolean {
        // Créer un callback pour gérer les événements de renommage
        val callback = object : PhotoRenamer.RenameCallback {
            override fun onRenameSuccess(newName: String) {
                updateSelectedPhotoAfterRename(newName)
            }
            
            override fun onRenameInProgress(inProgress: Boolean) {
                _isRenameOperationInProgress.value = inProgress
            }
            
            override fun onRenameComplete(reloadPhotos: Boolean) {
                if (reloadPhotos) {
                    viewModelScope.launch {
                        delay(500)
                        loadPhotos(context)
                    }
                }
            }
        }
        
        return PhotoRenamer.renamePhoto(context, photo, newName, callback)
    }
    
    fun deletePhoto(context: Context, photo: PhotoModel): Boolean {
        // Vérifier d'abord si on a la permission complète
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
                    viewModelScope.launch {
                        delay(500)
                        loadPhotos(context)
                    }
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
                        
                        viewModelScope.launch {
                            delay(500)
                            loadPhotos(context)
                        }
                        return true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            // Essai 3: Méthode standard MediaStore
            val deletedRows = context.contentResolver.delete(photo.uri, null, null)
            
            if (deletedRows > 0) {
                viewModelScope.launch {
                    delay(300)
                    loadPhotos(context)
                }
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
    
    // Nouvelle méthode pour lancer l'appareil photo
    fun launchCamera(activity: Activity, cameraLauncher: ActivityResultLauncher<Intent>) {
        // Utiliser INTENT_ACTION_STILL_IMAGE_CAMERA au lieu de ACTION_IMAGE_CAPTURE
        // Cela ouvre l'appareil photo en mode normal et permet de prendre plusieurs photos
        val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
    
        if (cameraIntent.resolveActivity(activity.packageManager) != null) {
            cameraLauncher.launch(cameraIntent)
        } else {
            Toast.makeText(activity, "Aucune application d'appareil photo trouvée", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkIfNameExists(currentPhotoPath: String, newFullName: String): Boolean {
        val parentPath = FilePathUtils.getParentFolderPath(currentPhotoPath) ?: return false
        return FilePathUtils.fileNameExistsInFolder(parentPath, newFullName)
    }

    fun importPhotosFromCamera(context: Context) {
        viewModelScope.launch {
            try {
                val cameraFolderPath = FilePathUtils.getCameraFolderPath()
                val cameraFolder = File(cameraFolderPath)
                
                val workingDir = File(_currentFolder.value)
                if (!workingDir.exists()) {
                    Toast.makeText(context, "Le dossier de destination n'existe pas", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Récupérer toutes les photos du dossier Camera
                val cameraPhotos = cameraFolder.listFiles { file -> 
                    file.isFile && 
                    FilePathUtils.isImageFile(file.name) && 
                    !FilePathUtils.isTrashFile(file.name)
                } ?: emptyArray()
                
                if (cameraPhotos.isEmpty()) {
                    Toast.makeText(context, "Aucune photo à importer", Toast.LENGTH_SHORT).show()
                    return@launch
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
                
                // Afficher un message récapitulatif
                if (imported > 0) {
                    Toast.makeText(
                        context, 
                        "$imported photos importées${if (skipped > 0) ", $skipped ignorées" else ""}", 
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Rafraîchir la liste des photos
                    loadPhotosFromFolder(context, _currentFolder.value)
                } else {
                    Toast.makeText(context, "Aucune photo importée", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Fonction pour déplacer les photos entre dossiers
    fun movePhotosFromCamera(context: Context, sourceFolder: String, destinationFolder: String) {
        viewModelScope.launch {
            var moved = 0
            var skipped = 0
            var failed = 0
            try {
                val sourceDir = File(sourceFolder)
                val destinationDir = File(destinationFolder)
                
                if (!sourceDir.exists() || !destinationDir.exists()) {
                    Toast.makeText(context, "Dossier source ou destination invalide", Toast.LENGTH_SHORT).show()
                    return@launch
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
                
                // Message de résultat
                if (moved > 0) {
                    Toast.makeText(
                        context,
                        "$moved photos déplacées${if (skipped > 0) ", $skipped ignorées" else ""}${if (failed > 0) ", $failed échecs" else ""}",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Déterminer quel dossier doit être rafraîchi
                    when (_currentFolder.value) {
                        sourceFolder -> {
                            // Si on affiche le dossier source, le rafraîchir
                            loadPhotosFromFolder(context, sourceFolder)
                        }
                        destinationFolder -> {
                            // Si on affiche le dossier destination, le rafraîchir
                            loadPhotosFromFolder(context, destinationFolder)
                        }
                        else -> {
                            // Dans les autres cas, rafraîchir le dossier courant
                            loadPhotosFromFolder(context, _currentFolder.value)
                        }
                    }
                } else {
                    Toast.makeText(context, "Aucune photo déplacée", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
        }
        
        return photosList
    }
    
    // Méthodes pour le mode plein écran
    
    // Activer/désactiver le mode plein écran
    fun setFullscreenMode(enabled: Boolean) {
        _fullscreenMode.value = enabled
    }
    
    // Définir l'index de la photo actuellement affichée en plein écran
    fun setFullscreenPhotoIndex(index: Int) {
        if (index >= 0 && index < _photos.value.size) {
            _fullscreenPhotoIndex.value = index
            // Ne pas mettre à jour la photo sélectionnée, pour éviter la confusion
            // lors de la sélection dans la grille après navigation en plein écran
        }
    }
    
    // Passer à la photo suivante en mode plein écran
    fun nextFullscreenPhoto() {
        val currentIndex = _fullscreenPhotoIndex.value
        val photosSize = _photos.value.size
        
        if (photosSize > 0) {
            // Passer à la photo suivante avec retour au début si on atteint la fin
            val nextIndex = (currentIndex + 1) % photosSize
            setFullscreenPhotoIndex(nextIndex)
        }
    }
    
    // Passer à la photo précédente en mode plein écran
    fun previousFullscreenPhoto() {
        val currentIndex = _fullscreenPhotoIndex.value
        val photosSize = _photos.value.size
        
        if (photosSize > 0) {
            // Passer à la photo précédente avec retour à la fin si on est au début
            val prevIndex = (currentIndex - 1 + photosSize) % photosSize
            setFullscreenPhotoIndex(prevIndex)
        }
    }
    
    // Obtenir l'index de la photo correspondant à un modèle PhotoModel
    fun getPhotoIndex(photo: PhotoModel): Int {
        return _photos.value.indexOf(photo)
    }

    // Réinitialiser l'état wasInFullscreenMode
    fun resetWasInFullscreenMode() {
        _wasInFullscreenMode.value = false
    }
    
    // Mémoriser que l'on vient du mode plein écran
    fun setWasInFullscreenMode(value: Boolean) {
        _wasInFullscreenMode.value = value
    }
}