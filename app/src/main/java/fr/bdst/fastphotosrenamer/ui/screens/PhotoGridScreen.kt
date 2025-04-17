package fr.bdst.fastphotosrenamer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import fr.bdst.fastphotosrenamer.model.PhotoModel

@Composable
fun PhotoGridScreen(
    photos: List<PhotoModel>,
    onPhotoClick: (PhotoModel) -> Unit,
    onPhotoZoom: (PhotoModel) -> Unit,
    showImages: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (showImages) {
        // Affichage en grille avec images
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),  // Retour à 3 colonnes
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier.padding(4.dp)
        ) {
            items(photos) { photo ->
                PhotoItem(
                    photo = photo, 
                    onClick = { onPhotoClick(photo) },
                    onFullscreen = { onPhotoZoom(photo) },
                    showImage = true
                )
            }
        }
    } else {
        // Affichage en liste quand les images sont désactivées
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = modifier.padding(4.dp)
        ) {
            items(photos) { photo ->
                val nameWithoutExtension = photo.name.substringBeforeLast(".", photo.name)
                ListItem(
                    headlineContent = { 
                        Text(
                            text = nameWithoutExtension,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Photo,
                            contentDescription = "Photo",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPhotoClick(photo) }
                )
            }
        }
    }
}

@Composable
fun PhotoItem(
    photo: PhotoModel, 
    onClick: () -> Unit,
    onFullscreen: () -> Unit,
    showImage: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            // Utiliser uniquement pointerInput pour gérer à la fois le clic simple et le clic long
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onFullscreen() }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // La partie image ou icône (prend 80% de la hauteur)
            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (showImage) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(photo.uri)
                            .crossfade(true)
                            .size(300)
                            .memoryCacheKey(photo.uri.toString())
                            .diskCacheKey(photo.uri.toString())
                            .build(),
                        contentDescription = photo.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = "Image",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // La partie nom (prend 20% de la hauteur)
            Box(
                modifier = Modifier
                    .weight(0.2f)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Extraire le nom sans extension
                val nameWithoutExtension = photo.name.substringBeforeLast(".", photo.name)
                
                Text(
                    text = nameWithoutExtension,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
