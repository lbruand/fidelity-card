package io.fidelitycard.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.fidelitycard.app.collector.CardDetailScreen
import io.fidelitycard.app.collector.CardListScreen
import io.fidelitycard.app.collector.JoinBusinessScreen
import io.fidelitycard.app.collector.RedeemFlowScreen
import io.fidelitycard.app.collector.StampFlowScreen
import io.fidelitycard.app.issuer.BusinessDetailScreen
import io.fidelitycard.app.issuer.BusinessListScreen
import io.fidelitycard.app.issuer.CreateBusinessScreen

private object Routes {
    const val HOME = "home"
    const val BUSINESS_LIST = "issuer/businesses"
    const val CREATE_BUSINESS = "issuer/businesses/create"
    const val BUSINESS_DETAIL = "issuer/businesses/{programId}"
    const val CARD_LIST = "collector/cards"
    const val JOIN_BUSINESS = "collector/cards/join"
    const val CARD_DETAIL = "collector/cards/{cardId}"
    const val STAMP_FLOW = "collector/cards/{cardId}/stamp"
    const val REDEEM_FLOW = "collector/cards/{cardId}/redeem"

    fun businessDetail(programId: String) = "issuer/businesses/$programId"
    fun cardDetail(cardId: String) = "collector/cards/$cardId"
    fun stampFlow(cardId: String) = "collector/cards/$cardId/stamp"
    fun redeemFlow(cardId: String) = "collector/cards/$cardId/redeem"
}

@Composable
fun FidelityCardApp() {
    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenBusinesses = { navController.navigate(Routes.BUSINESS_LIST) },
                onOpenCards = { navController.navigate(Routes.CARD_LIST) },
            )
        }

        composable(Routes.BUSINESS_LIST) {
            BusinessListScreen(
                onOpenBusiness = { programId -> navController.navigate(Routes.businessDetail(programId)) },
                onCreateBusiness = { navController.navigate(Routes.CREATE_BUSINESS) },
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
                onJoinBusiness = { navController.navigate(Routes.JOIN_BUSINESS) },
            )
        }
        composable(Routes.JOIN_BUSINESS) {
            JoinBusinessScreen(
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
                onGetStamp = { navController.navigate(Routes.stampFlow(cardId)) },
                onRedeem = { navController.navigate(Routes.redeemFlow(cardId)) },
            )
        }
        composable(
            route = Routes.STAMP_FLOW,
            arguments = listOf(navArgument("cardId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val cardId = backStackEntry.arguments?.getString("cardId").orEmpty()
            StampFlowScreen(cardId = cardId, onBack = { navController.popBackStack() }, onDone = { navController.popBackStack() })
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
