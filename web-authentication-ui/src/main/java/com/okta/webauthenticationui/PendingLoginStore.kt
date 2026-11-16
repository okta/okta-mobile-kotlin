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
package com.okta.webauthenticationui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.okta.oauth2.kmp.AuthorizationCodeFlowContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull

private val Context.pendingLoginDataStore: DataStore<Preferences> by preferencesDataStore(name = "com.okta.webauthenticationui.pending_login")

/**
 * Persists the in-progress [AuthorizationCodeFlowContext] in app-private storage so a login can be
 * completed after the host process is killed while the user is in the browser.
 *
 * Only a single pending login is kept; starting a new login overwrites it. Entries older than
 * [maxAgeMillis] are discarded since the authorization request is no longer worth completing.
 * When an [encryptor] is provided the stored value is encrypted; otherwise it is stored as-is.
 */
internal class PendingLoginStore(
    context: Context,
    private val encryptor: PendingLoginEncryptor? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
) {
    private val dataStore by lazy { context.applicationContext.pendingLoginDataStore }

    /** A stored login and how long it remains valid. */
    class Pending(
        val flowContext: AuthorizationCodeFlowContext,
        val remainingMillis: Long,
    )

    suspend fun save(
        flowContext: AuthorizationCodeFlowContext,
        clientId: String,
    ): Result<Unit> =
        runCatchingCancellable {
            val json = flowContext.toJson()
            val stored = encryptor?.encrypt(json)?.getOrThrow() ?: json
            dataStore.edit {
                it[KEY_CONTEXT] = stored
                it[KEY_SAVED_AT] = clock()
                it[KEY_CLIENT_ID] = clientId
            }
            Unit
        }

    /**
     * Returns the pending login for [clientId], or `null` if there is none, it expired, or it is unreadable.
     * An entry started by a different client is left in place for that client. An entry with no recorded
     * client (written before client binding existed) cannot be attributed safely and is discarded.
     */
    suspend fun load(clientId: String): Pending? =
        runCatchingCancellable {
            val preferences = dataStore.data.firstOrNull() ?: return@runCatchingCancellable null
            val stored = preferences[KEY_CONTEXT] ?: return@runCatchingCancellable null
            val storedClientId = preferences[KEY_CLIENT_ID]
            if (storedClientId == null) {
                clear()
                return@runCatchingCancellable null
            }
            if (storedClientId != clientId) return@runCatchingCancellable null
            val age = clock() - (preferences[KEY_SAVED_AT] ?: 0L)
            // A negative age means the clock moved backwards; treat that as expired rather than immortal.
            if (age < 0 || age > maxAgeMillis) {
                clear()
                return@runCatchingCancellable null
            }
            val json = encryptor?.decrypt(stored)?.getOrThrow() ?: stored
            Pending(AuthorizationCodeFlowContext.fromJson(json).getOrThrow(), maxAgeMillis - age)
        }.getOrNull()

    /** Best effort: a storage failure must not fail the login that is being completed. */
    suspend fun clear() {
        runCatchingCancellable { dataStore.edit { it.clear() } }
    }

    // runCatching would swallow CancellationException and break structured concurrency.
    private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private companion object {
        val KEY_CONTEXT = stringPreferencesKey("context")
        val KEY_SAVED_AT = longPreferencesKey("saved_at")
        val KEY_CLIENT_ID = stringPreferencesKey("client_id")
        const val DEFAULT_MAX_AGE_MILLIS = 10 * 60 * 1000L
    }
}
