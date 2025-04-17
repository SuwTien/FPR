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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import android.Manifest
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.documentfile.provider.DocumentFile
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

    private val _useSDCard = MutableStateFlow(false)
    val useSDCard: StateFlow<Boolean> = _useSDCard

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

    companion object {
        const val APP_FOLDER_NAME = "FPR"
    }

    // Initialiser le dossier courant dans init {}
    init {
        val dcimFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val cameraFolder = File(dcimFolder, "Camera")
        
        if (cameraFolder.exists() && cameraFolder.isDirectory) {
            _currentFolder.value = cameraFolder.absolutePath
        } else {
            // Fallback au dossier FPR si Camera n'existe pas
            val appFolder = File(dcimFolder, APP_FOLDER_NAME)
            _currentFolder.value = if (appFolder.exists()) appFolder.absolutePath 
                                   else dcimFolder.absolutePath
        }
    }

    // Méthode pour charger les photos d'un dossier spécifique
    fun loadPhotosFromFolder(context: Context, folderPath: String) {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Si c'est le dossier DCIM/Camera, utilise la méthode spéciale
            if (folderPath.endsWith("/DCIM/Camera")) {
                _photos.value = getCameraPhotos(context)
            } else {
                _photos.value = loadPhotosFromFolderSync(context, folderPath)
            }
            
            _isLoading.value = false
        }
    }

    // Méthode pour charger la liste des dossiers disponibles
    fun loadAvailableFolders(context: Context) {
        viewModelScope.launch {
            val folders = mutableListOf<String>()
            
            // 1. Ajouter DCIM/Camera en premier (dossier par défaut)
            val dcimFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val cameraFolder = File(dcimFolder, "Camera")
            if (cameraFolder.exists() && cameraFolder.isDirectory) {
                folders.add(cameraFolder.absolutePath)
            }
            
            // 2. Ajouter les sous-dossiers de FPR
            val appFolder = File(dcimFolder, APP_FOLDER_NAME)
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
                _availableFolders.value = listOf(cameraFolder.absolutePath) + sortedFolders.map { it.first }
            } else {
                _availableFolders.value = sortedFolders.map { it.first }
            }
        }
    }

    // Méthode pour charger les photos d'un dossier (implémentation synchrone)
    private fun loadPhotosFromFolderSync(context: Context, folderPath: String): List<PhotoModel> {
        val photosList = mutableListOf<PhotoModel>()
        val folder = File(folderPath)
        
        if (!folder.exists() || !folder.isDirectory) {
            return emptyList()
        }
        
        // Vérification spéciale pour DCIM/Camera
        val isCameraFolder = folderPath.endsWith("/DCIM/Camera")
        
        if (isCameraFolder) {
            // POUR DCIM/CAMERA: Utiliser uniquement le système de fichiers direct
            folder.listFiles()?.filter { file -> 
                file.isFile && 
                isImageFile(file.name) &&
                !isTrashFile(file.name) &&
                file.length() > 0  // Ignorer les fichiers vides
            }?.forEach { file ->
                try {
                    photosList.add(PhotoModel(
                        id = file.absolutePath.hashCode().toString(),
                        uri = Uri.fromFile(file),
                        name = file.name,
                        path = file.absolutePath,
                        file = file
                     ))
                } catch (e: Exception) {
                }
            }
        } else {
            // POUR LES AUTRES DOSSIERS: Le code original
            folder.listFiles()?.filter { 
                it.isFile && 
                isImageFile(it.name) &&
                !isTrashFile(it.name)
            }?.forEach { file ->
                try {
                    photosList.add(PhotoModel(
                        id = file.absolutePath.hashCode().toString(),
                        uri = Uri.fromFile(file),
                        name = file.name,
                        path = file.absolutePath,
                        file = file
                    ))
                } catch (e: Exception) {
                }
            }
        }
        
        return photosList.sortedByDescending { it.path }
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
                val dcimFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                val appFolder = File(dcimFolder, APP_FOLDER_NAME)
                
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
                    val cameraFolder = File(dcimFolder, "Camera")
                    if (cameraFolder.exists() && cameraFolder.isDirectory) {
                        _currentFolder.value = cameraFolder.absolutePath
                        // Charger les photos du dossier DCIM/Camera
                        loadPhotosFromFolder(context, cameraFolder.absolutePath)
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

    // Méthode pour vérifier si un fichier est une image
    private fun isImageFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    }
    fun setUseSDCard(context: Context, value: Boolean) {
        _useSDCard.value = value
        if (value) {
            loadPhotosFromSDCard(context)
        } else {
            loadPhotos(context)
        }
    }
    
    
    // FONCTION 1: Chargement des photos selon le contexte (dossier spécifique ou DCIM/Camera)
    fun loadPhotos(context: Context) {
        // Si un dossier spécifique est sélectionné et qu'on n'est pas en mode carte SD
        if (_currentFolder.value.isNotEmpty() && !_useSDCard.value) {
            loadPhotosFromFolder(context, _currentFolder.value)
            return
        }
        
        // Sinon, utiliser le comportement existant (DCIM/Camera)
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val photosList = mutableListOf<PhotoModel>()
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATA
                )
                
                // Filtrer pour n'inclure QUE les photos du stockage interne dans DCIM/Camera
                val selection = "${MediaStore.Images.Media.DATA} LIKE ? AND ${MediaStore.Images.Media.DATA} LIKE ?"
                val selectionArgs = arrayOf("/storage/emulated/0/%", "%DCIM/Camera%")
                
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn).toString()
                        val name = cursor.getString(nameColumn)
                        val path = cursor.getString(dataColumn)
                        val file = File(path)
                        
                        val uri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        
                        if (file.exists()) {
                            photosList.add(PhotoModel(id, uri, name, path, file))
                        }
                    }
                }
                
                _photos.value = photosList
                
                // Mettre à jour la photo sélectionnée
                _selectedPhoto.value?.let { currentSelected -> 
                    val updatedPhoto = photosList.find { it.uri == currentSelected.uri }
                    _selectedPhoto.value = updatedPhoto
                }
                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    // FONCTION 2: Chargement des photos de la carte SD uniquement
    fun loadPhotosFromSDCard(context: Context) {
        viewModelScope.launch {
            try {
                val photosList = mutableListOf<PhotoModel>()
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATA
                )
            
                // Obtenir le chemin de la carte SD
                val externalDirs = context.getExternalFilesDirs(null)
                if (externalDirs.size <= 1 || externalDirs[1] == null) {
                    // Pas de carte SD, revenir aux photos internes
                    Toast.makeText(context, "Aucune carte SD détectée", Toast.LENGTH_SHORT).show()
                    loadPhotos(context)
                    return@launch
                }
            
                // Construire un chemin partiel pour la carte SD
                val sdCardPath = externalDirs[1].path.split("/Android")[0]
            
                // Filtrer pour les photos de DCIM/Camera sur la carte SD uniquement
                // Exclure explicitement le stockage interne
                val selection = "${MediaStore.Images.Media.DATA} LIKE ? AND ${MediaStore.Images.Media.DATA} LIKE ? AND ${MediaStore.Images.Media.DATA} NOT LIKE ?"
                val selectionArgs = arrayOf("$sdCardPath%", "%DCIM/Camera%", "/storage/emulated/0/%")
                
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn).toString()
                        val name = cursor.getString(nameColumn)
                        val path = cursor.getString(dataColumn)
                        val file = File(path)
                    
                        val uri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                    
                        if (file.exists()) {
                            photosList.add(PhotoModel(id, uri, name, path, file))
                        }
                    }
                }
            
                _photos.value = photosList
            
                // Mettre à jour la photo sélectionnée
                _selectedPhoto.value?.let { currentSelected -> 
                    val updatedPhoto = photosList.find { it.uri == currentSelected.uri }
                    _selectedPhoto.value = updatedPhoto
                }
            } catch (e: Exception) {
                // Si erreur, revenir aux photos internes
                loadPhotos(context)
            }
        }
    }
    
    fun selectPhoto(photo: PhotoModel) {
        _selectedPhoto.value = photo
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
        // Cette détection est utile mais ne doit pas être utilisée pour décider
        // quelle source charger après le renommage
        val isOnSDCard = photo.file.absolutePath.startsWith("/storage/") && 
                        !photo.file.absolutePath.startsWith("/storage/emulated/0")
                        
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
                        // CORRECTION: Utiliser le mode global choisi par l'utilisateur
                        // plutôt que l'emplacement de la photo spécifique
                        if (_useSDCard.value) {
                            loadPhotosFromSDCard(context)
                        } else {
                            loadPhotos(context)
                        }
                    }
                }
            }
        }
        
        // Cette ligne reste inchangée
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
        try {
            val currentFile = File(currentPhotoPath)
            val parentDir = currentFile.parentFile ?: return false
        
            // Vérifier si le nom existe déjà dans le même répertoire
            val possibleFile = File(parentDir, newFullName)
        
            // Vérifier aussi que ce n'est pas le fichier actuel (même chemin)
            return possibleFile.exists() && possibleFile.absolutePath != currentFile.absolutePath
        } catch (e: Exception) {
            return false
        }
    }

    // Ajouter ces méthodes dans la classe PhotosViewModel

    // Vérifier si une carte SD est disponible
    fun isSDCardAvailable(context: Context): Boolean {
        val externalDirs = context.getExternalFilesDirs(null)
        // S'il y a plus d'un répertoire externe, cela signifie qu'une carte SD est présente
        return externalDirs.size > 1 && externalDirs[1] != null
    }

    fun importPhotosFromCamera(context: Context) {
        viewModelScope.launch {
            try {
                val cameraDir = File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM), "Camera")
                
                val workingDir = File(_currentFolder.value)
                if (!workingDir.exists()) {
                    Toast.makeText(context, "Le dossier de destination n'existe pas", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Récupérer toutes les photos du dossier Camera
                val cameraPhotos = cameraDir.listFiles { file -> 
                    file.isFile && isImageFile(file.name) && !isTrashFile(file.name)
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
                    file.isFile && isImageFile(file.name) && !isTrashFile(file.name)
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
                
                // Le reste du code (message Toast, etc.) reste inchangé
                // ...
            } catch (e: Exception) {
                Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            if (moved > 0) {
                Toast.makeText(
                    context,
                    "$moved photos déplacées${if (skipped > 0) ", $skipped ignorées" else ""}${if (failed > 0) ", $failed échecs" else ""}",
                    Toast.LENGTH_SHORT
                ).show()
                
                // NOUVEAU CODE - Rafraîchir selon le dossier affiché
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
                    if (isTrashFile(name)) continue
                    
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

    private fun isTrashFile(fileName: String): Boolean {
        val lowerName = fileName.toLowerCase()
        return lowerName.startsWith("trashed-") ||
               lowerName.startsWith(".trashed") ||
               lowerName.contains("trash") ||
               lowerName.contains("deleted") ||
               lowerName.startsWith(".") ||  // Fichiers cachés
               lowerName.contains("~") ||    // Fichiers temporaires
               lowerName.endsWith(".tmp")    // Autres fichiers temporaires
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
            // Mettre également à jour la photo sélectionnée pour la cohérence
            _selectedPhoto.value = _photos.value[index]
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