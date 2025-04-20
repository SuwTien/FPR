# Bug de rotation dans FastPhotosRenamer

## Description du problème

L'application rencontrait un problème lors de la rotation de l'écran du téléphone : peu importe le dossier dans lequel l'utilisateur se trouvait, l'application revenait systématiquement au dossier par défaut (DCIM/Camera).

Ce problème se manifestait dans plusieurs contextes :
- Dans la grille de photos
- En mode plein écran (visualisation)
- Dans l'écran d'édition (renommage)

## Cause identifiée

Le problème provenait de la méthode `onResume()` dans `MainActivity.kt` qui :
1. Appelait `ensureAppFolderExists()` à chaque reprise du cycle de vie
2. Cette méthode forçait le dossier courant à être DCIM/Camera
3. Puis `loadPhotos(this)` était appelé, chargeant les photos du dossier par défaut

Lors d'une rotation de l'écran, l'activité est détruite et recréée, ce qui déclenche à nouveau le cycle de vie, incluant `onResume()`.

## Solution implémentée

Nous avons modifié deux éléments clés :

1. Créé une nouvelle méthode `ensureAppFolderExistsWithoutChangingCurrentFolder()` qui vérifie uniquement l'existence des dossiers sans modifier le dossier courant

2. Modifié la méthode `onResume()` pour :
   - Utiliser cette nouvelle méthode
   - Recharger les photos du dossier actuellement sélectionné par l'utilisateur et non pas systématiquement le dossier par défaut

```kotlin
override fun onResume() {
    super.onResume()
    
    // Vérifier l'existence des dossiers sans changer le dossier courant
    ensureAppFolderExistsWithoutChangingCurrentFolder()
    
    // Recharger les photos du dossier courant, pas du dossier par défaut
    if (areMediaPermissionsGranted()) {
        val currentFolder = viewModel.currentFolder.value
        if (currentFolder.isNotEmpty()) {
            viewModel.loadPhotosFromFolder(this, currentFolder)
        } else {
            // Seulement si aucun dossier n'est sélectionné, on charge le dossier par défaut
            viewModel.loadPhotos(this)
        }
    }
}
```

Cette correction garantit que le contexte de navigation de l'utilisateur est préservé lors d'événements du cycle de vie comme la rotation de l'écran.

## Date de la correction
20 avril 2025