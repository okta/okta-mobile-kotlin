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
package com.okta.authfoundation.client

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.jwt.Jwt
import com.okta.authfoundation.jwt.JwtParser
import com.okta.testhelpers.OktaRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Rule
import org.junit.Test
import kotlin.test.fail

/**
 * Id tokens are generated from here: https://jwt.io/
 * Header:
 *  {"kid": "FJA0HGNtsuuda_Pl45J42kvQqcsu_0C4Fg7pbJLXTHY","alg": "RS256"}
 * Payload:
 *  {"sub": "00ub41z7mgzNqryMv696","name": "Jay Newstrom","email": "jaynewstrom@example.com","ver": 1,"iss": "https://example-test.okta.com/oauth2/default","aud": "unit_test_client_id","iat": 1644347069,"exp": 1644350669,"jti": "ID.55cxBtdYl8l6arKISPBwd0yOT-9UCTaXaQTXt2laRLs","amr": ["pwd"],"idp": "00o8fou7sRaGGwdn4696","sid": "idxWxklp_4kSxuC_nU1pXD-nA","preferred_username": "jaynewstrom@example.com","auth_time": 1644347068,"at_hash": "gMcGTbhGT1G_ldsHoJsPzQ","ds_hash": "DAeLOFRqifysbgsrbOgbog"}
 * Signature Public Key:
 *  -----BEGIN PUBLIC KEY-----
    MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA2xcCZycepeC6tbbIldJ6
    d2qMN/absNkv84h9NA/UzOlrbBil3ZlhZ/1471fOSQ3tJjT+6OcOIH1Wp3JvOurz
    puoGrKRyHJfkPD6jNoGb+5Cm2nCM5k4BJjK4pS/X6fkNhYZO62V1jR8rVNQtuE+O
    AGjDX6QqfhBFZsimScfBF1oA4wmdTIHdfmywweT0uQoGmm0Kymnc8A4Rn3Grp6rb
    mMm9crlF3xC+Aglb4kHb5LRngyPvvP1HcI5vNph7Do09t/6Lm+Wc59ZLKhgeJLXE
    hCZOeUxMo048R/vLkNtq5SUK9glQ3vlOZHl4ldhvCShKuBtyFimkqnkL6ARjYDsM
    +QIDAQAB
    -----END PUBLIC KEY-----
 * Signature Private Key:
 *  -----BEGIN PRIVATE KEY-----
    MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDbFwJnJx6l4Lq1
    tsiV0np3aow39puw2S/ziH00D9TM6WtsGKXdmWFn/XjvV85JDe0mNP7o5w4gfVan
    cm866vOm6gaspHIcl+Q8PqM2gZv7kKbacIzmTgEmMrilL9fp+Q2Fhk7rZXWNHytU
    1C24T44AaMNfpCp+EEVmyKZJx8EXWgDjCZ1Mgd1+bLDB5PS5CgaabQrKadzwDhGf
    caunqtuYyb1yuUXfEL4CCVviQdvktGeDI++8/Udwjm82mHsOjT23/oub5Zzn1ksq
    GB4ktcSEJk55TEyjTjxH+8uQ22rlJQr2CVDe+U5keXiV2G8JKEq4G3IWKaSqeQvo
    BGNgOwz5AgMBAAECggEAZHXZiTk76W3xz08ADQsVUtqNbz/qRh5gyXfFiXDU8Bz8
    P/XRYJprOsbUhFMr6P20x3c3h84jASzX5jIn5MlFbj0TUGibVpcjdah3KJAn2SOM
    Ds/bG+OazUwmtMAKbmPgGmDqoS/Fxi8LrHsad9Aq2e8v3xQk0+dcG3RYI66v0KeA
    Rdq1GQ4DsFyICwWqhbjz0gBx45QGn5U9PPmXrpQpXR//HdUQWmYqth/549Udpt+A
    2S5QSpeK+7kZKzGK1RyOO+Guw4gku1V8NrqKEhCexgGneEXyYc35v1Sie0Zhtn6H
    4yg5zmBMi/KZZFSqEHv46dXgckPJDo0Gb8lUV/JgXQKBgQDz5iQE8zfEkUH3f+Oi
    vuiNzNlBucZEfAvgke+NDu9PNV6wJdUlU21CteDcRBeJIcGrVs8CxgZRIx6JDon3
    sBacykf5JvjZDEbvNs3h6/nuAtMx7PtRFRsyP3swC5KqRDP8Uq6PfE4aE5FsPHah
    97u+FnpI826OyNf/gnwMNXb/pwKBgQDl9cIbIdkJkFFr3iza/bAFj3bOIi4aD/KC
    itJwK/K+FbtQg/sijU3KIOKVjAdVDZ1WADG2lMcftdoav9brPgdgxPWuyeJYIfmX
    1gei9luEQkO0k36CpBkpqT3HWdSoXAoBce+JlyCARssW+zSl1Dc8J8lD2X4FBQqQ
    IjxTX7IiXwKBgQC7iE5TvAs6ShI10pDeNvoq5cJ7BfPL/rFHOA7AICajeb7XpA9S
    huYw8BX4ZybNmzYFn1bGpCqBQoadDZ/J4gxQ/DwA+BVJFmaIUlRVjRL8DhIDhlrq
    ylbB+QuoMo3P+2cZcR2lWAfZhwg+9/KjsQ8bJr9ZzktI4GcsoFDvNkDMawKBgG9X
    0yg391J+Ii5MYQOXmcbXc/rS6eeMmStD9CiD3wDSnOObQ9my+VtJGOy35ET2Vpvx
    dCCnYNKlxnj1Mias3f2o4BxFe+aYbLVr2D67cgxT2Vxxneu7cMOPQm5nvGPYTK/u
    bsD7/6ycmnECKLeyTRw/V2AWysG7cyXerb7gsuuZAoGBAKt6NNVpxqmHB6/Wdqsi
    sct6YBeZSLPuK/PtK5anVwTYlN7Omg2BjS+/yCygNuDp+px9ykHwYicSgDsJxyOt
    1Spw+uMVoNG6yvIE3q7mjhDoHDu5lUA8s6uzzcTsvDnSLR6uNV2iqDzR/Osu3+bp
    QjRCFXd0Tr5PlKpAHb6dk+aI
    -----END PRIVATE KEY-----
 */
