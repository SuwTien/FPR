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
- **Extraction du gestionnaire de dossiers** : Sortir la logique de gestion des dossiers dans une classe `FolderManager`

### Phase 3 : Architecture améliorée
- **Implémentation du Repository Pattern** : Créer une couche d'abstraction `PhotoRepository` pour l'accès aux données
- **Refactorisation des callbacks en Flow** : Moderniser la gestion asynchrone avec Kotlin Flow

### Phase 4 : Qualité et maintenance
- **Documentation complète** : Améliorer les commentaires et la documentation
- **Tests unitaires** : Ajouter des tests pour les fonctionnalités critiques

## État d'avancement

- [x] Début du plan : 21 avril 2025
- [ ] Phase 1 complétée
- [ ] Phase 2 complétée  
- [ ] Phase 3 complétée
- [ ] Phase 4 complétée