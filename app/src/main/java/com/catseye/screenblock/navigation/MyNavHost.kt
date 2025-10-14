package com.catseye.screenblock.navigation

import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.catseye.screenblock.ui.OverlayScreen
import com.catseye.screenblock.ui.RootScreen
import com.catseye.screenblock.viewmodel.RootViewModel

const val ROOT_URI = "myapp://root_id"

@Composable
fun MyNavHost(
    paddingValues: PaddingValues,
    navCon: NavHostController
) {
    NavHost(
        modifier = Modifier.padding(paddingValues),
        navController = navCon,
        startDestination = Destinations.ROOT.name
    ) {
        composable(
            route = Destinations.ROOT.name,
            deepLinks = listOf(navDeepLink { uriPattern = ROOT_URI })
        ) { backStack ->
            val parent = remember(backStack) {
                navCon.getBackStackEntry(navCon.graph.id)
            }
            val vm = hiltViewModel<RootViewModel>(parent)
            RootScreen(vm)
        }

        composable(Destinations.OVERLAY.name) {
            OverlayScreen()
        }
    }
}