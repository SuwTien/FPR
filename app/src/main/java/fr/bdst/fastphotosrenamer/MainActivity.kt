package fr.bdst.fastphotosrenamer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import fr.bdst.fastphotosrenamer.ui.screens.MainScreen
import fr.bdst.fastphotosrenamer.ui.theme.FastPhotosRenamerTheme
import fr.bdst.fastphotosrenamer.viewmodel.PhotosViewModel
import androidx.core.app.ActivityCompat
import java.io.File
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private val viewModel: PhotosViewModel by viewModels()
    
    // Variable pour savoir si l'activité est créée pour la première fois ou après une rotation
    private var isInitialCreation = true

    companion object {
        const val APP_FOLDER_NAME = "FPR"
        const val WRITE_PERMISSION_REQUEST_CODE = 1001
    }
    
    private val requestMediaPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Vérifier si les permissions pour les photos ont été accordées
        val imagesGranted = if (Build.VERSION.SDK_INT >= 33) {
            permissions[Manifest.permission.READ_MEDIA_IMAGES] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        
        if (imagesGranted) {
            viewModel.loadPhotos(this)
        } else {
            Toast.makeText(this, "L'accès aux photos est nécessaire pour utiliser l'application", Toast.LENGTH_LONG).show()
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Rafraîchir la galerie après la prise de photo
            viewModel.loadPhotos(this)
        }
    }
    
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.launchCamera(this, cameraLauncher)
        }
    }

    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission accordée, rafraîchir les photos
            viewModel.loadPhotos(this)
        }
    }

    // Launcher pour l'accès à l'arborescence des fichiers
    private val requestDocumentTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // Persister la permission
            val contentResolver = applicationContext.contentResolver
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        
            // Stocker l'URI dans un SharedPreferences
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("document_tree_uri", uri.toString()).apply()
        
            // Rafraîchir les photos
            viewModel.loadPhotos(this)
            Toast.makeText(this, "Accès aux fichiers accordé!", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Déterminer si c'est une recréation après rotation
        isInitialCreation = savedInstanceState == null
        
        // On n'initialise les dossiers et permissions que lors de la première création
        if (isInitialCreation) {
            checkAndRequestAllPermissions()
        } else {
            // En cas de recréation, on s'assure juste que les dossiers existent
            // sans changer le dossier courant
            ensureAppFolderExistsWithoutChangingCurrentFolder()
        }
        
        // Détection du thème sombre via une méthode non-Compose
        val isDarkTheme = resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        // Configurer les icônes de la barre d'état
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkTheme
        }
        
        setContent {
            FastPhotosRenamerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        onCameraClick = { checkAndRequestCameraPermission() }
                    )
                }
            }
        }
    }
    
    // Sauvegarder l'état lors d'une rotation
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Nous n'avons pas besoin de sauvegarder des données supplémentaires ici,
        // le simple fait que onSaveInstanceState soit appelé indique une recréation
    }
    
    private fun checkAndRequestAllPermissions() {
        // Choisir la permission appropriée selon la version Android
        if (Build.VERSION.SDK_INT >= 33) {
            // Pour Android 13+, utiliser READ_MEDIA_IMAGES
            val mediaPermission = Manifest.permission.READ_MEDIA_IMAGES
            
            if (ContextCompat.checkSelfPermission(this, mediaPermission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(mediaPermission),
                    100
                )
            } else {
                // Permission déjà accordée
                ensureAppFolderExists()  // Créer le dossier FPR ici
                viewModel.loadPhotos(this)
            }
        } else {
            // Pour Android 12 et moins, utiliser READ_EXTERNAL_STORAGE
            val readPermission = Manifest.permission.READ_EXTERNAL_STORAGE
            
            if (ContextCompat.checkSelfPermission(this, readPermission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(readPermission),
                    100
                )
            } else {
                // Permission déjà accordée
                ensureAppFolderExists()  // Créer le dossier FPR ici
                viewModel.loadPhotos(this)
            }
            
            // Permission d'écriture uniquement pour Android 10 et moins
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    101
                )
            }
        }
        
        // Pour Android 11+, informer l'utilisateur sur l'accès à tous les fichiers
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!areAllFilesPermissionsGranted()) {
                Toast.makeText(
                    this,
                    "Pour renommer les photos, vous devrez accorder l'accès à tous les fichiers dans les paramètres",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            100 -> {
                // Code pour permission de lecture
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    viewModel.loadPhotos(this)
                }
            }
            101 -> {
                // Code pour permission d'écriture - rien de spécial à faire
            }
            WRITE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    viewModel.loadPhotos(this)
                }
            }
        }
    }
    
    private fun checkAndRequestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.launchCamera(this, cameraLauncher)
            }
            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Vérifier l'existence des dossiers sans changer le dossier courant
        ensureAppFolderExistsWithoutChangingCurrentFolder()
        
        // Recharger les photos uniquement si les permissions sont accordées
        if (areMediaPermissionsGranted()) {
            // Au lieu de charger DCIM/Camera par défaut, on charge le dossier actuellement sélectionné
            val currentFolder = viewModel.currentFolder.value
            if (currentFolder.isNotEmpty()) {
                viewModel.loadPhotosFromFolder(this, currentFolder)
            } else {
                // Seulement si aucun dossier n'est sélectionné, on charge le dossier par défaut
                viewModel.loadPhotos(this)
            }
        }
    }
    
    // Méthode utilitaire pour vérifier si les permissions média sont accordées
    private fun areMediaPermissionsGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // Vérifier si les permissions "All Files Access" sont accordées (pour Android 11+)
    private fun areAllFilesPermissionsGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true
        }
    }
    
    // Méthode pour créer le dossier de l'application
    private fun ensureAppFolderExists() {
        if (areMediaPermissionsGranted()) {
            try {
                // 1. S'assurer que le dossier FPR existe (pour l'organisation)
                val dcimFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                val appFolder = File(dcimFolder, APP_FOLDER_NAME)
                
                if (!appFolder.exists()) {
                    appFolder.mkdir()
                }
                
                // 2. Mais TOUJOURS utiliser le dossier Camera comme dossier par défaut
                val cameraFolder = File(dcimFolder, "Camera")
                if (cameraFolder.exists() && cameraFolder.isDirectory) {
                    viewModel.setCurrentFolder(cameraFolder.absolutePath)
                } else {
                    // Fallback uniquement si Camera n'existe pas
                    viewModel.setCurrentFolder(appFolder.absolutePath)
                }
            } catch (e: Exception) {
                // Gestion des erreurs
            }
        }
    }

    // Méthode pour vérifier l'existence des dossiers sans changer le dossier courant
    private fun ensureAppFolderExistsWithoutChangingCurrentFolder() {
        if (areMediaPermissionsGranted()) {
            try {
                // S'assurer que le dossier FPR existe (pour l'organisation)
                val dcimFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                val appFolder = File(dcimFolder, APP_FOLDER_NAME)
                
                if (!appFolder.exists()) {
                    appFolder.mkdir()
                }
                
                // Ne pas modifier le dossier courant ici, contrairement à ensureAppFolderExists
            } catch (e: Exception) {
                // Gestion silencieuse des erreurs
            }
        }
    }

    fun getRequestDocumentTreeLauncher(): ActivityResultLauncher<Uri?> {
        return requestDocumentTreeLauncher
    }
}