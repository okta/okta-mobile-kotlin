/*
 * Copyright 2022-Present Okta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sample.okta.android.legacy.legacydashboard

import android.app.Activity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.oidc.AuthorizationStatus
import com.okta.oidc.RequestCallback
import com.okta.oidc.ResultCallback
import com.okta.oidc.Tokens
import com.okta.oidc.net.response.UserInfo
import com.okta.oidc.util.AuthorizationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import sample.okta.android.legacy.SampleWebAuthClientHelper

internal class LegacyDashboardViewModel : ViewModel() {
    private val _requestStateLiveData = MutableLiveData<RequestState>(RequestState.Result(""))
    val requestStateLiveData: LiveData<RequestState> = _requestStateLiveData

    private val _userInfoLiveData = MutableLiveData<Map<String, String>>(emptyMap())
    val userInfoLiveData: LiveData<Map<String, String>> = _userInfoLiveData

    private val _tokenLiveData = MutableLiveData<Tokens>()
    val tokenLiveData: LiveData<Tokens> = _tokenLiveData

    var lastButtonId: Int = 0
    private var lastRequestJob: Job? = null

    private lateinit var token: Tokens

    init {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                token = SampleWebAuthClientHelper.webAuthClient.sessionClient.tokens
                _tokenLiveData.postValue(token)
            }
            getUserInfo()
        }
    }

    private suspend fun getUserInfo() {
        withContext(Dispatchers.IO) {
            SampleWebAuthClientHelper.webAuthClient.sessionClient.getUserProfile(
                object : RequestCallback<UserInfo, AuthorizationException> {
                    override fun onSuccess(result: UserInfo) {
                        _userInfoLiveData.postValue(result.raw.asMap())
                    }

                    override fun onError(error: String?, exception: AuthorizationException?) {
                        _userInfoLiveData.postValue(emptyMap())
                    }
                }
            )
        }
    }

    fun registerCallback(activity: Activity) {
        SampleWebAuthClientHelper.webAuthClient.registerCallback(
            object : ResultCallback<AuthorizationStatus, AuthorizationException> {
                override fun onSuccess(result: AuthorizationStatus) {
                    if (result == AuthorizationStatus.SIGNED_OUT) {
                        _requestStateLiveData.postValue(RequestState.Result("Signed out."))
                    } else {
                        _requestStateLiveData.postValue(RequestState.Result("Failed to sign out."))
                    }
                }

                override fun onCancel() {
                    _requestStateLiveData.postValue(RequestState.Result("Sign out cancelled."))
                }

                override fun onError(msg: String?, exception: AuthorizationException?) {
                    _requestStateLiveData.postValue(RequestState.Result("Failed to sign out."))
                }
            },
            activity
        )
    }

    fun revoke(buttonId: Int, revokeRefreshToken: Boolean) {
        performRequest(buttonId) {
            val token = if (revokeRefreshToken) {
                token.refreshToken
            } else {
                token.accessToken
            }
            SampleWebAuthClientHelper.webAuthClient.sessionClient.revokeToken(
                token,
                object : RequestCallback<Boolean, AuthorizationException> {
                    override fun onSuccess(result: Boolean) {
                        _requestStateLiveData.postValue(RequestState.Result("Token revoked."))
                    }

                    override fun onError(error: String?, exception: AuthorizationException?) {
                        _requestStateLiveData.postValue(RequestState.Result("Failed to revoke token."))
                    }
                }
            )
        }
    }

    fun refresh(buttonId: Int) {
        performRequest(buttonId) {
            SampleWebAuthClientHelper.webAuthClient.sessionClient.refreshToken(
                object : RequestCallback<Tokens, AuthorizationException> {
                    override fun onSuccess(result: Tokens) {
                        token = result
                        _tokenLiveData.postValue(token)
                        _requestStateLiveData.postValue(RequestState.Result("Token refreshed."))
                    }

                    override fun onError(error: String?, exception: AuthorizationException?) {
                        _requestStateLiveData.postValue(RequestState.Result("Failed to refresh token."))
                    }
                }
            )
        }
    }

    fun logoutOfWeb(activity: Activity) {
        viewModelScope.launch {
            SampleWebAuthClientHelper.webAuthClient.signOutOfOkta(activity)
        }
    }

    fun deleteToken() {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                SampleWebAuthClientHelper.webAuthClient.sessionClient.clear()
                _requestStateLiveData.postValue(RequestState.Result("Token removed."))
            }
        }
    }

    private fun performRequest(buttonId: Int, performer: suspend () -> Unit) {
        if (lastRequestJob?.isActive == true) {
            // Re-enable the button, so it's not permanently disabled.
            _requestStateLiveData.value = RequestState.Result("")
        }
        lastRequestJob?.cancel()
        lastButtonId = buttonId

        _requestStateLiveData.value = RequestState.Loading

        lastRequestJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                performer()
            }
        }
    }

    sealed class RequestState {
        object Loading : RequestState()
        data class Result(val text: String) : RequestState()
    }
}

private fun JSONObject.asMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    for (key in keys()) {
        map[key] = get(key).toString()
    }
    return map
}