class DefaultIdTokenValidatorTest {
    private val idTokenValidator: IdTokenValidator = DefaultIdTokenValidator()
    @get:Rule val oktaRule = OktaRule(idTokenValidator)

    private suspend fun createJwt(idToken: String): Jwt {
        val parser = JwtParser(oktaRule.configuration.json, Dispatchers.Unconfined)
        return parser.parse(idToken)
    }

    @Test fun testValidIdToken(): Unit = runBlocking {
        val idToken =
            "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6InVuaXRfdGVzdF9jbGllbnRfaWQiLCJpYXQiOjE2NDQzNDcwNjksImV4cCI6MTY0NDM1MDY2OSwianRpIjoiSUQuNTVjeEJ0ZFlsOGw2YXJLSVNQQndkMHlPVC05VUNUYVhhUVRYdDJsYVJMcyIsImFtciI6WyJwd2QiXSwiaWRwIjoiMDBvOGZvdTdzUmFHR3dkbjQ2OTYiLCJzaWQiOiJpZHhXeGtscF80a1N4dUNfblUxcFhELW5BIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiamF5bmV3c3Ryb21AZXhhbXBsZS5jb20iLCJhdXRoX3RpbWUiOjE2NDQzNDcwNjgsImF0X2hhc2giOiJnTWNHVGJoR1QxR19sZHNIb0pzUHpRIiwiZHNfaGFzaCI6IkRBZUxPRlJxaWZ5c2Jnc3JiT2dib2cifQ.tT8aKK4r8yFcW9KgVtZxvjXRJVzz-_rve14CVtpUlyvCTE1yj20wmPS0z3-JirI9xXgt5KeNPYqo3Wbv8c9XY_HY3hsPQdILYpPsUkf-sctmzSoKC_dTbs5xe8uKSgmpMrggfUAWrNPiJt9Ek2p7GgP64Wx79Pq5vSHk0yWlonFfXut5ahpSfqWilmYlvLr8gFbqoLnAJfl4ZbTY8pPw_aQgCdcQ-ImHRu-8bCSCtbFRzZB-SMJFLfRF2kmx0H-QF855wUODTuUSydkff-BKb-8wnbqWg0R9NvRdoXhEybv8TXXZY3cQqgolWLAyiPMrz07n0q_UEjAilUiCjn1f4Q"
        val client = oktaRule.createOidcClient(oktaRule.createEndpoints("https://example-test.okta.com".toHttpUrl().newBuilder()))
        idTokenValidator.validate(client, createJwt(idToken))
    }

