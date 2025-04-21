# Plan de refactorisation de FastPhotosRenamer

Ce document décrit le plan de refactorisation progressif de l'application FastPhotosRenamer pour améliorer sa structure, sa maintenabilité et sa robustesse.

## Motivation

Le code actuel présente plusieurs opportunités d'amélioration :
- Le `PhotosViewModel` est devenu très volumineux avec trop de responsabilités
- Le code contient des fonctionnalités abandonnées (gestion de carte SD) qui compliquent la base de code
- La gestion des chemins de fichiers n'est pas suffisamment robuste et centralisée
- Les tests et la documentation pourraient être améliorés

## Plan de refactorisation en 4 phases

### Phase 1 : Nettoyage et simplification
- **Nettoyage du code lié à la carte SD** : Puisque cette fonctionnalité a été abandonnée, retirer proprement ce code pour alléger la base de code
- **Centralisation de la gestion des chemins de fichiers** : Créer une classe utilitaire `FilePathUtils` pour normaliser les chemins et détecter les dossiers spéciaux

### Phase 2 : Séparation des responsabilités
- **Extraction du gestionnaire de photos** : Sortir la logique de chargement et de pagination des photos dans une classe `PhotoManager`
  - *Étape 2.1* : Créer `PhotoPaginator` pour la pagination des photos ✓
  - *Étape 2.2* : Étendre les fonctionnalités vers un `PhotoManager` complet ✓
    - Déplacer les fonctions de chargement des photos ✓
    - Déplacer les fonctions de renommage et de suppression ✓
    - Déplacer les fonctions d'import/export ✓
  - *Étape 2.3* : Suppression de `PhotoPaginator` désormais obsolète ✓
- **Extraction du gestionnaire de dossiers** : Sortir la logique de gestion des dossiers dans une classe `FolderManager`
  - *Étape 2.4* : Créer la classe `FolderManager` ✓
    - Déplacer les fonctions de chargement des dossiers ✓
    - Déplacer les fonctions de création des dossiers ✓
    - Déplacer les fonctions de vérification et de navigation ✓
  - *Étape 2.5* : Adapter le `PhotosViewModel` pour utiliser ces nouvelles classes ✓
  - *Étape 2.6* : Tests et validation des modifications ✓

### Phase 3 : Architecture améliorée
- **Implémentation du Repository Pattern** : Créer une couche d'abstraction `PhotoRepository` pour l'accès aux données
  - *Étape 3.1* : Définir les interfaces des Repository
  - *Étape 3.2* : Créer les implémentations concrètes des Repository
  - *Étape 3.3* : Adapter les classes Manager existantes
- **Refactorisation des callbacks en Flow** : Moderniser la gestion asynchrone avec Kotlin Flow
  - *Étape 3.4* : Convertir les callbacks en Flows
  - *Étape 3.5* : Adapter le ViewModel
  - *Étape 3.6* : Tests et validation

### Phase 4 : Qualité et maintenance
- **Documentation complète** : Améliorer les commentaires et la documentation
- **Tests unitaires** : Ajouter des tests pour les fonctionnalités critiques

## État d'avancement

- [x] Début du plan : 21 avril 2025
- [x] Phase 1 complétée
- [x] Phase 2 complétée  
  - [x] Étape 2.1 : Création de `PhotoPaginator` pour la pagination
  - [x] Étape 2.2 : Extension vers `PhotoManager` complet
  - [x] Étape 2.3 : Suppression de `PhotoPaginator` (obsolète)
  - [x] Étape 2.4 : Création de `FolderManager`
  - [x] Étape 2.5 : Adaptation du `PhotosViewModel`
  - [x] Étape 2.6 : Tests et validation
- [ ] Phase 3 complétée
  - [ ] Étape 3.1 : Définir les interfaces des Repository
  - [ ] Étape 3.2 : Créer les implémentations concrètes
  - [ ] Étape 3.3 : Adapter les classes Manager existantes
  - [ ] Étape 3.4* : Convertir les callbacks en Flows
  - [ ] Étape 3.5 : Adapter le ViewModel
  - [ ] Étape 3.6 : Tests et validation
- [ ] Phase 4 complétée