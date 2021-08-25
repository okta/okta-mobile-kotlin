/*
 * Copyright 2021-Present Okta, Inc.
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
package com.okta.idx.android.cucumber.hooks

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.okta.idx.android.dynamic.MainActivity
import io.cucumber.java.After
import io.cucumber.java.Before
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ActivityHooks : Application.ActivityLifecycleCallbacks {
    private lateinit var countDownLatch: CountDownLatch

    @Before fun launchActivity() {
        // Not using activity scenario due to the way it launches multiple activities affects
        // working with our social redirects.

        val application = ApplicationProvider.getApplicationContext<Application>()
        application.registerActivityLifecycleCallbacks(this)
        countDownLatch = CountDownLatch(1)
        val intent = Intent(application, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        application.startActivity(intent)
        assertThat(countDownLatch.await(10, TimeUnit.SECONDS)).isTrue()
    }

    @After fun finishActivity() {
        countDownLatch = CountDownLatch(1)
        SharedState.activity?.let {
            it.finish()
            countDownLatch.await(10, TimeUnit.SECONDS)
            val application = ApplicationProvider.getApplicationContext<Application>()
            application.unregisterActivityLifecycleCallbacks(this)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Timber.d("Created: %s", activity)
        SharedState.activity = activity
        countDownLatch.countDown()
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        Timber.d("Destroying: %s", activity)
        countDownLatch.countDown()
    }
}
