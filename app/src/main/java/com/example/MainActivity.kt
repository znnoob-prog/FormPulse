package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.FormDraftDatabase
import com.example.data.remote.ConnectivityObserver
import com.example.data.repository.FormRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.screens.FormAnalyticsScreen
import com.example.ui.screens.FormBuilderScreen
import com.example.ui.screens.FormResponderScreen
import com.example.ui.screens.FormsHubScreen
import com.example.ui.theme.FormPulseTheme
import com.example.ui.viewmodel.FormPulseViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object Routes {
    const val HUB = "forms_hub"
    const val RESPOND = "form_responder/{formId}"
    const val ANALYTICS = "form_analytics/{formId}"
    const val BUILDER_NEW = "form_builder_new"
    const val BUILDER_EDIT = "form_builder_edit/{formId}"

    fun respond(formId: String) = "form_responder/$formId"
    fun analytics(formId: String) = "form_analytics/$formId"
    fun edit(formId: String) = "form_builder_edit/$formId"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FormPulseTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        val uid = user.uid
        key(uid) {
            AuthenticatedFormPulseApp(
                currentUserId = uid,
                userDisplayName = user.displayName ?: "",
                userEmail = user.email
            )
        }
    }
}

@Composable
private fun AuthenticatedFormPulseApp(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: FormPulseViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val draftDao = FormDraftDatabase.getInstance(app).formDraftDao()
                val repository = FormRepository(db = db, draftDao = draftDao)
                val connectivityObserver = ConnectivityObserver(app, db)
                FormPulseViewModel(
                    repository = repository,
                    connectivityObserver = connectivityObserver,
                    currentUserId = currentUserId
                )
            }
        }
    )

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HUB
    ) {
        composable(Routes.HUB) {
            FormsHubScreen(
                viewModel = viewModel,
                userEmail = userEmail,
                onOpenResponder = { formId -> navController.navigate(Routes.respond(formId)) },
                onOpenAnalytics = { formId -> navController.navigate(Routes.analytics(formId)) },
                onOpenEditor = { formId ->
                    if (formId == null) navController.navigate(Routes.BUILDER_NEW)
                    else navController.navigate(Routes.edit(formId))
                },
                onSignOut = {
                    signOut(credentialManager = credentialManager, scope = scope)
                }
            )
        }

        composable(
            route = Routes.RESPOND,
            arguments = listOf(navArgument("formId") { type = NavType.StringType })
        ) { backStackEntry ->
            val formId = backStackEntry.arguments?.getString("formId").orEmpty()
            FormResponderScreen(
                formId = formId,
                defaultRespondentName = userDisplayName,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenAnalytics = {
                    navController.popBackStack()
                    navController.navigate(Routes.analytics(formId))
                }
            )
        }

        composable(
            route = Routes.ANALYTICS,
            arguments = listOf(navArgument("formId") { type = NavType.StringType })
        ) { backStackEntry ->
            val formId = backStackEntry.arguments?.getString("formId").orEmpty()
            FormAnalyticsScreen(
                formId = formId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenResponder = {
                    navController.navigate(Routes.respond(formId))
                }
            )
        }

        composable(Routes.BUILDER_NEW) {
            FormBuilderScreen(
                formId = null,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSaved = { createdId ->
                    navController.popBackStack()
                    navController.navigate(Routes.respond(createdId))
                }
            )
        }

        composable(
            route = Routes.BUILDER_EDIT,
            arguments = listOf(navArgument("formId") { type = NavType.StringType })
        ) { backStackEntry ->
            val formId = backStackEntry.arguments?.getString("formId").orEmpty()
            FormBuilderScreen(
                formId = formId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
    }
}
