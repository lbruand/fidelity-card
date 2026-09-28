package io.fidelitycard.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.fidelitycard.app.backup.BackupScreen
import io.fidelitycard.app.collector.CardDetailScreen
import io.fidelitycard.app.collector.CardListScreen
import io.fidelitycard.app.collector.RedeemFlowScreen
import io.fidelitycard.app.collector.ScanBusinessScreen
import io.fidelitycard.app.data.AppMode
import io.fidelitycard.app.issuer.BusinessDetailScreen
import io.fidelitycard.app.issuer.BusinessListScreen
import io.fidelitycard.app.issuer.CreateBusinessScreen

private object Routes {
    const val HOME = "home"
    const val BACKUP = "backup"
    const val BUSINESS_LIST = "issuer/businesses"
    const val CREATE_BUSINESS = "issuer/businesses/create"
    const val BUSINESS_DETAIL = "issuer/businesses/{programId}"
    const val CARD_LIST = "collector/cards"
    const val SCAN_BUSINESS = "collector/cards/scan"
    const val CARD_DETAIL = "collector/cards/{cardId}"
    const val GET_STAMP = "collector/cards/{cardId}/stamp"
    const val REDEEM_FLOW = "collector/cards/{cardId}/redeem"

    fun businessDetail(programId: String) = "issuer/businesses/$programId"
    fun cardDetail(cardId: String) = "collector/cards/$cardId"
    fun getStamp(cardId: String) = "collector/cards/$cardId/stamp"
    fun redeemFlow(cardId: String) = "collector/cards/$cardId/redeem"
}

@Composable
fun FidelityCardApp() {
    val navController: NavHostController = rememberNavController()
    val context = LocalContext.current
    val modePreference = remember {
        (context.applicationContext as FidelityApplication).modePreference
    }
    // Skips the "who are you" fork on every launch once a mode has been
    // picked once (TODO.md "Product / UX") - HOME stays reachable via each
    // list screen's "Switch mode" action, it's just no longer the default
    // landing screen. A device can still use both modes (SPEC/SPECS.md
    // §4); this only decides which one opens by default.
    val startDestination = when (modePreference.mode) {
        AppMode.ISSUER -> Routes.BUSINESS_LIST
        AppMode.COLLECTOR -> Routes.CARD_LIST
        null -> Routes.HOME
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenBusinesses = {
                    modePreference.mode = AppMode.ISSUER
                    navController.navigate(Routes.BUSINESS_LIST) { popUpTo(Routes.HOME) { inclusive = true } }
                },
                onOpenCards = {
                    modePreference.mode = AppMode.COLLECTOR
                    navController.navigate(Routes.CARD_LIST) { popUpTo(Routes.HOME) { inclusive = true } }
                },
                onOpenBackup = { navController.navigate(Routes.BACKUP) },
            )
        }
        composable(Routes.BACKUP) {
            BackupScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.BUSINESS_LIST) {
            BusinessListScreen(
                onOpenBusiness = { programId -> navController.navigate(Routes.businessDetail(programId)) },
                onCreateBusiness = { navController.navigate(Routes.CREATE_BUSINESS) },
                onSwitchMode = { navController.navigate(Routes.HOME) },
            )
        }
        composable(Routes.CREATE_BUSINESS) {
            CreateBusinessScreen(
                onBack = { navController.popBackStack() },
                onCreated = { programId ->
                    navController.navigate(Routes.businessDetail(programId)) {
                        popUpTo(Routes.BUSINESS_LIST)
                    }
                },
            )
        }
        composable(
            route = Routes.BUSINESS_DETAIL,
            arguments = listOf(navArgument("programId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val programId = backStackEntry.arguments?.getString("programId").orEmpty()
            BusinessDetailScreen(programId = programId, onBack = { navController.popBackStack() })
        }

        composable(Routes.CARD_LIST) {
            CardListScreen(
                onOpenCard = { cardId -> navController.navigate(Routes.cardDetail(cardId)) },
                onJoinBusiness = { navController.navigate(Routes.SCAN_BUSINESS) },
                onSwitchMode = { navController.navigate(Routes.HOME) },
            )
        }
        composable(Routes.SCAN_BUSINESS) {
            ScanBusinessScreen(
                cardId = null,
                onBack = { navController.popBackStack() },
                onDone = { cardId ->
                    navController.navigate(Routes.cardDetail(cardId)) {
                        popUpTo(Routes.CARD_LIST)
                    }
                },
            )
        }
        composable(
            route = Routes.CARD_DETAIL,
            arguments = listOf(navArgument("cardId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val cardId = backStackEntry.arguments?.getString("cardId").orEmpty()
            CardDetailScreen(
                cardId = cardId,
                onBack = { navController.popBackStack() },
                onGetStamp = { navController.navigate(Routes.getStamp(cardId)) },
                onRedeem = { navController.navigate(Routes.redeemFlow(cardId)) },
                onLeft = { navController.popBackStack(Routes.CARD_LIST, inclusive = false) },
            )
        }
        composable(
            route = Routes.GET_STAMP,
            arguments = listOf(navArgument("cardId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val cardId = backStackEntry.arguments?.getString("cardId").orEmpty()
            ScanBusinessScreen(cardId = cardId, onBack = { navController.popBackStack() }, onDone = { navController.popBackStack() })
        }
        composable(
            route = Routes.REDEEM_FLOW,
            arguments = listOf(navArgument("cardId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val cardId = backStackEntry.arguments?.getString("cardId").orEmpty()
            RedeemFlowScreen(cardId = cardId, onBack = { navController.popBackStack() }, onDone = { navController.popBackStack() })
        }
    }
}
