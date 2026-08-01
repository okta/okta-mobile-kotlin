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
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.credential.Credential
import com.okta.authfoundation.credential.CredentialDataSource
import com.okta.authfoundation.credential.Token
import com.okta.oidc.clients.sessions.SessionClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * A helper class to migrate tokens from the [Legacy OIDC SDK](https://github.com/okta/okta-oidc-android) to Auth Foundations
 * [Credential].
 *
 * [migrate] is idempotent — it records completion in private SharedPreferences and returns [Result.PreviouslyMigrated] on subsequent
 * calls, so it is safe to invoke on every app start.
 *
 * See [migrate.md](https://github.com/okta/okta-mobile-kotlin/blob/master/migrate.md) for more information.
 */
object LegacyTokenMigration {
    private const val SHARED_PREFERENCE_FILE = "com.okta.legacytokenmigration.status"
    private const val SHARED_PREFERENCE_HAS_MIGRATED_KEY = "com.okta.legacytokenmigration.has_migrated"
    private const val SHARED_PREFERENCE_MIGRATED_TOKEN_ID_KEY = "com.okta.legacytokenmigration.token.id"

    /**
     * Migrates the token held by a legacy [SessionClient] into a new [Credential], persisting it to the [CredentialDataSource], and
     * clears the legacy [SessionClient].
     *
     * On success/previously-migrated, look the stored credential up with `Credential.with(result.tokenId)` and optionally make it the
     * default via `Credential.default = ...` / `Credential.setDefaultAsync(...)`.
     *
     * @param context used for storing the status of previous migrations in Shared Preferences.
     * @param sessionClient a configured session client with the stored tokens from a previous authentication where the token will be
     *  migrated from.
     *
     * @return a [Result]: [Result.SuccessfullyMigrated] (new credential created), [Result.PreviouslyMigrated] (migration already ran
     *  previously), [Result.MissingLegacyToken] (the [SessionClient] had no token), or [Result.Error] (an exception occurred —
     *  inspect `exception`). This function never throws for these cases; it is safe to call from any dispatcher (it switches to
     *  `Dispatchers.IO` internally).
     */
    suspend fun migrate(
        context: Context,
        sessionClient: SessionClient,
    ): Result {
        return withContext(Dispatchers.IO) {
            val sharedPreferences = context.sharedPreferences()
            if (sharedPreferences.hasMarkedTokensAsMigrated()) {
                val tokenId = sharedPreferences.getString(SHARED_PREFERENCE_MIGRATED_TOKEN_ID_KEY, "")!!
                return@withContext Result.PreviouslyMigrated(tokenId)
            }
            try {
                val legacyToken = sessionClient.tokens ?: return@withContext Result.MissingLegacyToken
                val token =
                    Token(
                        id = UUID.randomUUID().toString(),
                        tokenType = "Bearer",
                        expiresIn = legacyToken.expiresIn,
                        accessToken = legacyToken.accessToken ?: "",
                        scope = legacyToken.scope?.joinToString(" ") ?: "",
                        refreshToken = legacyToken.refreshToken,
                        idToken = legacyToken.idToken,
                        deviceSecret = null,
                        issuedTokenType = null,
                        oidcConfiguration = OidcConfiguration.default
                    )
                val credential = Credential.store(token)
                sharedPreferences.markTokensAsMigrated(credential.id)
                sessionClient.clear()
                Result.SuccessfullyMigrated(credential.id)
            } catch (t: Exception) {
                Result.Error(t)
            }
        }
    }

    @VisibleForTesting internal fun Context.sharedPreferences(): SharedPreferences = getSharedPreferences(SHARED_PREFERENCE_FILE, Context.MODE_PRIVATE)

    @VisibleForTesting internal fun SharedPreferences.markTokensAsMigrated(tokenId: String): Unit =
        with(edit()) {
            putBoolean(SHARED_PREFERENCE_HAS_MIGRATED_KEY, true)
            putString(SHARED_PREFERENCE_MIGRATED_TOKEN_ID_KEY, tokenId)
            apply()
        }

    @VisibleForTesting internal fun SharedPreferences.hasMarkedTokensAsMigrated(): Boolean = getBoolean(SHARED_PREFERENCE_HAS_MIGRATED_KEY, false)

    /**
     * The exhaustive set of outcomes from [LegacyTokenMigration.migrate]. Branch on it with an exhaustive `when` to handle success,
     * prior migration, a missing legacy token, and errors.
     */
    sealed class Result {
        /**
         * The token was previously migrated. No changes were made as a result of the [LegacyTokenMigration.migrate] call. The migrated token was
         * stored in [CredentialDataSource] with the returned [tokenId].
         *
         * @property tokenId The id of the stored [Credential]; retrieve it with `Credential.with(tokenId)`.
         */
        data class PreviouslyMigrated(
            val tokenId: String,
        ) : Result()

        /**
         * An error occurred when migrating the token.
         * See the associated [exception] for details.
         */
        class Error internal constructor(
            /**
             * The exception that caused migration to fail — e.g. an error thrown while reading tokens from the [SessionClient], or a
             * failure persisting the new [Credential]. Migration state is left untouched, so the call can be safely retried.
             */
            val exception: Exception,
        ) : Result()

        /**
         * The token migrated successfully. A new [Credential] was created and stored in the [CredentialDataSource]; reference it with
         * the returned [tokenId] via `Credential.with(tokenId)`. The [SessionClient] passed to [migrate] has been cleared and must not
         * be used again.
         *
         * @property tokenId The id of the stored [Credential]; retrieve it with `Credential.with(tokenId)`.
         */
        data class SuccessfullyMigrated(
            val tokenId: String,
        ) : Result()

        /**
         * The [SessionClient] supplied to the [LegacyTokenMigration.migrate] call did not contain a token, migration is not possible.
         */
        object MissingLegacyToken : Result()
    }
}
