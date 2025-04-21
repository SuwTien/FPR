package fr.bdst.fastphotosrenamer.utils

import android.os.Environment
import java.io.File

/**
 * Classe utilitaire pour gérer les chemins de fichiers dans l'application
 */
class FilePathUtils {
    companion object {
        private const val APP_FOLDER_NAME = "FPR" // Dossier de l'application
        
        /**
         * Obtient le chemin du dossier DCIM/Camera
         */
        fun getCameraFolderPath(): String {
            val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            return File(dcimDir, "Camera").absolutePath
        }
        
        /**
         * Obtient le chemin du dossier de l'application (DCIM/FPR)
         */
        fun getAppFolderPath(): String {
            val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            return File(dcimDir, APP_FOLDER_NAME).absolutePath
        }

        /**
         * Obtient le chemin du dossier par défaut pour les photos
         */
        fun getDefaultPhotoDir(): String {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath
        }
        
        /**
         * Vérifie si un chemin correspond au dossier DCIM/Camera
         */
        fun isCameraFolder(folderPath: String): Boolean {
            val cameraPath = getCameraFolderPath()
            val normalizedPath = normalizePath(folderPath)
            val normalizedCameraPath = normalizePath(cameraPath)
            
            return normalizedPath == normalizedCameraPath
        }
        
        /**
         * Normalise un chemin de fichier pour les comparaisons
         * (supprime les slashes en fin, normalise les slashes, etc.)
         */
        fun normalizePath(path: String): String {
            var normalizedPath = path.replace('\\', '/')
            
            // Supprimer les slashes à la fin
            while (normalizedPath.endsWith("/")) {
                normalizedPath = normalizedPath.substring(0, normalizedPath.length - 1)
            }
            
            return normalizedPath
        }
        
        /**
         * Obtient le chemin du dossier parent d'un fichier
         */
        fun getParentFolderPath(filePath: String): String? {
            val file = File(filePath)
            return file.parent
        }
        
        /**
         * Vérifie si un nom de fichier existe déjà dans un dossier
         */
        fun fileNameExistsInFolder(folderPath: String, fileName: String): Boolean {
            val folder = File(folderPath)
            if (!folder.exists() || !folder.isDirectory) return false
            
            val file = File(folder, fileName)
            return file.exists()
        }
        
        /**
         * Vérifie si un fichier est une image basé sur son extension
         */
        fun isImageFile(fileName: String): Boolean {
            val lowerName = fileName.toLowerCase()
            return lowerName.endsWith(".jpg") || 
                   lowerName.endsWith(".jpeg") || 
                   lowerName.endsWith(".png") || 
                   lowerName.endsWith(".gif") ||
                   lowerName.endsWith(".webp") ||
                   lowerName.endsWith(".heic") ||
                   lowerName.endsWith(".heif")
        }
        
        /**
         * Vérifie si un fichier est dans la corbeille
         * (commence par "." ou contient ".trashed-")
         */
        fun isTrashFile(fileName: String): Boolean {
            return fileName.startsWith(".") || 
                   fileName.contains(".trashed-") || 
                   fileName.contains(".pending-")
        }
    }
}