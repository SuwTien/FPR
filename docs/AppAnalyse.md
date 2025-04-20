# FastPhotosRenamer - Analyse Technique

## Architecture de l'application

L'application FastPhotosRenamer est développée avec :
- **Langage et UI** : Kotlin et Jetpack Compose pour l'interface utilisateur
- **Architecture** : MVVM (Model-View-ViewModel)
- **Versions d'Android** : Gestion adaptative des permissions et fonctionnalités selon les versions d'Android (API 26-33+)

## Fonctionnalités principales

### 1. Affichage et navigation des photos
- Affichage en grille à 3 colonnes des photos
- Mode plein écran avec navigation entre photos (swipe gauche/droite)
- Vue détaillée pour renommage et suppression
- Navigation optimisée entre les différents modes d'affichage

### 2. Gestion des dossiers
- Création de nouveaux dossiers dans DCIM/FPR
- Navigation entre dossiers (DCIM/Camera par défaut)
- Déplacement de photos entre dossiers avec préservation des métadonnées
- Interface conviviale pour la sélection des dossiers

### 3. Prise de photos
- Lancement direct de l'appareil photo via Intent (INTENT_ACTION_STILL_IMAGE_CAMERA)
- Actualisation automatique de la galerie après la prise de photos

### 4. Renommage et suppression
- Renommage des photos avec gestion des erreurs et collisions de noms
- Suppression avec différentes stratégies selon la version d'Android
- Actualisation en temps réel de l'interface après les opérations

## Points importants de l'implémentation

### MainActivity.kt
- Gestion des permissions d'accès aux médias et au stockage selon versions Android
- Création du dossier de l'application (DCIM/FPR) si nécessaire
- Initialisation du ViewModel et affichage de l'interface principale
- DCIM/Camera utilisé comme dossier par défaut, avec fallback sur DCIM/FPR

### PhotosViewModel.kt
- Gestion sophistiquée des dossiers et photos
- Multiples méthodes pour charger les photos (stockage interne/externe)
- Mécanismes pour préserver les métadonnées lors des déplacements
- Adaptation aux différentes versions d'Android (API 26 à 33+)
- Mode plein écran avec navigation et retour à la grille

### Interface utilisateur
- **MainScreen.kt** : Écran principal avec barre supérieure pour la sélection de dossiers
- **PhotoGridScreen.kt** : Grille de photos avec gestion des clics simples (renommage) et longs (plein écran)
- **PhotoFullscreenScreen.kt** : Mode plein écran pour visualiser les images
- **PhotoDetailScreen.kt** : Écran de détail pour renommer/supprimer les photos

## Points d'attention

- L'application a été conçue pour un usage personnel, optimisée pour la rapidité et la praticité
- Des adaptations spécifiques ont été faites pour gérer les particularités des différentes versions d'Android
- Préférence pour le dossier DCIM/Camera comme répertoire par défaut
- Gestion des erreurs intégrée pour les opérations critiques de fichiers

## Optimisations

- Chargement rapide des images avec Coil et mise en cache
- Gestion efficace des métadonnées lors des opérations sur les fichiers
- Interface utilisateur intuitive et réactive
- Transitions fluides entre les différents modes d'affichage
