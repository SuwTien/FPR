package fr.bdst.fastphotosrenamer.model

import android.net.Uri
import java.io.File

data class PhotoModel(
    val id: String,
    val uri: Uri,
    val name: String,
    val path: String,
    val file: File
)