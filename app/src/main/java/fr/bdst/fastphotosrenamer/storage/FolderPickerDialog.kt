package fr.bdst.fastphotosrenamer.ui.components

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FolderPickerDialog(
    currentFolder: String,
    availableFolders: List<String>,
    onFolderSelected: (String) -> Unit,
    onMovePhotos: (String, String) -> Unit, // Source, Destination
    onCreateFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var sourceFolder by remember { mutableStateOf("") }
    var destinationFolder by remember { mutableStateOf("") }
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val dialogHeight = screenHeight * 0.9f  // 90% de la hauteur de l'écran
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = dialogHeight)
                    .padding(16.dp)
                    .widthIn(max = 480.dp)
            ) {
                // Titre
                Text(
                    text = "Dossier de travail",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Liste
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                ) {
                    items(availableFolders) { folder ->
                        val (displayName, isSubFolder) = getFormattedFolderName(folder)
                        val isSelected = folder == currentFolder
                        val isCamera = folder.endsWith("/DCIM/Camera")
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onFolderSelected(folder)
                                    onDismiss()
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween // Important pour aligner à gauche et à droite
                        ) {
                            // À GAUCHE: Nom du dossier avec icône optionnelle
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Nom du dossier
                                Text(
                                    text = displayName,
                                    // Utiliser des styles plus grands
                                    style = if (isSelected) 
                                        MaterialTheme.typography.titleMedium 
                                    else 
                                        MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                            
                            // À DROITE: Bouton d'action (seulement pour non-Camera)
                            if (!isCamera) {
                                IconButton(
                                    onClick = {
                                        val dcimFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                                        val cameraFolder = File(dcimFolder, "Camera")
                                        val cameraPath = cameraFolder.absolutePath
                                        sourceFolder = cameraPath
                                        destinationFolder = folder
                                        showConfirmationDialog = true
                                    },
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Déplacer depuis DCIM/Camera",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        
                        // Séparateur optionnel
                        if (folder != availableFolders.last()) {
                            Divider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
                
                // Bouton pour créer un nouveau dossier
                Button(
                    onClick = onCreateFolder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Créer",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Nouveau dossier")
                }
            }
        }
    }
    
    // Boîte de dialogue de confirmation
    if (showConfirmationDialog) {
        val sourceName = getFormattedFolderName(sourceFolder).first
        val destName = getFormattedFolderName(destinationFolder).first
        
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text("Déplacer les photos") },  // Changer "Copier" en "Déplacer"
            text = { 
                Text("Voulez-vous déplacer les photos de $sourceName vers $destName? " +
                     "Les fichiers existants ne seront pas déplacés.")
            },
            confirmButton = {
                Button(onClick = {
                    onMovePhotos(sourceFolder, destinationFolder)
                    showConfirmationDialog = false
                }) {
                    Text("Déplacer")  // Changer "Copier" en "Déplacer"
                }
            },
            dismissButton = {
                Button(onClick = { showConfirmationDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

// Fonction getFormattedFolderName modifiée
private fun getFormattedFolderName(path: String): Pair<String, Boolean> {
    val segments = path.split("/")
    val lastSegment = segments.lastOrNull() ?: ""
    
    return when {
        // Cas Camera
        path.endsWith("/DCIM/Camera") -> {
            Pair("DCIM/Camera", false)
        }
        // Cas sous-dossier de FPR
        path.contains("/FPR/") -> {
            Pair(lastSegment, true)
        }
        // Autres cas
        else -> {
            Pair(lastSegment, false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    
    // État pour vérifier si le dossier existe déjà
    var folderAlreadyExists by remember { mutableStateOf(false) }
    
    // Fonction pour vérifier si le dossier existe déjà
    fun checkIfFolderExists(name: String): Boolean {
        val dcimFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val appFolder = File(dcimFolder, "FPR")
        val newFolder = File(appFolder, name.trim())
        return newFolder.exists()
    }
    
    // État pour indiquer si le nom est valide
    val hasInvalidChars = folderName.trim().isNotEmpty() && (folderName.contains("/") || folderName.contains("\\"))
    val isValidName = folderName.trim().isNotEmpty() && !hasInvalidChars && !folderAlreadyExists
    
    // Vérifier si le dossier existe à chaque changement du nom
    LaunchedEffect(folderName) {
        if (folderName.trim().isNotEmpty() && !hasInvalidChars) {
            folderAlreadyExists = checkIfFolderExists(folderName)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nouveau dossier") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .imePadding() // Permet à la BottomBar de rester visible au-dessus du clavier
            ) {
                // Champ de saisie pour le nom du dossier
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        singleLine = true,
                        label = { Text("Nom du dossier") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        isError = hasInvalidChars || folderAlreadyExists,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (isValidName) {
                                    keyboardController?.hide()
                                    onConfirm(folderName.trim())
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                    )
                }
                
                // Message d'erreur si le nom est invalide
                if (folderName.trim().isNotEmpty() && hasInvalidChars) {
                    Text(
                        text = "Le nom du dossier ne peut pas contenir / ou \\",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )
                }
                // Message d'erreur si le dossier existe déjà
                else if (folderAlreadyExists) {
                    Text(
                        text = "Ce dossier existe déjà",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )
                }
                
                // Boutons d'action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bouton annuler
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Annuler")
                    }
                    
                    // Espacement entre les boutons
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Bouton créer
                    Button(
                        onClick = { 
                            if (isValidName) {
                                keyboardController?.hide()
                                onConfirm(folderName.trim())
                            }
                        },
                        enabled = isValidName,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Créer")
                    }
                }
            }
        }
    ) { padding -> 
        // Contenu vide car nous utilisons uniquement le bottomBar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
    
    // Demander le focus au champ de texte dès l'affichage
    LaunchedEffect(Unit) {
        // Petit délai pour s'assurer que le TextField est prêt
        delay(100)
        focusRequester.requestFocus()
    }
    
    // Gérer le bouton retour pour quitter la création
    BackHandler {
        onDismiss()
    }
}