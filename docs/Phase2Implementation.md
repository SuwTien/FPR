# Guide d'implémentation - Phase 2

Ce document détaille l'implémentation technique de la Phase 2 du plan de refactorisation de FastPhotosRenamer.

## Contexte

Actuellement, le `PhotosViewModel` contient un excès de responsabilités, ce qui rend le code difficile à maintenir et à tester. La phase 2 vise à extraire ces responsabilités dans des classes dédiées suivant le principe de responsabilité unique.

## Étapes d'implémentation

### 2.1 ✓ Création de `PhotoPaginator` (TERMINÉ)

- La classe `PhotoPaginator` a été créée avec succès pour gérer la pagination des photos
- Elle offre une méthode `loadPhotosPageFromFolder` qui charge une page de photos avec un offset et une limite
- L'intégration dans `PhotosViewModel` est faite via les méthodes `loadPhotosFromFolderPaginated` et `loadMorePhotos`

### 2.2 Extension vers `PhotoManager` complet

#### Description
Transformer le `PhotoPaginator` existant en un gestionnaire complet de photos qui s'occupera de toutes les opérations liées aux photos.

#### Fonctions à déplacer
- **Chargement des photos**
  - `loadPhotosFromFolderPaginated` (déjà en partie dans `PhotoPaginator`)
  - `loadPhotos` (logique principale de chargement)
  - `getCameraPhotos` (obtention des photos du dossier Camera)

- **Opérations sur les photos**
  - `selectPhoto` / `clearSelectedPhoto` / `updateSelectedPhotoAfterRename`
  - `renamePhoto` (coordination avec `PhotoRenamer`)
  - `deletePhoto` (logique de suppression)
  - `importPhotosFromCamera` / `movePhotosFromCamera` (logique d'import/déplacement)

#### Implementation proposée
```kotlin
object PhotoManager {
    // Constantes
    const val PAGE_SIZE = 30 // Repris de PhotoPaginator
    
    // États internes
    private val _isRenameOperationInProgress = MutableStateFlow(false)
    val isRenameOperationInProgress: StateFlow<Boolean> = _isRenameOperationInProgress
    
    // Méthodes existantes de PhotoPaginator
    fun loadPhotosPageFromFolder(
        context: Context, 
        folderPath: String,
        offset: Int,
        limit: Int
    ): Pair<List<PhotoModel>, Boolean> {
        // Code existant
    }
    
    // Nouvelles méthodes à ajouter
    fun loadPhotos(
        context: Context,
        folderPath: String,
        offset: Int = 0,
        limit: Int = PAGE_SIZE
    ): Pair<List<PhotoModel>, Boolean> {
        // Adaptation du code existant de PhotosViewModel.loadPhotos
    }
    
    fun deletePhoto(context: Context, photo: PhotoModel): Boolean {
        // Déplacer le code de PhotosViewModel.deletePhoto
    }
    
    fun renamePhoto(context: Context, photo: PhotoModel, newName: String, callback: PhotoRenamer.RenameCallback): Boolean {
        // Logique simple de délégation à PhotoRenamer
        return PhotoRenamer.renamePhoto(context, photo, newName, callback)
    }
    
    // etc. pour les autres fonctions
}
```

### 2.3 Création de `FolderManager`

#### Description
Créer une classe dédiée à la gestion des dossiers qui s'occupera de tout ce qui est lié aux opérations sur les dossiers.

#### Fonctions à déplacer
- **Gestion des dossiers**
  - `loadAvailableFolders` (chargement des dossiers disponibles)
  - `setCurrentFolder` / `createNewFolder`
  - `setShouldOpenFolderDropdown` / `resetShouldOpenFolderDropdown` (UI state)

#### Implementation proposée
```kotlin
object FolderManager {
    // États internes
    private val _availableFolders = MutableStateFlow<List<String>>(emptyList())
    val availableFolders: StateFlow<List<String>> = _availableFolders
    
    private val _shouldOpenFolderDropdown = MutableStateFlow(false)
    val shouldOpenFolderDropdown: StateFlow<Boolean> = _shouldOpenFolderDropdown
    
    // Méthodes
    fun loadAvailableFolders(context: Context) {
        // Code provenant de PhotosViewModel.loadAvailableFolders
    }
    
    fun createNewFolder(context: Context, folderName: String): String? {
        // Code provenant de PhotosViewModel.createNewFolder
        // Retour du chemin du nouveau dossier ou null
    }
    
    // etc. pour les autres fonctions
}
```

### 2.4 Adaptation du `PhotosViewModel`

#### Description
Refactoriser le `PhotosViewModel` pour qu'il utilise les nouvelles classes dédiées au lieu de contenir toute la logique lui-même.

#### Modifications nécessaires
- Remplacer les appels directs aux méthodes par des délégations aux nouvelles classes
- Conserver la gestion de l'état global et la coordination entre les différents composants
- Ajouter des références aux flux d'états des nouvelles classes

#### Implémentation proposée
```kotlin
class PhotosViewModel : ViewModel() {
    // États globaux de l'application
    private val _photos = MutableStateFlow<List<PhotoModel>>(emptyList())
    val photos: StateFlow<List<PhotoModel>> = _photos
    
    private val _selectedPhoto = MutableStateFlow<PhotoModel?>(null)
    val selectedPhoto: StateFlow<PhotoModel?> = _selectedPhoto
    
    // Accès aux états des managers
    val isRenameOperationInProgress = PhotoManager.isRenameOperationInProgress
    val availableFolders = FolderManager.availableFolders
    val shouldOpenFolderDropdown = FolderManager.shouldOpenFolderDropdown
    
    // État local spécifique au ViewModel
    private val _currentFolder = MutableStateFlow<String>("")
    val currentFolder: StateFlow<String> = _currentFolder
    
    // ... autres états spécifiques au ViewModel ...
    
    // Méthode de délégation
    fun loadPhotosFromFolder(context: Context, folderPath: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val (loadedPhotos, hasMore) = PhotoManager.loadPhotos(context, folderPath)
            _photos.value = loadedPhotos
            _hasMorePhotos.value = hasMore
            _isLoading.value = false
        }
    }
    
    // ... etc. pour les autres méthodes ...
}
```

### 2.5 Tests et validation

#### Objectifs
- Vérifier que toutes les fonctionnalités continuent de fonctionner après la refactorisation
- S'assurer qu'il n'y a pas de régression dans le comportement de l'application
- Identifier et corriger les éventuels problèmes d'intégration entre les nouvelles classes

#### Plan de test
1. **Test du `PhotoManager`**
   - Chargement correct des photos avec pagination
   - Opérations de renommage et suppression
   - Import et déplacement de photos

2. **Test du `FolderManager`**
   - Liste correcte des dossiers disponibles
   - Création de nouveaux dossiers
   - Navigation entre les dossiers

3. **Test d'intégration**
   - Interaction correcte entre `PhotosViewModel`, `PhotoManager` et `FolderManager`
   - Vérification que les états sont correctement propagés
   - Comportement global de l'application

## Prochaines étapes

Une fois la phase 2 terminée, nous pourrons aborder la phase 3 qui consistera à implémenter un Repository Pattern plus formel et à moderniser la gestion asynchrone.

## Conseils de mise en œuvre

- Procéder par étapes, une fonction à la fois
- Tester régulièrement pour s'assurer que les modifications n'introduisent pas de régression
- Utiliser des interfaces claires pour la communication entre les différentes couches
- Documenter les classes et les méthodes importantes
- Exploiter les coroutines et flows Kotlin pour une gestion d'état réactive