    @Test fun testInvalidIssuer() {
        val idToken =
            "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2ludmFsaWQtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6InVuaXRfdGVzdF9jbGllbnRfaWQiLCJpYXQiOjE2NDQzNDcwNjksImV4cCI6MTY0NDM1MDY2OSwianRpIjoiSUQuNTVjeEJ0ZFlsOGw2YXJLSVNQQndkMHlPVC05VUNUYVhhUVRYdDJsYVJMcyIsImFtciI6WyJwd2QiXSwiaWRwIjoiMDBvOGZvdTdzUmFHR3dkbjQ2OTYiLCJzaWQiOiJpZHhXeGtscF80a1N4dUNfblUxcFhELW5BIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiamF5bmV3c3Ryb21AZXhhbXBsZS5jb20iLCJhdXRoX3RpbWUiOjE2NDQzNDcwNjgsImF0X2hhc2giOiJnTWNHVGJoR1QxR19sZHNIb0pzUHpRIiwiZHNfaGFzaCI6IkRBZUxPRlJxaWZ5c2Jnc3JiT2dib2cifQ.fCgpBN0j4Pde9wskDVgMtX32_uFv7LJvTuGgY4jQ-W6Wb2L6AwXZG-Uw70BpvLo-30v-B295M0QPSUQ0d0dSWa38g94wvqNEyG2gisq7HtXuhlxgSJgDk5E554u9pRNYnj5NXCavpc5EAqFvzq8tSZxDaL4tnqxVb25KcELzQ3bLkBZSIvpy1kyCwPtfmTFT-EVydgJZ2YFSNmvOqEbi0uQGAHPUpGH9i5oibG9LEu0HDfPHYOrryhz8a1ScOwXmWao6AWiIGpGP2n6qNQ9Ee68TTz2XrVFVvwsg9GOKTwv_8LzkSfZvWbkow0TIXVJvC4xpn48viH6ggK8DG3GuPA"
        assertFailsWithMessage("Invalid issuer.", idToken)
    }

    @Test fun testInvalidAudience() {
        val idToken =
            "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6Im1pc21hdGNoIiwiaWF0IjoxNjQ0MzQ3MDY5LCJleHAiOjE2NDQzNTA2NjksImp0aSI6IklELjU1Y3hCdGRZbDhsNmFyS0lTUEJ3ZDB5T1QtOVVDVGFYYVFUWHQybGFSTHMiLCJhbXIiOlsicHdkIl0sImlkcCI6IjAwbzhmb3U3c1JhR0d3ZG40Njk2Iiwic2lkIjoiaWR4V3hrbHBfNGtTeHVDX25VMXBYRC1uQSIsInByZWZlcnJlZF91c2VybmFtZSI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwiYXV0aF90aW1lIjoxNjQ0MzQ3MDY4LCJhdF9oYXNoIjoiZ01jR1RiaEdUMUdfbGRzSG9Kc1B6USIsImRzX2hhc2giOiJEQWVMT0ZScWlmeXNiZ3NyYk9nYm9nIn0.13wgKC4sNq06yMFJIcHCB4mv0nD7EJZmtYns8ntgFafcY57x_P1x_C39n-3IGcYA77hSiMZCf0zlzuLlriy19p4Leyc4x9BU5gEbX8-yrbOMpvSq2ef9M1FBhWUkJ__o6EUGOytgn9veCHbOEXopcHYd7IOzvxPYv2-c8oKZCyCloLZD49dNK_s6H9uuxNHzV_kPrmP3J0Mab_tN0lwCqHClYxumEeGK8I8E27shpUJcfPlEmTZX2VSgFBDHAdPvxaf05iN8QDX2MmITWlFuIC_R16GvAzwLRmErM21G1ZSusqMUjAoEFlXso7ucxzZS-n6-1tH76wp7GWfutP1x0w"
        assertFailsWithMessage("Invalid audience.", idToken)
    }

