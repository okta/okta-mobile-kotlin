package com.okta.idx.android.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.okta.idx.android.TokenViewModel
import com.okta.idx.android.network.Network
import com.okta.idx.sdk.api.model.TokenType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Request
import timber.log.Timber
import java.io.IOException

internal class DashboardViewModel : ViewModel() {
    private val _logoutStateLiveData = MutableLiveData<LogoutState>(LogoutState.Idle)
    val logoutStateLiveData: LiveData<LogoutState> = _logoutStateLiveData

    private val _userInfoLiveData = MutableLiveData<Map<String, String>>(emptyMap())
    val userInfoLiveData: LiveData<Map<String, String>> = _userInfoLiveData

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getClaims()?.let { _userInfoLiveData.postValue(it) }
            } catch (e: IOException) {
                Timber.e(e, "User info request failed.")
            }
        }
    }

    fun logout() {
        _logoutStateLiveData.value = LogoutState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (TokenViewModel.tokenResponse.refreshToken != null) {
                    // Revoking the refresh token revokes both!
                    Network.authenticationWrapper().revokeToken(
                        TokenType.REFRESH_TOKEN,
                        TokenViewModel.tokenResponse.refreshToken
                    )
                } else {
                    Network.authenticationWrapper().revokeToken(
                        TokenType.ACCESS_TOKEN,
                        TokenViewModel.tokenResponse.accessToken
                    )
                }

                TokenViewModel._tokenResponse = null

                _logoutStateLiveData.postValue(LogoutState.Success)
            } catch (e: Exception) {
                _logoutStateLiveData.postValue(LogoutState.Failed)
            }
        }
    }

    private fun getClaims(): Map<String, String>? {
        val accessToken = TokenViewModel.tokenResponse.accessToken
        val request = Request.Builder()
            .addHeader("authorization", "Bearer $accessToken")
            .url("${Network.baseUrl}/v1/userinfo")
            .build()
        val response = Network.okHttpClient().newCall(request).execute()
        if (response.isSuccessful) {
            val parser = ObjectMapper().createParser(response.body?.byteStream())
            val json = parser.readValueAsTree<JsonNode>()
            val map = mutableMapOf<String, String>()
            for (entry in json.fields()) {
                map[entry.key] = entry.value.asText()
            }
            return map
        }

        return null
    }

    fun acknowledgeLogoutSuccess() {
        _logoutStateLiveData.value = LogoutState.Idle
    }

    sealed class LogoutState {
        object Idle : LogoutState()
        object Loading : LogoutState()
        object Success : LogoutState()
        object Failed : LogoutState()
    }
}
