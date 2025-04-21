package fr.bdst.fastphotosrenamer.model

import android.net.Uri
import java.io.File

data class PhotoModel(
    val id: Long,
    val uri: Uri,
    val name: String,
    val path: String,
    val file: File,
    val dateAdded: Long = 0,
    val dateModified: Long = 0,
    val size: Long = 0,
    val width: Int = 0,
    val height: Int = 0
)