package com.example.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class ConnectivityObserver(
    context: Context,
    private val db: FirebaseFirestore
) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isSystemOnline = MutableStateFlow(checkCurrentConnectivity())
    val isSystemOnline: StateFlow<Boolean> = _isSystemOnline.asStateFlow()

    private val _isOfflineModeForced = MutableStateFlow(false)
    val isOfflineModeForced: StateFlow<Boolean> = _isOfflineModeForced.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isSystemOnline.value = checkCurrentConnectivity()
        }

        override fun onLost(network: Network) {
            _isSystemOnline.value = checkCurrentConnectivity()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            _isSystemOnline.value = checkCurrentConnectivity()
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            _isSystemOnline.value = checkCurrentConnectivity()
        }
    }

    fun isEffectivelyOnline(): Boolean =
        _isSystemOnline.value && !_isOfflineModeForced.value

    suspend fun setOfflineModeForced(forced: Boolean) {
        _isOfflineModeForced.value = forced
        try {
            if (forced) {
                db.disableNetwork().await()
            } else {
                db.enableNetwork().await()
            }
        } catch (_: Exception) {
            // Firestore toggles network state asynchronously even when offline
        }
    }

    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }

    private fun checkCurrentConnectivity(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
