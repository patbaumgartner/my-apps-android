package com.patbaumgartner.android.myapps.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.patbaumgartner.android.myapps.R
import com.patbaumgartner.android.myapps.ui.screens.AppDetailsScreen
import com.patbaumgartner.android.myapps.ui.screens.MyAppsScreen
import com.patbaumgartner.android.myapps.ui.screens.SettingsScreen
import com.patbaumgartner.android.myapps.ui.screens.UpdatesScreen

private sealed class Route(val route: String, val labelRes: Int, val iconRes: Int) {
    data object MyApps : Route("apps", R.string.screen_my_apps, R.drawable.ic_apps)
    data object Updates : Route("updates", R.string.screen_updates, R.drawable.ic_update)
    data object Details : Route("details/{repo}", R.string.screen_details, R.drawable.ic_apps) {
        fun create(repo: String) = "details/$repo"
    }

    data object Settings : Route("settings", R.string.screen_settings, R.drawable.ic_settings)
}

private val bottomRoutes = listOf(Route.MyApps, Route.Updates, Route.Settings)

@Composable
fun MyAppsApp(viewModel: MyAppsViewModel) {
    val apps by viewModel.apps.collectAsState()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomRoutes.forEach { route ->
                    NavigationBarItem(
                        selected = backStackEntry?.destination?.hierarchy?.any { it.route == route.route } == true,
                        onClick = {
                            navController.navigate(route.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(route.iconRes), contentDescription = null) },
                        label = { Text(stringResource(route.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.MyApps.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Route.MyApps.route) {
                MyAppsScreen(
                    apps = viewModel.apps,
                    onRefresh = viewModel::refreshAll,
                    onOpenDetails = { repo -> navController.navigate(Route.Details.create(repo)) },
                )
            }
            composable(Route.Updates.route) {
                UpdatesScreen(
                    apps = viewModel.apps,
                    onRefresh = viewModel::refreshAll,
                    onInstall = viewModel::install,
                    onOpenDetails = { repo -> navController.navigate(Route.Details.create(repo)) },
                )
            }
            composable(
                Route.Details.route,
                arguments = listOf(navArgument("repo") { type = NavType.StringType }),
            ) { entry ->
                val repo = entry.arguments?.getString("repo").orEmpty()
                AppDetailsScreen(
                    state = apps[repo],
                    canInstallUnknown = viewModel.canInstallUnknown,
                    onInstall = { viewModel.install(repo) },
                    onChannel = { channel -> viewModel.setChannel(repo, channel) },
                    onRefresh = { viewModel.refreshOne(repo) },
                    onNavigateUp = { navController.navigateUp() },
                    onOpenInstallSettings = {
                        val intent = viewModel.installerSettingsIntent()
                        navController.context.startActivity(intent)
                    },
                )
            }
            composable(Route.Settings.route) {
                SettingsScreen(
                    themeMode = viewModel.themeMode,
                    backgroundChecks = viewModel.backgroundChecksEnabled,
                    wifiOnly = viewModel.backgroundWifiOnly,
                    notifications = viewModel.notificationsEnabled,
                    discovered = viewModel.discovered,
                    onTheme = viewModel::setTheme,
                    onBackgroundChecks = viewModel::setBackgroundChecks,
                    onWifiOnly = viewModel::setBackgroundWifiOnly,
                    onNotifications = viewModel::setNotificationsEnabled,
                    onCheckNow = viewModel::refreshAll,
                )
            }
        }
    }
}

@Composable
private fun stringResource(id: Int): String = androidx.compose.ui.res.stringResource(id)
