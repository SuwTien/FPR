package fr.bdst.fastphotosrenamer.ui.components

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Bouton qui affiche un menu avec les options "À propos"
 */
@Composable
fun AboutMenuButton(
    // Paramètres pour coordonner l'état avec le parent
    showAboutMenu: Boolean = false,
    onShowAboutMenuChange: (Boolean) -> Unit = {},
    onShowFunctionalityDialog: () -> Unit = {},
    onShowLicenseDialog: () -> Unit = {},
    onShowCreditsDialog: () -> Unit = {}
) {
    val context = LocalContext.current
    
    // Bouton avec menu déroulant
    Box {
        IconButton(onClick = { onShowAboutMenuChange(true) }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Menu À propos"
            )
        }
        
        // Menu déroulant
        DropdownMenu(
            expanded = showAboutMenu,
            onDismissRequest = { onShowAboutMenuChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text("Fonctionnement") },
                onClick = {
                    onShowAboutMenuChange(false)
                    onShowFunctionalityDialog()
                }
            )
            
            DropdownMenuItem(
                text = { Text("Licence") },
                onClick = {
                    onShowAboutMenuChange(false)
                    onShowLicenseDialog()
                }
            )
            
            DropdownMenuItem(
                text = { Text("Crédits") },
                onClick = {
                    onShowAboutMenuChange(false)
                    onShowCreditsDialog()
                }
            )
            
            DropdownMenuItem(
                text = { 
                    val versionName = getAppVersion(context)
                    Text("Version $versionName")
                },
                onClick = { onShowAboutMenuChange(false) }
            )
        }
    }
}

/**
 * Dialogue affichant le fonctionnement de l'application
 */
@Composable
fun FunctionalityDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Fonctionnement",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "FastPhotosRenamer vous permet de rapidement prendre, visualiser et renommer vos photos.",
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Utilisation:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Column {
                        Text(
                            text = "Appareil photo : ",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Cliquez sur le bouton en bas pour prendre une photo",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Column {
                        Text(
                            text = "Clic simple : ",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Ouvre l'écran de renommage pour la photo",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Column {
                        Text(
                            text = "Appui long : ",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Ouvre la photo en mode plein écran",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Column {
                        Text(
                            text = "Menu dossier : ",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Cliquez sur le dossier en haut pour changer de répertoire",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Gestion des photos:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Column {
                        Text(
                            text = "Déplacer des photos : ",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Utilisez le menu dossier pour déplacer les photos de DCIM/Camera vers un dossier créé",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Column {
                        Text(
                            text = "Supprimer : ",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Dans l'écran de renommage, utilisez le bouton de suppression",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Column {
                        Text(
                            text = "Navigation plein écran : ",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Utilisez les flèches latérales pour parcourir les photos",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Fermer")
                }
            }
        }
    }
}

/**
 * Dialogue affichant les informations de licence
 */
@Composable
fun LicenseDialog(
    onDismiss: () -> Unit,
    onShowFullLicense: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Licence",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "FastPhotosRenamer est distribué sous licence GPL-3.0.",
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Cette licence libre vous permet d'utiliser, modifier et distribuer le logiciel, " +
                    "à condition de conserver la mention de copyright et de distribuer vos modifications " +
                    "sous la même licence."
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Vous pouvez consulter le code source sur GitHub.")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Nouveau bouton pour voir la licence complète
                Button(
                    onClick = onShowFullLicense,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Voir la licence complète")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Fermer")
                }
            }
        }
    }
}

/**
 * Dialogue affichant les crédits
 */
@Composable
fun CreditsDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Crédits",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Auteur :",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Cédric",
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Développeur :",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Claude 3.7 (Anthropic)",
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Technologies:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("• Kotlin & Jetpack Compose")
                Text("• Android SDK")
                Text("• Coil pour le chargement d'images") 
                Text("• Material Design 3")
                Text("• Visual Studio Code")
                Text("• GitHub Copilot")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Remerciements spéciaux à tous les contributeurs et testeurs.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Fermer")
                }
            }
        }
    }
}

/**
 * Dialogue affichant le texte complet de la licence GPL-3.0
 */
@Composable
fun FullLicenseDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val licenseText = remember { readLicenseFile(context) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false // Utiliser la largeur maximale disponible
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f) // 95% de la largeur de l'écran
                .padding(4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Licence GPL-3.0",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = licenseText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace // Police à largeur fixe pour une meilleure lisibilité
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Fermer")
                }
            }
        }
    }
}

/**
 * Récupère la version de l'application
 */
private fun getAppVersion(context: Context): String {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "N/A"
    } catch (e: PackageManager.NameNotFoundException) {
        "N/A"
    }
}

/**
 * Lit le contenu du fichier de licence
 */
private fun readLicenseFile(context: Context): String {
    return try {
        val inputStream = context.assets.open("gpl-3.0.txt")
        val reader = BufferedReader(InputStreamReader(inputStream))
        val stringBuilder = StringBuilder()
        var line: String?
        
        while (reader.readLine().also { line = it } != null) {
            stringBuilder.append(line).append('\n')
        }
        
        reader.close()
        inputStream.close()
        
        stringBuilder.toString()
    } catch (e: IOException) {
        "Impossible de charger le texte de la licence. Vous pouvez consulter la licence GPL-3.0 sur https://www.gnu.org/licenses/gpl-3.0.html"
    }
}