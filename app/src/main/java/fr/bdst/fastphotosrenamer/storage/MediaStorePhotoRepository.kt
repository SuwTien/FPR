package fr.bdst.fastphotosrenamer.storage

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import fr.bdst.fastphotosrenamer.model.PhotoModel
import fr.bdst.fastphotosrenamer.repository.interfaces.PhotoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

class MediaStorePhotoRepository : PhotoRepository {

    companion object {
        private const val TAG = "MediaStorePhotoRepo"
    }

    override fun loadPhotosPageFromFolder(
        context: Context,
        folderPath: String,
        offset: Int,
        limit: Int
    ): Flow<Pair<List<PhotoModel>, Boolean>> {
        // Implementation needed - returning empty list for now
        return flow {
            emit(Pair(emptyList(), false))
        }
    }

    override fun loadPhotos(
        context: Context, 
        folderPath: String
    ): Flow<Pair<List<PhotoModel>, Boolean>> {
        // Implementation needed - returning empty list for now
        return flow {
            emit(Pair(emptyList(), false))
        }
    }

    override fun deletePhoto(
        context: Context, 
        photo: PhotoModel
    ): Flow<Boolean> {
        // Implementation needed - returning false for now
        return flow {
            emit(false)
        }
    }

    override fun renamePhoto(
        context: Context, 
        photo: PhotoModel, 
        newName: String
    ): Flow<PhotoRepository.RenameResult> {
        // Implementation needed - returning failure for now
        return flow {
            emit(PhotoRepository.RenameResult(false))
        }
    }

    override fun checkIfNameExists(
        currentPhotoPath: String, 
        newFullName: String
    ): Boolean {
        // Implementation needed - returning false for now
        return false
    }

    override fun importPhotosFromCamera(
        context: Context,
        destinationFolder: String
    ): Flow<Pair<Int, Int>> {
        // Implementation needed - returning zeros for now
        return flow {
            emit(Pair(0, 0))
        }
    }

    override fun movePhotosFromFolder(
        context: Context,
        sourceFolder: String,
        destinationFolder: String
    ): Flow<Triple<Int, Int, Int>> = flow {
        // Initialize counters
        var movedCount = 0
        var skippedCount = 0
        var failedCount = 0
        
        try {
            // Check if folders exist
            val sourceDir = File(sourceFolder)
            val destinationDir = File(destinationFolder)
            
            if (!sourceDir.exists() || !sourceDir.isDirectory) {
                emit(Triple(movedCount, skippedCount, 1))
                return@flow
            }
            
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }
            
            // Get all image files from source folder
            val imageFiles = sourceDir.listFiles { file ->
                file.isFile && isImageFile(file.name)
            } ?: emptyArray()
            
            for (imageFile in imageFiles) {
                val destinationFile = File(destinationDir, imageFile.name)
                
                // Skip if destination file already exists
                if (destinationFile.exists()) {
                    skippedCount++
                    continue
                }
                
                try {
                    // Move file
                    if (imageFile.renameTo(destinationFile)) {
                        // Update MediaStore
                        updateMediaStoreForMovedFile(context, imageFile.absolutePath, destinationFile.absolutePath)
                        movedCount++
                    } else {
                        failedCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error moving file ${imageFile.name}", e)
                    failedCount++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error moving files from $sourceFolder to $destinationFolder", e)
            failedCount++
        }
        
        // Emit final result
        emit(Triple(movedCount, skippedCount, failedCount))
    }

    override fun getCameraPhotos(
        context: Context
    ): Flow<List<PhotoModel>> {
        // Implementation needed - returning empty list for now
        return flow {
            emit(emptyList())
        }
    }

    /**
     * Updates MediaStore after a file has been moved
     */
    private fun updateMediaStoreForMovedFile(context: Context, oldPath: String, newPath: String) {
        try {
            val contentResolver = context.contentResolver
            
            // Remove old entry
            contentResolver.delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Images.Media.DATA + "=?",
                arrayOf(oldPath)
            )
            
            // Add new entry
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, newPath)
                put(MediaStore.Images.Media.MIME_TYPE, getMimeTypeFromPath(newPath))
            }
            
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating MediaStore for moved file", e)
        }
    }

    /**
     * Helper method to check if a file is an image based on its extension
     */
    private fun isImageFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    }

    /**
     * Helper method to get MIME type from file path
     */
    private fun getMimeTypeFromPath(path: String): String {
        return when (path.substringAfterLast('.').lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            else -> "image/jpeg" // Default
        }
    }
}