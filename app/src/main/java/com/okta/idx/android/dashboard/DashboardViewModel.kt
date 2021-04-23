package com.okta.idx.android.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.idx.android.TokenViewModel
import com.okta.idx.android.network.Network
import com.okta.idx.sdk.api.model.TokenType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class DashboardViewModel : ViewModel() {
    private val _logoutStateLiveData = MutableLiveData<LogoutState>(LogoutState.Idle)
    val logoutStateLiveData: LiveData<LogoutState> = _logoutStateLiveData

    fun logout() {
        _logoutStateLiveData.value = LogoutState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (TokenViewModel.tokenResponse.refreshToken != null) {
                    // Revoking the refresh token revokes both!
                    Network.idxClient().revokeToken(
                        TokenType.REFRESH_TOKEN.toString(),
                        TokenViewModel.tokenResponse.refreshToken
                    )
                } else {
                    Network.idxClient().revokeToken(
                        TokenType.ACCESS_TOKEN.toString(),
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
