package com.okta.idx.android.cucumber.hooks

import io.cucumber.core.api.Scenario
import io.cucumber.java.Before
import org.junit.Assume
import timber.log.Timber

internal class SkipScenario {
    @Before("@skip_scenario") fun skip_scenario(scenario: Scenario) {
        Timber.d("SKIP SCENARIO: %s", scenario.name)
        Assume.assumeTrue(false)
    }
}
