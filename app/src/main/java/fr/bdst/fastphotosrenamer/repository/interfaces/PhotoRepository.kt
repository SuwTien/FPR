package fr.bdst.fastphotosrenamer.repository.interfaces

import android.content.Context
import fr.bdst.fastphotosrenamer.model.PhotoModel
import fr.bdst.fastphotosrenamer.storage.PhotoRenamer
import kotlinx.coroutines.flow.Flow

/**
 * Interface définissant les opérations pour accéder et manipuler les photos
 */
interface PhotoRepository {

    /**
     * Charge une page de photos depuis un dossier spécifique
     * @param context Le contexte de l'application
     * @param folderPath Le chemin du dossier
     * @param offset L'offset pour la pagination (position de départ)
     * @param limit Le nombre maximum de photos à charger
     * @return Un Flow émettant une paire avec la liste des photos et un booléen indiquant s'il y a d'autres photos
     */
    fun loadPhotosPageFromFolder(
        context: Context,
        folderPath: String,
        offset: Int,
        limit: Int
    ): Flow<Pair<List<PhotoModel>, Boolean>>
    
    /**
     * Charge les photos depuis un dossier spécifique ou le dossier par défaut
     * @param context Le contexte de l'application
     * @param folderPath Le chemin du dossier à charger. Si vide, utilise le dossier par défaut
     * @return Un Flow émettant une paire avec la liste des photos et un booléen indiquant s'il y a d'autres photos
     */
    fun loadPhotos(
        context: Context, 
        folderPath: String
    ): Flow<Pair<List<PhotoModel>, Boolean>>
    
    /**
     * Supprime une photo du système
     * @param context Le contexte de l'application
     * @param photo La photo à supprimer
     * @return Un Flow émettant true si la suppression a réussi, false sinon
     */
    fun deletePhoto(
        context: Context, 
        photo: PhotoModel
    ): Flow<Boolean>
    
    /**
     * Renomme une photo
     * @param context Le contexte de l'application
     * @param photo La photo à renommer
     * @param newName Le nouveau nom à appliquer
     * @return Un Flow émettant les résultats du renommage (succès, progression, complétion)
     */
    fun renamePhoto(
        context: Context, 
        photo: PhotoModel, 
        newName: String
    ): Flow<RenameResult>
    
    /**
     * Vérifie si un nom de fichier existe déjà dans le dossier parent d'un fichier
     * @param currentPhotoPath Le chemin de la photo actuelle
     * @param newFullName Le nouveau nom complet à vérifier
     * @return true si le nom existe déjà, false sinon
     */
    fun checkIfNameExists(
        currentPhotoPath: String, 
        newFullName: String
    ): Boolean
    
    /**
     * Importe les photos du dossier DCIM/Camera vers un dossier spécifié
     * @param context Le contexte de l'application
     * @param destinationFolder Le dossier de destination
     * @return Un Flow émettant une paire avec le nombre de photos importées et ignorées
     */
    fun importPhotosFromCamera(
        context: Context,
        destinationFolder: String
    ): Flow<Pair<Int, Int>>
    
    /**
     * Déplace les photos d'un dossier vers un autre
     * @param context Le contexte de l'application
     * @param sourceFolder Le dossier source
     * @param destinationFolder Le dossier de destination
     * @return Un Flow émettant un Triple avec le nombre de photos déplacées, ignorées et échouées
     */
    fun movePhotosFromFolder(
        context: Context,
        sourceFolder: String,
        destinationFolder: String
    ): Flow<Triple<Int, Int, Int>>
    
    /**
     * Obtient les photos du dossier DCIM/Camera
     * @param context Le contexte de l'application
     * @return Un Flow émettant la liste des photos trouvées dans le dossier DCIM/Camera
     */
    fun getCameraPhotos(
        context: Context
    ): Flow<List<PhotoModel>>
    
    /**
     * Classe data pour représenter le résultat d'une opération de renommage
     */
    data class RenameResult(
        val success: Boolean,
        val newName: String? = null,
        val inProgress: Boolean = false,
        val reloadNeeded: Boolean = false,
        val error: String? = null
    )
}