package com.okta.idx.android.infrastructure.espresso

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.google.common.truth.Truth.assertThat
import com.okta.idx.android.R
import org.hamcrest.Matchers.allOf

fun selectAuthenticator(authenticatorTitle: String) {
    onView(
        allOf(
            withParent(withChild(withText(authenticatorTitle))),
            withId(R.id.select_button)
        )
    ).perform(click())
}

fun waitForElement(resourceId: String) {
    val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val selector = UiSelector().resourceId(resourceId)
    assertThat(uiDevice.findObject(selector).waitForExists(10_000)).isTrue()
}
