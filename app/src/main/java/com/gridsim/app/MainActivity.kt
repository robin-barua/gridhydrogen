package com.gridsim.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gridsim.app.ui.AppViewModel
import com.gridsim.app.ui.screens.CustomScreen
import com.gridsim.app.ui.screens.DemoScreen
import com.gridsim.app.ui.screens.HomeScreen
import com.gridsim.app.ui.screens.ResultsScreen
import com.gridsim.app.ui.theme.GridHydrogenSimTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GridHydrogenSimTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GridSimApp()
                }
            }
        }
    }
}

@Composable
fun GridSimApp() {
    val navController: NavHostController = rememberNavController()
    val viewModel: AppViewModel = viewModel()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("demo") { DemoScreen(navController, viewModel) }
        composable("custom") { CustomScreen(navController, viewModel) }
        composable("results") { ResultsScreen(navController, viewModel) }
    }
}
