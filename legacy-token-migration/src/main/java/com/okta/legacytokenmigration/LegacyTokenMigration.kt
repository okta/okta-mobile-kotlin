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
package com.okta.legacytokenmigration

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import com.okta.authfoundation.credential.Credential
import com.okta.authfoundation.credential.Token
import com.okta.oidc.clients.sessions.SessionClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A helper class to migrate tokens from the [Legacy OIDC SDK](https://github.com/okta/okta-oidc-android) to Auth Foundations
 * [Credential].
 *
 * See [migrate.md](https://github.com/okta/okta-mobile-kotlin/blob/master/migrate.md) for more information.
 */
object LegacyTokenMigration {
    private const val SHARED_PREFERENCE_FILE = "com.okta.legacytokenmigration.status"
    private const val SHARED_PREFERENCE_HAS_MIGRATED_KEY = "com.okta.legacytokenmigration.has_migrated"

    /**
     * Attempts to migrate a token from a [SessionClient] to a [Credential].
     *
     * @param context used for storing the status of previous migrations in Shared Preferences.
     * @param sessionClient a configured session client with the stored tokens from a previous authentication where the token will be
     *  migrated from.
     * @param credential the credential where the token should be migrated to.
     *
     * @return a [Result] with the outcome of the migration.
     */
    suspend fun migrate(context: Context, sessionClient: SessionClient, credential: Credential): Result {
        return withContext(Dispatchers.IO) {
            val sharedPreferences = context.sharedPreferences()
            if (sharedPreferences.hasMarkedTokensAsMigrated()) {
                return@withContext Result.PreviouslyMigrated
            }
            try {
                val legacyToken = sessionClient.tokens ?: return@withContext Result.MissingLegacyToken
                val token = Token(
                    tokenType = "Bearer",
                    expiresIn = legacyToken.expiresIn,
                    accessToken = legacyToken.accessToken ?: "",
                    scope = legacyToken.scope?.joinToString(" ") ?: "",
                    refreshToken = legacyToken.refreshToken,
                    idToken = legacyToken.idToken,
                    deviceSecret = null,
                    issuedTokenType = null,
                )
                credential.storeToken(token)
                sharedPreferences.markTokensAsMigrated()
                sessionClient.clear()
                Result.SuccessfullyMigrated
            } catch (t: Exception) {
                Result.Error(t)
            }
        }
    }

    @VisibleForTesting internal fun Context.sharedPreferences(): SharedPreferences {
        return getSharedPreferences(SHARED_PREFERENCE_FILE, Context.MODE_PRIVATE)
    }

    @VisibleForTesting internal fun SharedPreferences.markTokensAsMigrated(): Unit = with(edit()) {
        putBoolean(SHARED_PREFERENCE_HAS_MIGRATED_KEY, true)
        apply()
    }

    @VisibleForTesting internal fun SharedPreferences.hasMarkedTokensAsMigrated(): Boolean {
        return getBoolean(SHARED_PREFERENCE_HAS_MIGRATED_KEY, false)
    }

    /**
     * Describes the result from [LegacyTokenMigration.migrate].
     */
    sealed class Result {
        /**
         * The token was previously migrated. No changes were made as a result of the [LegacyTokenMigration.migrate] call.
         */
        object PreviouslyMigrated : Result()

        /**
         * An error occurred when migrating the token.
         * See the associated [exception] for details.
         */
        class Error internal constructor(
            /**
             *
             */
            val exception: Exception
        ) : Result()

        /**
         * The token migrated successfully.
         * The [Credential] supplied to the [LegacyTokenMigration.migrate] call now stores the token.
         * The [SessionClient] supplied to the [LegacyTokenMigration.migrate] call has been cleared, and should no longer be used.
         */
        object SuccessfullyMigrated : Result()

        /**
         * The [SessionClient] supplied to the [LegacyTokenMigration.migrate] call did not contain a token, migration is not possible.
         */
        object MissingLegacyToken : Result()
    }
}