    @Test fun testInvalidHttps() {
        val idToken =
            "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwOi8vZXhhbXBsZS10ZXN0Lm9rdGEuY29tL29hdXRoMi9kZWZhdWx0IiwiYXVkIjoidW5pdF90ZXN0X2NsaWVudF9pZCIsImlhdCI6MTY0NDM0NzA2OSwiZXhwIjoxNjQ0MzUwNjY5LCJqdGkiOiJJRC41NWN4QnRkWWw4bDZhcktJU1BCd2QweU9ULTlVQ1RhWGFRVFh0MmxhUkxzIiwiYW1yIjpbInB3ZCJdLCJpZHAiOiIwMG84Zm91N3NSYUdHd2RuNDY5NiIsInNpZCI6ImlkeFd4a2xwXzRrU3h1Q19uVTFwWEQtbkEiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJqYXluZXdzdHJvbUBleGFtcGxlLmNvbSIsImF1dGhfdGltZSI6MTY0NDM0NzA2OCwiYXRfaGFzaCI6ImdNY0dUYmhHVDFHX2xkc0hvSnNQelEiLCJkc19oYXNoIjoiREFlTE9GUnFpZnlzYmdzcmJPZ2JvZyJ9.Q-AzBM6ytuKWEyCI92HU79hvXhYU-nI58RGJGkxekZeRsgCBIT_eOs8CVKnWN7Z2AkcZ9Dx88XS5zDlJozQA35RUo_1sD3KG2dvzoVGl0AoDQCWbp3Qp6UiGR7ftKnjGeRZ0uvlxsqQlkZfc1uvvNfgArJBQTweGE4KvIlaDcIprHe_swMWjPg9yr-Qhaw2dYzgZ_go12_J0pjXkVc0rvKiamR24tLhFrIsCu99XZPhNlcHdrLEtq8YEHC9Ur0TBMxc1Ij3vZW9RJefcDLCsVrNUXPYMmaA9HHDn78zGxXb5b9qudhBCIpE17id2_3P2McKlY3dO4QOEdHdfy2wb-A"
        assertFailsWithMessage("Issuer must use HTTPS.", idToken, "http")
    }

    @Test fun testInvalidAlgorithm() {
        val idToken =
            "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMzODQifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6InVuaXRfdGVzdF9jbGllbnRfaWQiLCJpYXQiOjE2NDQzNDcwNjksImV4cCI6MTY0NDM1MDY2OSwianRpIjoiSUQuNTVjeEJ0ZFlsOGw2YXJLSVNQQndkMHlPVC05VUNUYVhhUVRYdDJsYVJMcyIsImFtciI6WyJwd2QiXSwiaWRwIjoiMDBvOGZvdTdzUmFHR3dkbjQ2OTYiLCJzaWQiOiJpZHhXeGtscF80a1N4dUNfblUxcFhELW5BIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiamF5bmV3c3Ryb21AZXhhbXBsZS5jb20iLCJhdXRoX3RpbWUiOjE2NDQzNDcwNjgsImF0X2hhc2giOiJnTWNHVGJoR1QxR19sZHNIb0pzUHpRIiwiZHNfaGFzaCI6IkRBZUxPRlJxaWZ5c2Jnc3JiT2dib2cifQ.xqwjwvhb9XR4yceShDuAaUAlsZxsKoHj426gkaEDwKqE5vlpIRD8wNYbru9rgqHWkzxRBYOR-I-6aG7EAe_SlnabEOA1PcXTs8rjj2QUhvEznxAQSTauG17HjFMvcEWhMLvx_irVORmjLWSyy15rfDtMUUgbX9v2wQEoOF-gJMgF6mWnlHiHLVPUhSDbp-2DNjn4GgZsZeLgP7wVRJiXfpdZOCtBnz-I58X9LqFuVE1huReK2qZ2-FH4iaTJS2gmycYo64P2fNmhWyz4em4yHoc0lmqZ-caGa6gXcSODWhsHeO0hFfXn5vYr-qa1aIA0RK9czLA7JhFFFF2anTtD4w"
        assertFailsWithMessage("Invalid JWT algorithm.", idToken)
    }

