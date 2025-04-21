# Guide d'implémentation - Phase 3

Ce document détaille l'implémentation technique de la Phase 3 du plan de refactorisation de FastPhotosRenamer, centrée sur l'amélioration de l'architecture et la modernisation de la gestion asynchrone.

## Objectifs

1. Implémenter le Repository Pattern pour créer une abstraction entre les sources de données et la logique métier
2. Moderniser la gestion asynchrone en remplaçant les callbacks par des Kotlin Flow
3. Améliorer la testabilité et la maintenabilité du code

## Structure prévue

```
fr.bdst.fastphotosrenamer
├── model
│   └── PhotoModel.kt                    (existant)
├── repository
│   ├── interfaces
│   │   ├── PhotoRepository.kt           (nouveau)
│   │   └── FolderRepository.kt          (nouveau)
│   └── implementations
│       ├── MediaStorePhotoRepository.kt (nouveau)
│       └── FileSystemFolderRepository.kt(nouveau)
├── storage
│   ├── PhotoManager.kt                  (à modifier)
│   ├── FolderManager.kt                 (à modifier)
│   └── PhotoRenamer.kt                  (à modifier)
└── viewmodel
    └── PhotosViewModel.kt               (à modifier)
```

## Étapes d'implémentation

### Étape 3.1: Définir les interfaces des Repository

#### Création de PhotoRepository.kt
Interface définissant les opérations nécessaires pour accéder aux photos:
- Chargement de photos avec pagination
- Renommage, suppression et autres opérations sur les photos
- Toutes les méthodes retournent des Flows au lieu de callbacks

#### Création de FolderRepository.kt
Interface définissant les opérations nécessaires pour gérer les dossiers:
- Chargement des dossiers disponibles
- Création, vérification et autres opérations sur les dossiers
- Toutes les méthodes retournent des Flows

### Étape 3.2: Créer les implémentations concrètes des Repository

#### Implémentation de MediaStorePhotoRepository.kt
- Utilise MediaStore pour accéder aux photos
- Implémente toutes les méthodes de l'interface PhotoRepository
- Gère les spécificités de MediaStore et les différentes versions d'Android

#### Implémentation de FileSystemFolderRepository.kt
- Utilise le système de fichiers pour gérer les dossiers
- Implémente toutes les méthodes de l'interface FolderRepository
- Gère les spécificités du système de fichiers Android

### Étape 3.3: Adapter les classes Manager existantes

#### Modification de PhotoManager.kt
- Injecter PhotoRepository et l'utiliser à la place des appels directs
- Conserver la logique métier mais déléguer l'accès aux données au repository
- Adapter l'exposition des états pour utiliser des Flows

#### Modification de FolderManager.kt
- Injecter FolderRepository et l'utiliser à la place des appels directs
- Conserver la logique métier mais déléguer l'accès aux données au repository
- Adapter l'exposition des états pour utiliser des Flows

### Étape 3.4: Moderniser la gestion asynchrone

#### Conversion des callbacks en Flows
- Remplacer les callbacks dans les différentes opérations par des Flows
- Utiliser les opérateurs de Flow (map, flatMapLatest, etc.) pour transformer les données
- Implémenter la gestion des erreurs avec catch/onCompletion

#### Modification de PhotoRenamer.kt
- Adapter pour retourner des Flows au lieu d'utiliser des callbacks
- Conserver la logique mais moderniser l'interface

### Étape 3.5: Adapter le ViewModel

#### Modification de PhotosViewModel.kt
- Injecter les Manager modifiés
- Collecter les Flows dans des StateFlows pour l'UI
- Utiliser viewModelScope pour gérer le cycle de vie des collectes

### Étape 3.6: Tests et validation

- Tester chaque composant individuellement
- Vérifier l'intégration de tous les composants
- S'assurer qu'il n'y a pas de régressions fonctionnelles

## Implémentation de l'injection de dépendances

Pour simplifier l'injection des repositories dans les managers, nous utiliserons une approche simple de dépendances:

```kotlin
// Exemple de configuration des dépendances
object AppDependencies {
    // Repositories
    val photoRepository: PhotoRepository by lazy { MediaStorePhotoRepository() }
    val folderRepository: FolderRepository by lazy { FileSystemFolderRepository() }
    
    // Managers
    val photoManager: PhotoManager by lazy { PhotoManager(photoRepository) }
    val folderManager: FolderManager by lazy { FolderManager(folderRepository) }
}
```

## Notes sur l'utilisation de Kotlin Flow

### Avantages des Flows par rapport aux callbacks
- Composition et transformation plus faciles avec des opérateurs
- Intégration native avec les coroutines Kotlin
- Gestion plus simple des opérations séquentielles et parallèles
- Support du backpressure (important pour les grandes collections de photos)

### Exemples d'utilisation de Flow

```kotlin
// Exemple de méthode repository retournant un Flow
override fun getPhotosFromFolder(folderPath: String, offset: Int, limit: Int): Flow<List<PhotoModel>> = flow {
    // Effectuer l'opération asynchrone
    val photos = loadPhotosFromMediaStore(folderPath, offset, limit)
    // Émettre le résultat
    emit(photos)
}

// Exemple d'utilisation dans le Manager
fun loadPhotos(folderPath: String) = viewModelScope.launch {
    photoRepository.getPhotosFromFolder(folderPath, 0, PAGE_SIZE)
        .catch { e -> 
            // Gérer les erreurs
            _errorState.value = e.message
        }
        .collect { photos ->
            // Mettre à jour l'état
            _photos.value = photos
        }
}
```

## Intégration avec l'UI existante

L'UI existante utilisant déjà des StateFlows, l'adaptation sera minimale. Les changements principaux concerneront la façon dont ces StateFlows sont alimentés (via des Flow au lieu d'affectations directes).

## Prochaines étapes après la Phase 3

Une fois la phase 3 complétée, nous pourrons passer à la phase 4 qui se concentrera sur:
- L'amélioration de la documentation
- L'ajout de tests unitaires
- L'optimisation des performances