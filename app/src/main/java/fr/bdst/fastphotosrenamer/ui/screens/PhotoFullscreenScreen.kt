package fr.bdst.fastphotosrenamer.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import fr.bdst.fastphotosrenamer.model.PhotoModel
import fr.bdst.fastphotosrenamer.viewmodel.PhotosViewModel
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
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
    
    // Obtenir les dimensions de l'écran pour un meilleur calcul des limites de zoom
    val configuration = LocalConfiguration.current
    val screenWidth = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            // Désactiver complètement le swipe utilisateur
            userScrollEnabled = false,
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
                            // ou si on est en train de zoomer
                            if (scale > 1.01f || zoom > 1.01f) {
                                // Calculer la nouvelle échelle
                                val newScale = (scale * zoom).coerceIn(1f, 8f)  // Zoom max à 8x
                                
                                // Calculer les limites de déplacement basées sur les dimensions de l'écran
                                val horizontalOverflow = (screenWidth * newScale - screenWidth) / 2f
                                val verticalOverflow = (screenHeight * newScale - screenHeight) / 2f
                                
                                // Amplifier le déplacement pour plus de fluidité
                                val panX = pan.x * 2.0f  // Plus réactif
                                val panY = pan.y * 2.0f
                                
                                // Calculer les nouveaux offsets
                                val newOffsetX = offsetX + panX
                                val newOffsetY = offsetY + panY
                                
                                // Appliquer l'échelle et les déplacements avec des limites
                                scale = newScale
                                offsetX = newOffsetX.coerceIn(-horizontalOverflow, horizontalOverflow)
                                offsetY = newOffsetY.coerceIn(-verticalOverflow, verticalOverflow)
                            } else {
                                // Si on essaie de dé-zoomer en dessous de 1, réinitialiser à 1
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    // Uniquement double tap pour réinitialiser le zoom, pas de tap simple pour sortir
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                // Réinitialiser le zoom et les offsets
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        )
                    },
                contentAlignment = Alignment.Center
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
            }
        }
        
        // Bouton simplifié en bas de l'écran avec le nom de la photo
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .clickable {
                    val currentPhoto = if (photos.isNotEmpty() && pagerState.currentPage < photos.size) {
                        photos[pagerState.currentPage]
                    } else null
                    
                    if (currentPhoto != null) {
                        // Sauvegarder l'index courant avant de naviguer
                        viewModel.setFullscreenPhotoIndex(pagerState.currentPage)
                        viewModel.setWasInFullscreenMode(true)
                        // Désactiver temporairement le mode plein écran pour aller à l'écran de renommage
                        viewModel.setFullscreenMode(false)
                        // Sélectionner la photo pour passer à l'écran de détail
                        viewModel.selectPhoto(currentPhoto)
                    }
                }
                .background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val currentPhoto = if (photos.isNotEmpty() && pagerState.currentPage < photos.size) {
                photos[pagerState.currentPage]
            } else null
            
            // Nom de la photo sans extension
            Text(
                text = currentPhoto?.name?.substringBeforeLast(".", currentPhoto?.name ?: "") ?: "",
                color = Color.White,
                style = TextStyle(
                    fontSize = 16.sp,
                    shadow = Shadow(
                        color = Color.Black,
                        offset = Offset(1f, 1f),
                        blurRadius = 2f
                    ),
                    textAlign = TextAlign.Center
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Indicateur de position dans la galerie en haut
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${pagerState.currentPage + 1}/${photos.size}",
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        
        // Flèche gauche (visible uniquement s'il y a une photo précédente)
        if (pagerState.currentPage > 0) {
            Box(
                modifier = Modifier
                    .width(40.dp)  // Largeur de bande réduite de moitié sur le côté gauche (80dp -> 40dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .size(50.dp)
                        // Arrière-plan complètement invisible (alpha = 0)
                        .background(
                            color = Color.Black.copy(alpha = 0f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Photo précédente",
                        // Flèche légèrement transparente
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
        
        // Flèche droite (visible uniquement s'il y a une photo suivante)
        if (pagerState.currentPage < pagerState.pageCount - 1) {
            Box(
                modifier = Modifier
                    .width(40.dp)  // Largeur de bande réduite de moitié sur le côté droit (80dp -> 40dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(50.dp)
                        // Arrière-plan complètement invisible (alpha = 0)
                        .background(
                            color = Color.Black.copy(alpha = 0f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Photo suivante",
                        // Flèche légèrement transparente
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(30.dp)
                            .graphicsLayer { 
                                rotationY = 180f 
                            }
                    )
                }
            }
        }
    }
}