package com.sjtu.canvas.helper.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sjtu.canvas.helper.R
import com.sjtu.canvas.helper.ui.navigation.Screen
import com.sjtu.canvas.helper.ui.screens.*

data class NavigationItem(
    val route: String,
    val icon: ImageVector,
    val label: String
)

@Composable
fun CanvasHelperApp() {
    val navController = rememberNavController()
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    
    // Adaptive layout based on screen size
    val useNavigationRail = screenWidthDp >= 600 // Tablet
    
    val navigationItems = listOf(
        NavigationItem(
            route = Screen.Courses.route,
            icon = Icons.Default.Book,
            label = stringResource(R.string.nav_courses)
        ),
        NavigationItem(
            route = Screen.Settings.route,
            icon = Icons.Default.Settings,
            label = stringResource(R.string.nav_settings)
        )
    )
    
    Scaffold(
        bottomBar = {
            if (!useNavigationRail) {
                BottomNavigation {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    
                    navigationItems.forEach { item ->
                        BottomNavigationItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(Screen.Courses.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (useNavigationRail) {
                // Material 2 doesn't have NavigationRail, we'll use a vertical navigation instead
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(72.dp),
                    elevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.height(16.dp))
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route
                        
                        navigationItems.forEach { item ->
                            val isSelected = currentRoute == item.route
                            IconButton(
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(Screen.Courses.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = isSelected,
                                        onClick = {
                                            navController.navigate(item.route) {
                                                popUpTo(Screen.Courses.route) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        role = Role.Tab
                                    )
                            ) {
                                Column(
                                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        item.icon, 
                                        contentDescription = item.label,
                                        tint = if (isSelected) 
                                            MaterialTheme.colors.primary 
                                        else 
                                            MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.caption,
                                        color = if (isSelected) 
                                            MaterialTheme.colors.primary 
                                        else 
                                            MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            AppNavHost(
                navController = navController,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
        modifier = modifier
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Courses.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Courses.route) {
            CoursesScreen(
                onAssignmentsClick = { courseId ->
                    navController.navigate(Screen.Assignments.createRoute(courseId))
                },
                onVideosClick = { courseId ->
                    navController.navigate(Screen.Videos.createRoute(courseId))
                },
                onFilesClick = { courseId ->
                    navController.navigate(Screen.Files.createRoute(courseId))
                }
            )
        }
        
        composable(Screen.Assignments.route) { backStackEntry ->
            val courseId = backStackEntry.arguments?.getString("courseId")?.toLongOrNull() ?: 0L
            AssignmentsScreen(
                courseId = courseId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Videos.route) { backStackEntry ->
            val courseId = backStackEntry.arguments?.getString("courseId")?.toLongOrNull() ?: 0L
            VideosScreen(
                courseId = courseId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Files.route) { backStackEntry ->
            val courseId = backStackEntry.arguments?.getString("courseId")?.toLongOrNull() ?: 0L
            CourseFilesScreen(
                courseId = courseId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
