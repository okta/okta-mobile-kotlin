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
package sample.okta.android.legacy.launch

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.legacytokenmigration.LegacyTokenMigration
import kotlinx.coroutines.launch
import sample.okta.android.legacy.SampleWebAuthClientHelper
import timber.log.Timber

internal class LaunchViewModel : ViewModel() {
    private val _migratedLiveData = MutableLiveData<Unit>()
    val migratedLiveData: LiveData<Unit> = _migratedLiveData

    fun migrateTokens(context: Context) {
        viewModelScope.launch {
            when (
                val result = LegacyTokenMigration.migrate(
                    context = context,
                    sessionClient = SampleWebAuthClientHelper.webAuthClient.sessionClient
                )
            ) {
                is LegacyTokenMigration.Result.Error -> {
                    Timber.d(result.exception, "Token migration failed.")
                }
                LegacyTokenMigration.Result.MissingLegacyToken -> {
                    Timber.d("No token to migrate.")
                }
                is LegacyTokenMigration.Result.PreviouslyMigrated -> {
                    Timber.d("Token previously migrated.")
                }
                is LegacyTokenMigration.Result.SuccessfullyMigrated -> {
                    _migratedLiveData.value = Unit // Update the UI.
                }
            }
        }
    }
}
