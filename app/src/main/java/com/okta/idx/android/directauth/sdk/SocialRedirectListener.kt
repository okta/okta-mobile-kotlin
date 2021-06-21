package com.okta.idx.android.directauth.sdk

import android.net.Uri

sealed class SocialRedirect {
    object Cancelled : SocialRedirect()
    data class Data(val uri: Uri) : SocialRedirect()
}

typealias SocialRedirectListener = ((redirect: SocialRedirect) -> Unit)
