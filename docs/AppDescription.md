# Documentation FastPhotosRenamer

## Table des matières
1. [Introduction](#introduction)
2. [Architecture de l'application](#architecture-de-lapplication)
3. [Structure des fichiers](#structure-des-fichiers)
4. [Modèles de données](#modèles-de-données)
5. [ViewModel](#viewmodel)
6. [Interfaces utilisateur](#interfaces-utilisateur)
7. [Stockage et renommage](#stockage-et-renommage)
8. [Fonctionnalités principales](#fonctionnalités-principales)
9. [Optimisations](#optimisations)
10. [Résumé technique](#résumé-technique)

## Introduction

FastPhotosRenamer est une application Android optimisée pour le renommage rapide et efficace de photos. Conçue pour être à la fois légère et pratique, elle permet aux utilisateurs de gérer facilement leur collection d'images tout en offrant des fonctionnalités essentielles telles que la visualisation, le renommage, la suppression et l'organisation en dossiers.

L'application est spécialement optimisée pour une utilisation personnelle, mettant l'accent sur la rapidité et l'efficacité. Elle se distingue par sa capacité à traiter les photos aussi bien dans le stockage interne que sur cartes SD (avec certaines limitations), et par son interface utilisateur intuitive développée avec Jetpack Compose.

## Architecture de l'application

FastPhotosRenamer suit le pattern d'architecture MVVM (Model-View-ViewModel) :

- **Model**: Représentation des données photos (`PhotoModel`)
- **View**: Interfaces utilisateur composées avec Jetpack Compose
- **ViewModel**: Logique de présentation et traitement des données (`PhotosViewModel`)

L'application utilise également les principes de la programmation réactive avec Kotlin Flow pour la gestion des états et des événements, permettant une interface utilisateur réactive et fluide.

## Structure des fichiers

### Package principal : `fr.bdst.fastphotosrenamer`

#### Activité principale
- `MainActivity.kt` : Point d'entrée de l'application, gère les permissions et initialise l'interface utilisateur

#### Sous-packages
- `model` : Contient les modèles de données
  - `PhotoModel.kt` : Modèle représentant une photo

- `viewmodel` : Contient le ViewModel
  - `PhotosViewModel.kt` : Gère la logique métier et l'état de l'application

- `ui` : Contient les composants de l'interface utilisateur
  - `screens` : Écrans principaux
    - `MainScreen.kt` : Écran principal qui organise les différentes vues
    - `PhotoGridScreen.kt` : Grille d'affichage des photos
    - `PhotoDetailScreen.kt` : Écran de détail et de renommage d'une photo
    - `PhotoFullscreenScreen.kt` : Mode plein écran pour visualiser les photos
  - `theme` : Configuration du thème de l'application
    - `Color.kt` : Définition des couleurs
    - `Theme.kt` : Configuration du thème
    - `Type.kt` : Configuration de la typographie
  - `components` : Composants UI réutilisables
    - `FolderPickerDialog.kt` : Dialogue de sélection de dossier

- `storage` : Fonctionnalités liées au stockage et renommage
  - `PhotoRenamer.kt` : Utilitaire pour le renommage des photos

## Modèles de données

### PhotoModel

Ce modèle représente une photo dans l'application :

```kotlin
data class PhotoModel(
    val id: String,       // Identifiant unique de la photo
    val uri: Uri,         // URI pour accéder à la photo
    val name: String,     // Nom du fichier
    val path: String,     // Chemin complet du fichier
    val file: File        // Objet File référençant la photo
)
```

## ViewModel

### PhotosViewModel

Le `PhotosViewModel` est le cœur de l'application, gérant toute la logique métier :

#### États principaux
- `photos`: Liste des photos actuellement affichées
- `selectedPhoto`: Photo sélectionnée pour le renommage ou la visualisation
- `currentFolder`: Dossier courant d'où les photos sont chargées
- `availableFolders`: Liste des dossiers disponibles
- `fullscreenMode`: État du mode plein écran
- `isLoading`: Indicateur de chargement en cours

#### Fonctionnalités principales
1. **Gestion des photos**
   - `loadPhotos`: Charge les photos du stockage interne
   - `loadPhotosFromSDCard`: Charge les photos depuis une carte SD
   - `loadPhotosFromFolder`: Charge les photos d'un dossier spécifique

2. **Gestion des dossiers**
   - `loadAvailableFolders`: Charge la liste des dossiers disponibles
   - `createNewFolder`: Crée un nouveau dossier
   - `setCurrentFolder`: Définit le dossier courant

3. **Opérations sur les photos**
   - `selectPhoto`: Sélectionne une photo pour visualisation ou renommage
   - `renamePhoto`: Renomme une photo
   - `deletePhoto`: Supprime une photo
   - `movePhotosFromCamera`: Déplace des photos entre dossiers

4. **Navigation et affichage**
   - `setFullscreenMode`: Active/désactive le mode plein écran
   - `nextFullscreenPhoto`: Passe à la photo suivante en mode plein écran
   - `previousFullscreenPhoto`: Passe à la photo précédente en mode plein écran

## Interfaces utilisateur

### MainScreen

L'écran principal qui orchestre les différentes vues selon l'état de l'application :
- Affiche la grille de photos en mode normal
- Bascule vers l'écran de détail quand une photo est sélectionnée
- Bascule vers le mode plein écran quand activé
- Gère la barre supérieure et inférieure avec les actions principales

### PhotoGridScreen

Affiche les photos sous forme de grille :
- Chargement optimisé des miniatures
- Gestion du clic pour sélectionner une photo
- Support du zoom pour passer en mode plein écran

### PhotoDetailScreen

Écran de détail et de renommage d'une photo :
- Affichage en grande taille de la photo
- Champ de renommage avec validation
- Options pour supprimer ou revenir à la grille

### PhotoFullscreenScreen

Mode plein écran pour visualiser les photos :
- Navigation entre les photos par balayage
- Contrôles discrets pour revenir à la grille ou renommer
- Optimisé pour l'expérience de visualisation

## Stockage et renommage

### PhotoRenamer

Classe utilitaire qui gère toutes les opérations de renommage de photos :

#### Fonctionnalités
- Renommage adaptif selon la version d'Android
- Méthodes spécifiques pour le stockage interne et externe
- Gestion des erreurs et fallbacks
- Préservation des métadonnées lors du renommage
- Interface de callback pour signaler la progression et le succès

#### Processus de renommage
1. Vérification des permissions
2. Détection du type de stockage (interne/externe)
3. Sélection de la méthode de renommage adaptée
4. Tentatives séquentielles avec différentes approches en cas d'échec
5. Mise à jour du MediaStore pour maintenir la cohérence

## Fonctionnalités principales

### 1. Renommage rapide des photos
- Interface optimisée pour le renommage en un minimum d'étapes
- Préservation des métadonnées des photos (date, taille)
- Compatibilité avec différentes versions d'Android

### 2. Organisation en dossiers
- Création et gestion de dossiers personnalisés
- Déplacement des photos entre dossiers
- Accès rapide aux dossiers récemment utilisés

### 3. Visualisation optimisée
- Mode grille pour aperçu rapide
- Mode plein écran avec navigation fluide
- Zoom et gestes intuitifs

### 4. Gestion de la caméra
- Prise de photos directe depuis l'application
- Import automatique des nouvelles photos
- Organisation immédiate des photos prises

### 5. Opérations sur les fichiers
- Suppression des photos avec confirmation
- Import des photos de la caméra vers les dossiers personnalisés
- Détection intelligente des types de fichiers image

## Optimisations

### Performance
- Chargement asynchrone des images via Corutines Kotlin
- Mise en cache des miniatures pour une navigation fluide
- Opérations de fichiers en arrière-plan

### Expérience utilisateur
- Interface réactive grâce à Jetpack Compose
- Retours visuels pour toutes les opérations
- Modes d'affichage adaptés à différents usages

### Compatibilité
- Support des différentes versions d'Android (API 26+)
- Gestion adaptative des permissions selon la version d'Android
- Méthodes alternatives pour les opérations critiques

## Résumé technique

FastPhotosRenamer est une application moderne développée avec :
- **Langage** : Kotlin
- **UI Framework** : Jetpack Compose
- **Architecture** : MVVM avec Kotlin Flow
- **Stockage** : MediaStore API et accès direct aux fichiers
- **Permissions** : Gestion adaptative selon les versions Android
- **Optimisations** : Chargement asynchrone, mise en cache, transitions fluides

L'application est spécifiquement conçue pour être rapide, légère et adaptée à un usage personnel, avec une concentration particulière sur l'efficacité du renommage et de l'organisation des photos.