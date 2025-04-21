# Changelog FastPhotosRenamer

## 21 avril 2025 - Refactorisation Phase 2.2

### Ajouts
- Création de la classe `PhotoManager` dans le package `storage` qui centralise toutes les opérations liées aux photos
  - Gestion du chargement et de la pagination des photos
  - Fonctions de renommage et de suppression de photos
  - Fonctions d'import et de déplacement des photos
  - Utilisation des coroutines Kotlin pour les opérations asynchrones
  - Callbacks pour informer des résultats des opérations

### Modifications
- Refactorisation du `PhotosViewModel` pour déléguer les opérations au `PhotoManager`
  - Réduction du couplage et meilleure séparation des responsabilités
  - Simplification des méthodes en remplaçant le code direct par des appels au `PhotoManager`
  - Conservation de la gestion de l'état de l'application (UI state) dans le ViewModel

### Suppressions prévues
- Le fichier `PhotoPaginator.kt` sera supprimé car ses fonctionnalités sont intégrées dans `PhotoManager`

### Prochaines étapes
- Implémenter un `FolderManager` pour gérer les opérations liées aux dossiers
- Adapter le `PhotosViewModel` pour utiliser le `FolderManager`
- Valider le fonctionnement de l'application après ces refactorisations