    @Test fun testCurrentTimeAfterExpires() {
        val idToken =
            "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6InVuaXRfdGVzdF9jbGllbnRfaWQiLCJpYXQiOjE2NDQzMTcwNjksImV4cCI6MTY0NDMyMDY2OSwianRpIjoiSUQuNTVjeEJ0ZFlsOGw2YXJLSVNQQndkMHlPVC05VUNUYVhhUVRYdDJsYVJMcyIsImFtciI6WyJwd2QiXSwiaWRwIjoiMDBvOGZvdTdzUmFHR3dkbjQ2OTYiLCJzaWQiOiJpZHhXeGtscF80a1N4dUNfblUxcFhELW5BIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiamF5bmV3c3Ryb21AZXhhbXBsZS5jb20iLCJhdXRoX3RpbWUiOjE2NDQzNDcwNjgsImF0X2hhc2giOiJnTWNHVGJoR1QxR19sZHNIb0pzUHpRIiwiZHNfaGFzaCI6IkRBZUxPRlJxaWZ5c2Jnc3JiT2dib2cifQ.pQfWCv43NiPBGCXZttT9YhVRudghwOMuXCYvx714c7ncnBa1xurZSdAMLY_cHqGuds5KF6pmddWxZIW_pWrpenQzPfK0bFIeIt9BwAeBcXmXou8Uzem6zpfz978uLP8izlteakuB_aiRTfHZmmnG9kwC4pAAVTs1zvepvlaex_0c3A4vEq3z4hsDaAnO-M2QmCCE0kOWhl0xKljV-YwYXPDQpjO3TN6eDitiaIuKTUY4hAKdif3h68lVCvckZ22UveQCgiB758R0XGfXg7k9bKCgfPZ5IW-5vinfUi6fDKeb1JRE5pCYTjuLD9c-ezv1E1yorIX9t1MfXhGmB1fYpg"
        assertFailsWithMessage("The current time MUST be before the time represented by the exp Claim.", idToken)
    }

    @Test fun testIssuedAtTooFarBeforeNow() {
        val idToken =
            "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6InVuaXRfdGVzdF9jbGllbnRfaWQiLCJpYXQiOjE2NDQzNDY0NjgsImV4cCI6MTY0NDM1MDY2OSwianRpIjoiSUQuNTVjeEJ0ZFlsOGw2YXJLSVNQQndkMHlPVC05VUNUYVhhUVRYdDJsYVJMcyIsImFtciI6WyJwd2QiXSwiaWRwIjoiMDBvOGZvdTdzUmFHR3dkbjQ2OTYiLCJzaWQiOiJpZHhXeGtscF80a1N4dUNfblUxcFhELW5BIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiamF5bmV3c3Ryb21AZXhhbXBsZS5jb20iLCJhdXRoX3RpbWUiOjE2NDQzNDcwNjgsImF0X2hhc2giOiJnTWNHVGJoR1QxR19sZHNIb0pzUHpRIiwiZHNfaGFzaCI6IkRBZUxPRlJxaWZ5c2Jnc3JiT2dib2cifQ.H1K3Ovj-djUHTs0gsrgK9zPOTi_QL9VT8-ZSlYmXgmBzDlYFy-mGk3AW8Mcm-JYS3SWZ92m5WRjCBkXU28XAo3nXYf0rNwhdl-Bv9BFO38puuot6wbNm0qX0vPPx9rVL8lCiZAFNcpKsVeHdon5qUhpG7s4SaITEPYqp6-p9i_0W50FkjdwfWNnGMDmdp3IEwmkAnGxUo1_aJwTYPmLPGtZthjk65hYKPCW5N3g7d-vzSTGn1TjkqYMNrs_2xGt9zQL1MQoExNwCEgFa8s3i7uJxXsdL5vFHSpPxtXWBAbuApxmflUr2mBMaXkvjv8phhXnXTTBDJGVy-FGpxMANyQ"
        assertFailsWithMessage("Issued at time is not within the allowed threshold of now.", idToken)
    }

