package fr.bdst.fastphotosrenamer.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import fr.bdst.fastphotosrenamer.model.PhotoModel
import fr.bdst.fastphotosrenamer.viewmodel.PhotosViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    photo: PhotoModel,
    onBack: () -> Unit,
    onRename: (PhotoModel, String, Context) -> Boolean,
    onDelete: (PhotoModel, Context) -> Boolean,
    viewModel: PhotosViewModel
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // États pour le zoom et le déplacement
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // État pour le mode de renommage
    var isRenaming by remember { mutableStateOf(false) }

    // Extraire l'extension du fichier
    val extension = photo.name.substringAfterLast(".", "")
    // Extraire le nom sans extension pour l'affichage
    val nameWithoutExtension = photo.name.substringBeforeLast(".", photo.name)

    // Utiliser TextFieldValue pour gérer la sélection du texte
    var textFieldValue by remember { 
        mutableStateOf(
            TextFieldValue(
                text = nameWithoutExtension,
                selection = TextRange(0, nameWithoutExtension.length) // Sélectionner tout par défaut
            )
        ) 
    }

    // État pour le clavier numérique ou alphabétique
    var useNumericKeyboard by remember { mutableStateOf(true) }

    // État pour suivre si le nom existe déjà
    var nameAlreadyExists by remember { mutableStateOf(false) }

    // État pour le focus du TextField
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Vérifier si le nom existe chaque fois que le texte change
    LaunchedEffect(textFieldValue.text) {
        if (isRenaming) {
            val newFullName = if (extension.isNotEmpty()) {
                "${textFieldValue.text}.$extension"
            } else {
                textFieldValue.text
            }

            nameAlreadyExists = viewModel.checkIfNameExists(photo.path, newFullName)
        }
    }

    // Collecter l'état d'avancement du renommage
    val isRenameInProgress by viewModel.isRenameOperationInProgress.collectAsState()

    // Vérifier si le bouton de renommage est activé
    val isRenameEnabled = textFieldValue.text.isNotEmpty() && 
                         textFieldValue.text != nameWithoutExtension && 
                         !nameAlreadyExists && !isRenameInProgress

    // BackHandler général pour retourner à l'écran principal
    BackHandler {
        if (isRenaming) {
            // Si on est en mode renommage, d'abord sortir du mode
            isRenaming = false
            textFieldValue = TextFieldValue(
                text = nameWithoutExtension,
                selection = TextRange(0, nameWithoutExtension.length)
            )
        } else {
            // Sinon, retourner à l'écran principal
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = nameWithoutExtension) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                    }
                }
            )
        },
        bottomBar = {
            if (isRenaming) {
                // Mode renommage dans la BottomBar avec plusieurs lignes
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .imePadding() // Important: permet à la BottomBar de rester visible au-dessus du clavier
                ) {
                    // Première ligne: champ de texte avec indicateur d'extension
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            singleLine = true,
                            label = { Text("Nouveau nom") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            trailingIcon = {
                                if (extension.isNotEmpty()) {
                                    Text(".$extension", Modifier.padding(end = 8.dp))
                                }
                            },
                            isError = nameAlreadyExists,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (useNumericKeyboard) KeyboardType.Number else KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (isRenameEnabled) {
                                        val newFullName = if (extension.isNotEmpty()) {
                                            "${textFieldValue.text}.$extension"
                                        } else {
                                            textFieldValue.text
                                        }
                                        keyboardController?.hide()
                                        val success = onRename(photo, newFullName, context)
                                        if (success) {
                                            isRenaming = false
                                            coroutineScope.launch {
                                                onBack()
                                            }
                                        }
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                        )
                    }
                    
                    // Message d'erreur si le nom existe déjà
                    if (nameAlreadyExists) {
                        Text(
                            text = "Ce nom de fichier existe déjà",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        )
                    }
                    
                    // Nouvelle ligne pour l'option clavier alpha
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = !useNumericKeyboard,  // Inversé: coché = clavier alpha, non coché = clavier numérique
                            onCheckedChange = { useNumericKeyboard = !it },
                            modifier = Modifier.size(20.dp)  // Checkbox plus petite
                        )
                        Text(
                            text = "Clavier alpha",
                            style = MaterialTheme.typography.bodySmall,  // Plus petit texte
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    
                    // Dernière ligne: boutons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly, // Répartir l'espace également
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bouton annuler qui prend 45% de la largeur
                        OutlinedButton(
                            onClick = { 
                                isRenaming = false 
                                textFieldValue = TextFieldValue(
                                    text = nameWithoutExtension,
                                    selection = TextRange(0, nameWithoutExtension.length)
                                )
                            },
                            modifier = Modifier.weight(1f) // Prend l'espace disponible
                        ) {
                            Text("Annuler")
                        }
                        
                        // Espacement entre les boutons
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        // Bouton valider qui prend 45% de la largeur
                        Button(
                            onClick = { 
                                if (isRenameEnabled) {
                                    val newFullName = if (extension.isNotEmpty()) {
                                        "${textFieldValue.text}.$extension"
                                    } else {
                                        textFieldValue.text
                                    }
                                    keyboardController?.hide()
                                    val success = onRename(photo, newFullName, context)
                                    if (success) {
                                        isRenaming = false
                                        coroutineScope.launch {
                                            onBack()
                                        }
                                    }
                                }
                            },
                            enabled = isRenameEnabled,
                            modifier = Modifier.weight(1f), // Prend l'espace disponible
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            if (isRenameInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Valider")
                            }
                        }
                    }
                }
            } else {
                // Mode normal - BottomBar standard avec bouton Renommer
                BottomAppBar {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                // Réinitialiser le textFieldValue avec sélection de tout le texte
                                textFieldValue = TextFieldValue(
                                    text = nameWithoutExtension,
                                    selection = TextRange(0, nameWithoutExtension.length)
                                )
                                isRenaming = true
                                // Demander le focus après un court délai
                                coroutineScope.launch {
                                    delay(100)
                                    focusRequester.requestFocus()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(0.8f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Renommer")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Image zoomable
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photo.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = photo.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            // Limiter le zoom entre 1x et 5x
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            
                            // Amplifier le déplacement pour plus de fluidité (multiplier par 1.5)
                            val panX = pan.x * 1.5f
                            val panY = pan.y * 1.5f
                            
                            // Calculer les nouveaux offsets avec des limites
                            val maxOffset = (scale - 1) * 500
                            
                            // Appliquer les déplacements amplifiés
                            offsetX = (offsetX + panX).coerceIn(-maxOffset, maxOffset)
                            offsetY = (offsetY + panY).coerceIn(-maxOffset, maxOffset)
                        }
                    }
            )
            
            // Instructions pour zoomer (texte discret)
            if (scale <= 1.05f && !isRenaming) {  // Montrer seulement quand non-zoomé et pas en mode renommage
                Text(
                    text = "Pincez pour zoomer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
    
    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = { 
                val success = onDelete(photo, context)
                if (success) {
                    showDeleteDialog = false
                }
            },
            photoName = nameWithoutExtension
        )
    }
}

@Composable
fun DeleteConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    photoName: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supprimer la photo") },
        text = { Text("Êtes-vous sûr de vouloir supprimer $photoName ?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Supprimer")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}