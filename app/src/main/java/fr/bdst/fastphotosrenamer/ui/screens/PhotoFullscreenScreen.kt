package fr.bdst.fastphotosrenamer.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import fr.bdst.fastphotosrenamer.model.PhotoModel
import fr.bdst.fastphotosrenamer.viewmodel.PhotosViewModel
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
fun PhotoFullscreenScreen(
    photos: List<PhotoModel>,
    initialPhotoIndex: Int,
    onBack: () -> Unit,
    onRename: (PhotoModel, Context) -> Unit,
    onLaunchCamera: () -> Unit,
    viewModel: PhotosViewModel
) {
    val pagerState = rememberPagerState(
        initialPage = initialPhotoIndex,
        pageCount = { photos.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Observer les changements de page pour mettre à jour le ViewModel
    LaunchedEffect(pagerState.currentPage) {
        viewModel.setFullscreenPhotoIndex(pagerState.currentPage)
    }
    
    // Back handler pour sortir du mode plein écran
    BackHandler {
        onBack()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // HorizontalPager pour naviguer entre les photos
        // Configuration optimisée pour la navigation entre photos
        val flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            // Rendre le fling plus sensible pour faciliter le défilement
            lowVelocityAnimationSpec = androidx.compose.animation.core.tween(
                durationMillis = 300
            )
        )
        
        HorizontalPager(
            state = pagerState,
            // Utiliser notre configuration de fling personnalisée
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val photo = photos[page]
            
            // Photo zoomable
            var scale by remember { mutableStateOf(1f) }
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }
            
            // On réinitialise le zoom lorsqu'on change de page
            LaunchedEffect(page) {
                scale = 1f
                offsetX = 0f
                offsetY = 0f
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    // Gestion du zoom et du déplacement
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            // N'activer les gestes de zoom et de déplacement que si le zoom est déjà actif
                            // ou si on est en train de zoomer (pas de déplacement quand scale=1)
                            if (scale > 1.01f || zoom > 1.01f) {
                                // Limiter le zoom entre 1x et 5x
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                
                                // Amplifier le déplacement pour plus de fluidité
                                val panX = pan.x * 1.5f
                                val panY = pan.y * 1.5f
                                
                                // Calculer les limites de déplacement basées sur le niveau de zoom
                                val maxOffset = (scale - 1) * 500
                                
                                // Appliquer les déplacements amplifiés avec limites
                                offsetX = (offsetX + panX).coerceIn(-maxOffset, maxOffset)
                                offsetY = (offsetY + panY).coerceIn(-maxOffset, maxOffset)
                            } else {
                                // Si on essaie de dé-zoomer en dessous de 1, réinitialiser à 1
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Double-tap pour réinitialiser le zoom + boutons de navigation rapide
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    // Réinitialiser le zoom et les offsets
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            )
                        }
                ) {
                    // Affichage de l'image
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(photo.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = photo.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Zones de toucher invisible pour la navigation rapide (droite/gauche)
                    if (scale <= 1.01f) { // Seulement si l'image n'est pas zoomée
                        // Zone de toucher gauche (photo précédente)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(80.dp)
                                .align(Alignment.CenterStart)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            if (pagerState.currentPage > 0) {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(
                                                        pagerState.currentPage - 1
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                        )
                        
                        // Zone de toucher droite (photo suivante)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(80.dp)
                                .align(Alignment.CenterEnd)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            if (pagerState.currentPage < photos.size - 1) {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(
                                                        pagerState.currentPage + 1
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                        )
                    }
                }
            }
        }
        
        // Overlay semi-transparent en haut
        TopAppBar(
            title = {
                val currentPhoto = if (photos.isNotEmpty() && pagerState.currentPage < photos.size) {
                    photos[pagerState.currentPage]
                } else null
                
                Text(
                    text = currentPhoto?.name?.substringBeforeLast(".", currentPhoto?.name ?: "") ?: "",
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.5f)
            ),
            modifier = Modifier.statusBarsPadding()
        )
        
        // Overlay au bas de l'écran
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            // Bouton Renommer qui prend toute la largeur maintenant
            Button(
                onClick = { 
                    if (photos.isNotEmpty() && pagerState.currentPage < photos.size) {
                        onRename(photos[pagerState.currentPage], context)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Renommer")
            }
        }
        
        // Indicateurs de navigation latérale
        // Flèche gauche (visible uniquement s'il y a une photo précédente)
        if (pagerState.currentPage > 0) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Photo précédente",
                    tint = Color.White.copy(alpha = 0.3f)
                )
            }
        }
        
        // Flèche droite (visible uniquement s'il y a une photo suivante)
        if (pagerState.currentPage < photos.size - 1) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Photo suivante",
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.graphicsLayer { 
                        rotationY = 180f 
                    }
                )
            }
        }
    }
}