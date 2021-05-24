package com.okta.idx.android.directauth

import com.okta.idx.sdk.api.client.ProceedContext
import java.util.concurrent.atomic.AtomicReference

internal object TestingGlobals {
    // This should not be read from production code, only set.
    val CURRENT_PROCEED_CONTEXT = AtomicReference<ProceedContext>()
}
