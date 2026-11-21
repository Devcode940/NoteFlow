package com.noteflow.ai.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.ctrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navArgument
import androidx.navigation.compose.rememberNavController
import com.noteflow.ai.domain.model.NoteFilterType
import com.noteflow.ai.feature.ai.AiAssistantScreen
import com.noteflow.ai.feature.auth.SignInScreen
import com.noteflow.ai.feature.commandpalette.CommandPaletteDialog
import com.noteflow.ai.feature.editor.NoteEditorScreen
import com.noteflow.ai.feature.export.ExportScreen
import com.noteflow.ai.feature.folders.FoldersScreen
import com.noteflow.ai.feature.notes.NoteListScreen
import com.noteflow.ai.feature.profile.ProfileScreen
import com.noteflow.ai.feature.search.SearchScreen
import com.noteflow.ai.feature.settings.SettingsScreen
import com.noteflow.ai.feature.tags.TagsScreen
import kotlinx.coroutines.launch

private data class DrawerDestination(
    val label: String,
    val route: String,
    val icon: @Composable () -> Unit
)

/**
 * Root navigation: collapsible drawer on phones, permanent drawer on tablets,
 * edge-to-edge Scaffold, and a global Ctrl+K command palette.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteFlowNavHost() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState()
    val scope = rememberCoroutineScope()
    var showPalette by remember { mutableStateOf(false) }

    val destinations = listOf(
        DrawerDestination("All Notes", Routes.notes(NoteFilterType.ALL)) { Icon(Icons.Filled.Home, null) },
        DrawerDestination("Recent Notes", Routes.notes(NoteFilterType.RECENT)) { Icon(Icons.Filled.Today, null) },
        DrawerDestination("Favorites", Routes.notes(NoteFilterType.FAVORITES)) { Icon(Icons.Filled.Star, null) },
        DrawerDestination("Pinned Notes", Routes.notes(NoteFilterType.PINNED)) { Icon(Icons.Filled.PushPin, null) },
        DrawerDestination("Archived Notes", Routes.notes(NoteFilterType.ARCHIVED)) { Icon(Icons.Filled.Archive, null) },
        DrawerDestination("Folders", Routes.FOLDERS) { Icon(Icons.Filled.Folder, null) },
        DrawerDestination("Tags", Routes.TAGS) { Icon(Icons.Filled.Tag, null) },
        DrawerDestination("Downloads & Exports", Routes.EXPORTS) { Icon(Icons.Filled.Download, null) },
        DrawerDestination("AI Assistant", Routes.AI) { Icon(Icons.Filled.AutoAwesome, null) },
        DrawerDestination("Settings", Routes.SETTINGS) { Icon(Icons.Filled.Settings, null) },
        DrawerDestination("User Profile", Routes.PROFILE) { Icon(Icons.Filled.Person, null) }
    )

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val currentTitle = when {
        currentRoute?.startsWith("editor/") == true -> "Note"
        currentRoute == Routes.FOLDERS -> "Folders"
        currentRoute == Routes.TAGS -> "Tags"
        currentRoute == Routes.SEARCH -> "Search"
        currentRoute == Routes.AI -> "AI Assistant"
        currentRoute == Routes.SETTINGS -> "Settings"
        currentRoute == Routes.PROFILE -> "Profile"
        currentRoute == Routes.SIGN_IN -> "Sign in"
        currentRoute == Routes.EXPORTS -> "Downloads & Exports"
        else -> "NoteFlow"
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.notes(NoteFilterType.ALL)) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        scope.launch { drawerState.close() }
    }

    BoxWithConstraints {
        val isWide = maxWidth >= 840.dp
        val drawerContent: @Composable () -> Unit = {
            destinations.forEach { destination ->
                NavigationDrawerItem(
                    label = { Text(destination.label) },
                    selected = currentRoute == destination.route,
                    onClick = { navigateTo(destination.route) },
                    icon = destination.icon,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }

        val content: @Composable () -> Unit = {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(currentTitle) },
                        navigationIcon = {
                            if (!isWide) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Filled.Home, contentDescription = "Open navigation")
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { navController.navigate(Routes.SEARCH) }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search notes")
                            }
                            IconButton(onClick = { navController.navigate(Routes.AI) }) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Assistant")
                            }
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .onPreviewKeyEvent { event ->
                            if (event.ctrlPressed && event.key == Key.K) {
                                showPalette = true
                                true
                            } else {
                                false
                            }
                        }
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.notes(NoteFilterType.ALL)
                    ) {
                        composable(
                            route = Routes.NOTES_PATTERN,
                            arguments = listOf(
                                navArgument("filter") { type = NavType.StringType },
                                navArgument("arg") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                }
                            )
                        ) { backStackEntry ->
                            val filterName = backStackEntry.arguments?.getString("filter") ?: "ALL"
                            val arg = backStackEntry.arguments?.getString("arg").orEmpty()
                            val filterType = runCatching { NoteFilterType.valueOf(filterName) }
                                .getOrDefault(NoteFilterType.ALL)
                            NoteListScreen(
                                filterType = filterType,
                                folderId = if (filterType == NoteFilterType.FOLDER) arg.ifBlank { null } else null,
                                tag = if (filterType == NoteFilterType.TAG) arg.ifBlank { null } else null,
                                onOpenNote = { id -> navController.navigate(Routes.editor(id)) },
                                onOpenFolder = { id ->
                                    navController.navigate(Routes.notes(NoteFilterType.FOLDER, folderId = id))
                                },
                                onOpenTag = { tag ->
                                    navController.navigate(Routes.notes(NoteFilterType.TAG, tag = tag))
                                }
                            )
                        }
                        composable(
                            route = Routes.EDITOR,
                            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val noteId = backStackEntry.arguments?.getString("noteId") ?: Routes.NEW_NOTE_ID
                            NoteEditorScreen(
                                noteId = noteId,
                                onNoteCreated = { newId ->
                                    navController.navigate(Routes.editor(newId)) {
                                        popUpTo(Routes.editor(noteId)) { inclusive = true }
                                    }
                                },
                                onNavigateBack = { navController.popBackStack() },
                                onOpenAi = { navController.navigate(Routes.AI) }
                            )
                        }
                        composable(Routes.FOLDERS) {
                            FoldersScreen(
                                onOpenFolder = { id ->
                                    navController.navigate(Routes.notes(NoteFilterType.FOLDER, folderId = id))
                                }
                            )
                        }
                        composable(Routes.TAGS) {
                            TagsScreen(
                                onOpenTag = { tag ->
                                    navController.navigate(Routes.notes(NoteFilterType.TAG, tag = tag))
                                }
                            )
                        }
                        composable(Routes.SEARCH) {
                            SearchScreen(
                                onOpenNote = { id -> navController.navigate(Routes.editor(id)) }
                            )
                        }
                        composable(Routes.AI) {
                            AiAssistantScreen(onOpenNote = { id -> navController.navigate(Routes.editor(id)) })
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(onOpenSignIn = { navController.navigate(Routes.SIGN_IN) })
                        }
                        composable(Routes.PROFILE) {
                            ProfileScreen(
                                onOpenSignIn = { navController.navigate(Routes.SIGN_IN) },
                                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                            )
                        }
                        composable(Routes.SIGN_IN) {
                            SignInScreen(onSignedIn = { navController.popBackStack() })
                        }
                        composable(Routes.EXPORTS) {
                            ExportScreen(onOpenNote = { id -> navController.navigate(Routes.editor(id)) })
                        }
                    }
                }
            }
        }

        if (isWide) {
            androidx.compose.foundation.layout.Row {
                PermanentDrawerSheet { drawerContent() }
                Box(Modifier.weight(1f)) { content() }
            }
        } else {
            ModalNavigationDrawer(drawerState = drawerState, drawerContent = { drawerContent() }) {
                content()
            }
        }
    }

    if (showPalette) {
        CommandPaletteDialog(
            onDismiss = { showPalette = false },
            onCreateNote = {
                showPalette = false
                navController.navigate(Routes.editor(Routes.NEW_NOTE_ID))
            },
            onSearch = {
                showPalette = false
                navController.navigate(Routes.SEARCH)
            },
            onCreateFolder = {
                showPalette = false
                navController.navigate(Routes.FOLDERS)
            },
            onOpenAi = {
                showPalette = false
                navController.navigate(Routes.AI)
            },
            onOpenSettings = {
                showPalette = false
                navController.navigate(Routes.SETTINGS)
            },
            onOpenFavorites = {
                showPalette = false
                navigateTo(Routes.notes(NoteFilterType.FAVORITES))
            },
            onOpenExports = {
                showPalette = false
                navController.navigate(Routes.EXPORTS)
            },
            onSwitchTheme = {
                showPalette = false
                /* theme toggle handled by settings screen */
            },
            onArchiveNote = {
                showPalette = false
                /* archive applies to the current note; handled in editor */
            }
        )
    }
}
