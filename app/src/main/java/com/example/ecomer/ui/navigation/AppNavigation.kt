package com.example.ecomer.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ecomer.EcomerApplication
import com.example.ecomer.data.model.SessionState
import com.example.ecomer.ui.auth.AuthViewModel
import com.example.ecomer.ui.auth.LoginScreen
import com.example.ecomer.ui.auth.RegisterScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.ecomer.ui.products.ProductScreen
import com.example.ecomer.ui.products.ProductViewModel

/** Điều hướng theo trạng thái phiên: Đang kiểm tra -> Đăng nhập -> Màn hình sản phẩm. */
@Composable
fun AppNavigation() {
    val app = LocalContext.current.applicationContext as EcomerApplication
    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory { initializer { AuthViewModel(app.authRepository) } }
    )
    val session by authViewModel.session.collectAsStateWithLifecycle()
    var showRegister by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(session) {
        if (session is SessionState.SignedIn) showRegister = false
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Crossfade(
            targetState = session::class,
            label = "session"
        ) { target ->
            when (target) {
                SessionState.Loading::class -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                SessionState.SignedIn::class -> {
                    (session as? SessionState.SignedIn)?.let { signedIn ->
                        val productViewModel: ProductViewModel = viewModel(
                            factory = viewModelFactory {
                                initializer { ProductViewModel(app.productRepository, app.authRepository) }
                            }
                        )
                        ProductScreen(
                            user = signedIn.user,
                            viewModel = productViewModel,
                            onSignOut = authViewModel::signOut
                        )
                    }
                }

                else -> if (showRegister) {
                    RegisterScreen(
                        viewModel = authViewModel,
                        onNavigateToLogin = {
                            authViewModel.resetRegister()
                            showRegister = false
                        }
                    )
                } else {
                    LoginScreen(authViewModel, onNavigateToRegister = { showRegister = true })
                }
            }
        }
    }
}
