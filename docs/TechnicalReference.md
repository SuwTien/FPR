# Index Technique de FastPhotosRenamer

Ce document fournit une référence technique détaillée de toutes les classes, fonctions et méthodes de l'application FastPhotosRenamer.

## Table des matières
1. [Classes et modèles](#classes-et-modèles)
2. [Fonctions principales](#fonctions-principales)
3. [Composants UI](#composants-ui)
4. [Utilitaires et helpers](#utilitaires-et-helpers)

## Classes et modèles

### MainActivity
*Responsabilité*: Point d'entrée de l'application, gestion des permissions et initialisation de l'UI.

**Propriétés**:
- `viewModel`: Instance du PhotosViewModel.
- `APP_FOLDER_NAME`: Constante "FPR" définissant le nom du dossier d'application.
- `WRITE_PERMISSION_REQUEST_CODE`: Code pour les requêtes de permission d'écriture.

**Méthodes**:
- `onCreate()`: Initialisation de l'application et vérification des permissions.
- `checkAndRequestAllPermissions()`: Vérifie et demande toutes les permissions nécessaires.
- `onRequestPermissionsResult()`: Gère les réponses aux demandes de permissions.
- `checkAndRequestCameraPermission()`: Vérifie et demande la permission de la caméra.
- `onResume()`: Actualise les photos à la reprise de l'application.
- `areMediaPermissionsGranted()`: Vérifie si les permissions média sont accordées.
- `areAllFilesPermissionsGranted()`: Vérifie les permissions d'accès à tous les fichiers.
- `ensureAppFolderExists()`: Crée le dossier d'application s'il n'existe pas.
- `getRequestDocumentTreeLauncher()`: Récupère le launcher pour l'accès à l'arborescence des documents.

**Launchers**:
- `requestMediaPermissionsLauncher`: Pour demander l'accès aux médias.
- `cameraLauncher`: Pour lancer l'appareil photo.
- `requestCameraPermissionLauncher`: Pour demander l'accès à la caméra.
- `requestStoragePermissionLauncher`: Pour demander l'accès au stockage.
- `requestDocumentTreeLauncher`: Pour l'accès à l'arborescence de fichiers.

### PhotoModel
*Responsabilité*: Représente une photo dans l'application.

**Propriétés**:
- `id`: Identifiant unique de la photo.
- `uri`: URI pour accéder à la photo.
- `name`: Nom du fichier.
- `path`: Chemin complet du fichier.
- `file`: Objet File référençant la photo.

### PhotosViewModel
*Responsabilité*: Gère la logique métier et l'état de l'application.

**États (StateFlow)**:
- `photos`: Liste des photos actuellement affichées.
- `selectedPhoto`: Photo sélectionnée pour le renommage ou la visualisation.
- `isRenameOperationInProgress`: Indique si une opération de renommage est en cours.
- `useSDCard`: Indique si l'application utilise la carte SD.
- `currentFolder`: Dossier courant d'où les photos sont chargées.
- `availableFolders`: Liste des dossiers disponibles.
- `shouldOpenFolderDropdown`: Contrôle l'ouverture du sélecteur de dossier.
- `isLoading`: Indicateur de chargement en cours.
- `fullscreenMode`: État du mode plein écran.
- `fullscreenPhotoIndex`: Index de la photo affichée en plein écran.
- `wasInFullscreenMode`: Indique si l'utilisateur était en mode plein écran.

**Méthodes de gestion des photos**:
- `loadPhotosFromFolder()`: Charge les photos d'un dossier spécifique.
- `loadAvailableFolders()`: Charge la liste des dossiers disponibles.
- `loadPhotosFromFolderSync()`: Charge les photos d'un dossier (synchrone).
- `setCurrentFolder()`: Définit le dossier courant.
- `createNewFolder()`: Crée un nouveau dossier.
- `setShouldOpenFolderDropdown()`: Définit l'état d'ouverture du sélecteur.
- `resetShouldOpenFolderDropdown()`: Réinitialise l'état du sélecteur.
- `isImageFile()`: Vérifie si un fichier est une image.
- `setUseSDCard()`: Active/désactive l'utilisation de la carte SD.
- `loadPhotos()`: Charge les photos du stockage interne.
- `loadPhotosFromSDCard()`: Charge les photos depuis une carte SD.
- `selectPhoto()`: Sélectionne une photo.
- `clearSelectedPhoto()`: Désélectionne la photo actuelle.
- `updateSelectedPhotoAfterRename()`: Met à jour la photo après renommage.

**Méthodes d'actions sur les photos**:
- `renamePhoto()`: Renomme une photo.
- `deletePhoto()`: Supprime une photo.
- `launchCamera()`: Lance l'appareil photo.
- `checkIfNameExists()`: Vérifie si un nom de fichier existe déjà.
- `isSDCardAvailable()`: Vérifie si une carte SD est disponible.
- `importPhotosFromCamera()`: Importe des photos du dossier DCIM/Camera.
- `movePhotosFromCamera()`: Déplace des photos entre dossiers.
- `getCameraPhotos()`: Obtient les photos du dossier DCIM/Camera.
- `isTrashFile()`: Vérifie si un fichier est dans la corbeille.

**Méthodes du mode plein écran**:
- `setFullscreenMode()`: Active/désactive le mode plein écran.
- `setFullscreenPhotoIndex()`: Définit l'index de la photo en plein écran.
- `nextFullscreenPhoto()`: Passe à la photo suivante en plein écran.
- `previousFullscreenPhoto()`: Passe à la photo précédente en plein écran.
- `getPhotoIndex()`: Obtient l'index d'une photo.
- `resetWasInFullscreenMode()`: Réinitialise l'état de provenance du mode plein écran.
- `setWasInFullscreenMode()`: Mémorise que l'on vient du mode plein écran.

### PhotoRenamer
*Responsabilité*: Utilitaire pour le renommage des photos.

**Interface**:
- `RenameCallback`: Interface pour les callbacks de renommage.
  - `onRenameSuccess()`: Appelé quand le renommage réussit.
  - `onRenameInProgress()`: Appelé quand le renommage est en cours.
  - `onRenameComplete()`: Appelé à la fin du processus de renommage.

**Méthodes**:
- `renamePhoto()`: Méthode principale pour renommer une photo.
- `checkPermissions()`: Vérifie les permissions nécessaires.
- `renameOnSDCard()`: Renomme une photo sur carte SD.
- `renameOnInternalStorage()`: Renomme une photo sur stockage interne.
- `renameDCIMCameraPhoto()`: Méthode spéciale pour les photos de DCIM/Camera.
- `renameWithSAF()`: Renommage avec Storage Access Framework.
- `findFile()`: Recherche un fichier dans DocumentFile.
- `getMimeType()`: Détermine le type MIME d'un fichier.

## Fonctions principales

### Gestion des photos
- `PhotosViewModel.loadPhotos()`: Charge toutes les photos depuis la source actuelle.
- `PhotosViewModel.selectPhoto()`: Sélectionne une photo pour le renommage ou la visualisation.
- `PhotosViewModel.renamePhoto()`: Renomme une photo sélectionnée.
- `PhotosViewModel.deletePhoto()`: Supprime une photo sélectionnée.

### Gestion des dossiers
- `PhotosViewModel.loadAvailableFolders()`: Charge tous les dossiers disponibles.
- `PhotosViewModel.createNewFolder()`: Crée un nouveau dossier de destination.
- `PhotosViewModel.setCurrentFolder()`: Change le dossier courant.
- `PhotosViewModel.movePhotosFromCamera()`: Déplace des photos entre dossiers.

### Opérations caméra
- `PhotosViewModel.launchCamera()`: Lance l'application caméra native.
- `PhotosViewModel.importPhotosFromCamera()`: Importe les photos prises vers un dossier spécifique.

## Composants UI

### MainScreen
*Responsabilité*: Écran principal qui orchestre les différentes vues selon l'état.

**États observés**:
- `photos`: Liste des photos à afficher.
- `selectedPhoto`: Photo sélectionnée pour le détail.
- `currentFolder`: Dossier actuellement affiché.
- `availableFolders`: Liste des dossiers disponibles.
- `shouldOpenFolderDropdown`: Contrôle l'ouverture du sélecteur de dossier.
- `fullscreenMode`: Indique si le mode plein écran est activé.
- `fullscreenPhotoIndex`: Index de la photo affichée en plein écran.

**Comportements**:
- Affiche la grille de photos en mode normal.
- Bascule vers l'écran de détail quand une photo est sélectionnée.
- Bascule vers le mode plein écran quand activé.
- Gère les dialogues de sélection et création de dossiers.

### PhotoGridScreen
*Responsabilité*: Affiche les photos sous forme de grille.

**Paramètres**:
- `photos`: Liste des photos à afficher.
- `onPhotoClick`: Callback lorsqu'une photo est cliquée.
- `onPhotoZoom`: Callback pour activer le mode plein écran.
- `showImages`: Contrôle si les images sont affichées.

**Fonctionnalités**:
- Affichage des photos en grille responsive.
- Gestion des interactions utilisateur (clic, zoom).
- Indicateur de chargement pendant le chargement des images.

### PhotoDetailScreen
*Responsabilité*: Écran de détail et de renommage d'une photo.

**Paramètres**:
- `photo`: La photo à afficher et potentiellement renommer.
- `onBack`: Callback pour revenir à l'écran précédent.
- `onRename`: Callback pour renommer la photo.
- `onDelete`: Callback pour supprimer la photo.
- `viewModel`: Référence au ViewModel pour les opérations avancées.
- `startInRenamingMode`: Indique si l'écran doit démarrer en mode renommage.

**Fonctionnalités**:
- Affichage en grande taille de la photo.
- Interface de renommage avec validation.
- Options pour supprimer ou revenir à la grille.
- Prévisualisation du nouveau nom de fichier.

### PhotoFullscreenScreen
*Responsabilité*: Mode plein écran pour visualiser les photos.

**Paramètres**:
- `photos`: Liste des photos à naviguer.
- `initialPhotoIndex`: Index initial à afficher.
- `onBack`: Callback pour quitter le mode plein écran.
- `onRename`: Callback pour renommer la photo actuelle.
- `onLaunchCamera`: Callback pour lancer l'appareil photo.
- `viewModel`: Référence au ViewModel pour les opérations avancées.

**Fonctionnalités**:
- Navigation entre les photos par balayage.
- Contrôles discrets pour revenir à la grille ou renommer.
- Optimisé pour l'expérience de visualisation immersive.

### FolderPickerDialog
*Responsabilité*: Dialogue pour sélectionner ou créer des dossiers.

**Paramètres**:
- `currentFolder`: Dossier actuellement sélectionné.
- `availableFolders`: Liste des dossiers disponibles.
- `onFolderSelected`: Callback quand un dossier est sélectionné.
- `onMovePhotos`: Callback pour déplacer des photos entre dossiers.
- `onCreateFolder`: Callback pour créer un nouveau dossier.
- `onDismiss`: Callback pour fermer le dialogue.

**Fonctionnalités**:
- Liste des dossiers disponibles avec sélection.
- Option pour créer un nouveau dossier.
- Option pour déplacer des photos d'un dossier à un autre.

### CreateFolderDialog
*Responsabilité*: Dialogue pour créer un nouveau dossier.

**Paramètres**:
- `onDismiss`: Callback pour fermer le dialogue.
- `onConfirm`: Callback avec le nom du nouveau dossier à créer.

**Fonctionnalités**:
- Champ de saisie pour le nom du dossier.
- Validation du nom de dossier.
- Boutons de confirmation et d'annulation.

## Utilitaires et helpers

### Gestion des permissions
- Vérification adaptée selon la version d'Android.
- Demande appropriée pour les différents niveaux d'accès.
- Support spécial pour Android 11+ avec "All Files Access".

### Gestion des fichiers
- Détection des types d'images.
- Filtrage des fichiers "poubelle" ou temporaires.
- Préservation des métadonnées lors des opérations.

### Optimisations
- Chargement asynchrone via les coroutines Kotlin.
- Gestion d'état réactive avec Kotlin Flow.
- Fallbacks et méthodes alternatives pour la compatibilité.