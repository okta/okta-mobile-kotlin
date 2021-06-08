package com.okta.idx.android

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootPageTestEndToEnd {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)
}