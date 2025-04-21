# Changelog FastPhotosRenamer

## 21 avril 2025 - Refactorisation Phase 2

### Ajouts
- Création de la classe `PhotoManager` dans le package `storage` qui centralise toutes les opérations liées aux photos
  - Gestion du chargement et de la pagination des photos
  - Fonctions de renommage et de suppression de photos
  - Fonctions d'import et de déplacement des photos
  - Utilisation des coroutines Kotlin pour les opérations asynchrones
  - Callbacks pour informer des résultats des opérations

- Création de la classe `FolderManager` dans le package `storage` qui centralise toutes les opérations liées aux dossiers
  - Gestion du chargement de la liste des dossiers disponibles
  - Fonctions de création de dossiers
  - Fonctions de vérification et de navigation des dossiers
  - Utilisation des coroutines Kotlin pour les opérations asynchrones

### Modifications
- Refactorisation du `PhotosViewModel` pour déléguer les opérations au `PhotoManager` et au `FolderManager`
  - Réduction du couplage et meilleure séparation des responsabilités
  - Simplification des méthodes en remplaçant le code direct par des appels aux managers
  - Conservation de la gestion de l'état de l'application (UI state) dans le ViewModel

### Suppressions
- Le fichier `PhotoPaginator.kt` a été supprimé car ses fonctionnalités sont intégrées dans `PhotoManager`

### Prochaines étapes
- Tester l'application après ces refactorisations pour s'assurer qu'il n'y a pas de régressions
- Implémenter le Repository Pattern pour améliorer davantage l'architecture
- Moderniser la gestion asynchrone avec Kotlin Flow