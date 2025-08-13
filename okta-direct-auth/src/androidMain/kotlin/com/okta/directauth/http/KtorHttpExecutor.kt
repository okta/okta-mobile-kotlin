package com.okta.directauth.http

import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.api.http.ApiResponse

class KtorHttpExecutor : ApiExecutor {
    override suspend fun execute(request: ApiRequest): Result<ApiResponse> = runCatching {
        TODO("https://oktainc.atlassian.net/browse/OKTA-1020908 implementation ticket")
    }
}