    @Test fun testIssuedAtTooFarAfterNow() {
        val idToken =
            "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6InVuaXRfdGVzdF9jbGllbnRfaWQiLCJpYXQiOjE2NDQzNDc2NzAsImV4cCI6MTY0NDM1MDY2OSwianRpIjoiSUQuNTVjeEJ0ZFlsOGw2YXJLSVNQQndkMHlPVC05VUNUYVhhUVRYdDJsYVJMcyIsImFtciI6WyJwd2QiXSwiaWRwIjoiMDBvOGZvdTdzUmFHR3dkbjQ2OTYiLCJzaWQiOiJpZHhXeGtscF80a1N4dUNfblUxcFhELW5BIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiamF5bmV3c3Ryb21AZXhhbXBsZS5jb20iLCJhdXRoX3RpbWUiOjE2NDQzNDcwNjgsImF0X2hhc2giOiJnTWNHVGJoR1QxR19sZHNIb0pzUHpRIiwiZHNfaGFzaCI6IkRBZUxPRlJxaWZ5c2Jnc3JiT2dib2cifQ.h83I4Lc-6gSnVUz9d6GeRqOCYV48QMPHjGVCc9IcUhVmiPz79RI3vaID096TVUhPNGYa42BsDreuWyToAxEm8lNlGFfRPyXkJOtaAckuA_gyfBWM5PXl3e5XSpgYB2ADkNgQtFLfpRJRaWQm5wCAR_MhHgHhqefTxxoP_ggoo3cVpfo4g6dtjEjkSb-7fpblgM1ewU1n3CUFcJiqPuXSuusuj3kUBttS27mvcludzT1iXf81C6YkMN1drMmDV3yeu0HztiG31blya1dce0LqFhmsJ3Y0tJA04guvfj2HkIiKYJDgYq07NeCPeadZuNM6oqa1liXCXsQtY7eV2CbNdA"
        assertFailsWithMessage("Issued at time is not within the allowed threshold of now.", idToken)
    }

    @Test fun testMalformedPayload() {
        val idToken =
            "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxfQ.eLQinAQv7hC1KC7TvPhTCz2tBbZsyo-RrqfIsxYannaqIFR8G7OG00ynVTIdF53zKFbKdpZXTfiBs4b2qPDT6qIMLpVbBKr55r6M-d5iJ8EKwSfPeV9FzY23059jJ-kaJP8Qtq7feAN0xqd7MmiE2uxB1ZvbD3Rgsgpr5zQfGeuAvhsVn8E0gaTWLmk5oNzXrIkN1DhxEm6bBtXjsAhTAUZFbAwN-RvFjGlLNj349n4Xc6MJQP5E_zah3gAg_6sNg5zaJ6QYhVaEWMb7p5Jy5GR7Yyhi1mZl2PgAc28sgZAxcLndka1UXqm73hHbkhm12frwNWLUOunJ_GdzwaPYNg"
        assertFailsWithMessage(
            "Fields [iss, aud, exp, iat, auth_time] are required for type with serial name 'com.okta.authfoundation.client.IdTokenValidationPayload', but they were missing",
            idToken
        )
    }

    private fun assertFailsWithMessage(message: String, idToken: String, issuerPrefix: String = "https") {
        try {
            val client = oktaRule.createOidcClient(oktaRule.createEndpoints("$issuerPrefix://example-test.okta.com".toHttpUrl().newBuilder()))
            runBlocking { idTokenValidator.validate(client, createJwt(idToken)) }
            fail()
        } catch (e: IllegalStateException) {
            assertThat(e).hasMessageThat().isEqualTo(message)
        } catch (e: SerializationException) {
            assertThat(e).hasMessageThat().isEqualTo(message)
        }
    }
}
