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
package com.okta.nativeauthentication

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.idx.kotlin.client.InteractionCodeFlow
import com.okta.idx.kotlin.dto.IdxRemediation
import com.okta.idx.kotlin.dto.IdxResponse
import com.okta.nativeauthentication.form.Element
import com.okta.nativeauthentication.utils.IdxResponseFactory
import com.okta.testing.network.NetworkRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class RealIdxResponseTransformerTest {
    @get:Rule val networkRule = NetworkRule()

    private val idxResponseFactory = IdxResponseFactory(networkRule)

    private val noInteractionsResponseTransformer: suspend (
        resultProducer: suspend (InteractionCodeFlow) -> OAuth2ClientResult<IdxResponse>
    ) -> Unit = {
        throw AssertionError("Not expected")
    }

    @Test fun basicFormIsReturned() {
        val json = """
        {
          "version": "1.0.0",
          "stateHandle": "029ZAB",
          "expiresAt": "2021-05-21T16:41:22.000Z",
          "intent": "LOGIN",
          "remediation": {
            "type": "array",
            "value": [
              {
                "rel": [
                  "create-form"
                ],
                "name": "identify",
                "href": "https://foo.oktapreview.com/idp/idx/identify",
                "method": "POST",
                "produces": "application/ion+json; okta-version=1.0.0",
                "value": [
                  {
                    "name": "identifier",
                    "label": "Username"
                  },
                  {
                    "name": "stateHandle",
                    "required": true,
                    "value": "029ZAB",
                    "visible": false,
                    "mutable": false
                  }
                ],
                "accepts": "application/json; okta-version=1.0.0"
              }
            ]
          },
          "app": {
            "type": "object",
            "value": {
              "name": "oidc_client",
              "label": "OIE Android Sample",
              "id": "0oal2s4yhspmifyt65d6"
            }
          }
        }
        """.trimIndent()
        val response = idxResponseFactory.fromJson(json)
        val clickCounter = AtomicInteger(0)
        val clickReference = AtomicReference<IdxRemediation>()
        val formBuilder = RealIdxResponseTransformer().transform(noInteractionsResponseTransformer, response) { remediation, _ ->
            clickReference.set(remediation)
            clickCounter.incrementAndGet()
        }
        assertThat(formBuilder.launchActions).isEmpty()
        val elements = formBuilder.elements
        assertThat(elements).hasSize(2)
        assertThat((elements[0] as Element.TextInput.Builder).remediation.name).isEqualTo("identify")
        assertThat((elements[0] as Element.TextInput.Builder).idxField.name).isEqualTo("identifier")
        assertThat((elements[0] as Element.TextInput.Builder).label).isEqualTo("Username")
        assertThat((elements[0] as Element.TextInput.Builder).value).isEqualTo("")
        assertThat((elements[0] as Element.TextInput.Builder).isSecret).isFalse()
        assertThat((elements[1] as Element.Action.Builder).text).isEqualTo("Sign In")
        assertThat(clickCounter.get()).isEqualTo(0)
        (elements[1] as Element.Action.Builder).onClick(formBuilder.build(emptyList()))
        assertThat(clickCounter.get()).isEqualTo(1)
        assertThat(clickReference.get().name).isEqualTo("identify")
    }

    @Test fun nestedFormAddsElement() {
        val json = """
        {
          "version": "1.0.0",
          "stateHandle": "029ZAB",
          "expiresAt": "2021-05-21T16:41:22.000Z",
          "intent": "LOGIN",
          "remediation": {
            "type": "array",
            "value": [
              {
                "rel": [
                  "create-form"
                ],
                "name": "identify",
                "href": "https://foo.oktapreview.com/idp/idx/identify",
                "method": "POST",
                "produces": "application/ion+json; okta-version=1.0.0",
                "value": [
                  {
                    "name": "identifier",
                    "label": "Username"
                  },
                  {
                    "name": "credentials",
                    "type": "object",
                    "form": {
                      "value": [
                        {
                          "name": "passcode",
                          "label": "Password",
                          "secret": true
                        }
                      ]
                    },
                    "required": true
                  },
                  {
                    "name": "stateHandle",
                    "required": true,
                    "value": "029ZAB",
                    "visible": false,
                    "mutable": false
                  }
                ],
                "accepts": "application/json; okta-version=1.0.0"
              }
            ]
          },
          "app": {
            "type": "object",
            "value": {
              "name": "oidc_client",
              "label": "OIE Android Sample",
              "id": "0oal2s4yhspmifyt65d6"
            }
          }
        }
        """.trimIndent()
        val response = idxResponseFactory.fromJson(json)
        val formBuilder = RealIdxResponseTransformer().transform(noInteractionsResponseTransformer, response) { _, _ -> }
        assertThat(formBuilder.launchActions).isEmpty()
        val elements = formBuilder.elements
        assertThat(elements).hasSize(3)
        assertThat((elements[0] as Element.TextInput.Builder).label).isEqualTo("Username")
        assertThat((elements[1] as Element.TextInput.Builder).label).isEqualTo("Password")
        assertThat((elements[1] as Element.TextInput.Builder).isRequired).isTrue()
        assertThat((elements[1] as Element.TextInput.Builder).isSecret).isTrue()
        assertThat((elements[2] as Element.Action.Builder).text).isEqualTo("Sign In")
    }

    @Test fun nestedFormAddsOptionsElement(): Unit = runBlocking {
        val json = """
        {
          "version": "1.0.0",
          "stateHandle": "029ZAB",
          "expiresAt": "2021-05-21T16:41:22.000Z",
          "intent": "LOGIN",
          "remediation": {
            "type": "array",
            "value": [
              {
                "rel": [
                  "create-form"
                ],
                "name": "select-authenticator-authenticate",
                "href": "https://foo.okta.com/idp/idx/challenge",
                "method": "POST",
                "produces": "application/ion+json; okta-version=1.0.0",
                "value": [
                  {
                    "name": "authenticator",
                    "type": "object",
                    "options": [
                      {
                        "label": "Email",
                        "value": {
                          "form": {
                            "value": [
                              {
                                "name": "id",
                                "required": true,
                                "value": "auttbu5xxmIlrSqER5d6",
                                "mutable": false
                              },
                              {
                                "name": "methodType",
                                "required": false,
                                "value": "email",
                                "mutable": false
                              }
                            ]
                          }
                        },
                        "relatesTo": "${'$'}.authenticatorEnrollments.value[0]"
                      },
                      {
                        "label": "Phone",
                        "value": {
                          "form": {
                            "value": [
                              {
                                "name": "id",
                                "required": true,
                                "value": "auttbu5xyM4W2p68j5d6",
                                "mutable": false
                              },
                              {
                                "name": "methodType",
                                "type": "string",
                                "required": false,
                                "options": [
                                  {
                                    "label": "SMS",
                                    "value": "sms"
                                  }
                                ]
                              },
                              {
                                "name": "enrollmentId",
                                "required": true,
                                "value": "paewtuilfHp0NRhpW5d6",
                                "mutable": false
                              }
                            ]
                          }
                        },
                        "relatesTo": "${'$'}.authenticatorEnrollments.value[1]"
                      }
                    ]
                  },
                  {
                    "name": "stateHandle",
                    "required": true,
                    "value": "02jbM3ltYruI-MEq8h8RCpHfnjPy-kJxpVq2HlfO2l",
                    "visible": false,
                    "mutable": false
                  }
                ],
                "accepts": "application/json; okta-version=1.0.0"
              }
            ]
          },
          "authenticatorEnrollments": {
            "type": "array",
            "value": [
              {
                "profile": {
                  "email": "j***8@gmail.com"
                },
                "type": "email",
                "key": "okta_email",
                "id": "eaewtv2kvtxDll3Js5d6",
                "displayName": "Email",
                "methods": [
                  {
                    "type": "email"
                  }
                ]
              },
              {
                "profile": {
                  "phoneNumber": "+1 XXX-XXX-0364"
                },
                "type": "phone",
                "key": "phone_number",
                "id": "paewtuilfHp0NRhpW5d6",
                "displayName": "Phone",
                "methods": [
                  {
                    "type": "sms"
                  }
                ]
              }
            ]
          },
          "app": {
            "type": "object",
            "value": {
              "name": "oidc_client",
              "label": "OIE Android Sample",
              "id": "0oal2s4yhspmifyt65d6"
            }
          }
        }
        """.trimIndent()
        val response = idxResponseFactory.fromJson(json)
        val formBuilder = RealIdxResponseTransformer().transform(noInteractionsResponseTransformer, response) { _, _ -> }
        assertThat(formBuilder.launchActions).isEmpty()
        val elements = formBuilder.build(emptyList()).elements
        assertThat(elements).hasSize(2)
        val optionsElement = elements[0] as Element.Options
        assertThat(optionsElement.option).isNull()
        assertThat(optionsElement.options).hasSize(2)
        assertThat(optionsElement.options[0].label).isEqualTo("Email")
        assertThat(optionsElement.options[0].elements).hasSize(0)
        assertThat(optionsElement.options[1].label).isEqualTo("Phone")
        assertThat(optionsElement.options[1].elements).hasSize(1)
        val nestedPhoneElement = optionsElement.options[1].elements[0] as Element.Options
        assertThat(nestedPhoneElement.option).isNull()
        assertThat(nestedPhoneElement.options).hasSize(1)
        assertThat(nestedPhoneElement.options[0].label).isEqualTo("SMS")
        assertThat((elements[1] as Element.Action).text).isEqualTo("Choose")
    }

    @Test fun recoverElement() {
        val json = """
        {
          "version": "1.0.0",
          "stateHandle": "029ZAB",
          "expiresAt": "2021-05-21T16:41:22.000Z",
          "intent": "LOGIN",
          "remediation": {
            "type": "array",
            "value": [
              {
                "rel": [
                  "create-form"
                ],
                "name": "challenge-authenticator",
                "relatesTo": [
                  "${'$'}.currentAuthenticatorEnrollment"
                ],
                "href": "https://foo.oktapreview.com/idp/idx/challenge/answer",
                "method": "POST",
                "value": [
                  {
                    "name": "credentials",
                    "type": "object",
                    "form": {
                      "value": [
                        {
                          "name": "passcode",
                          "label": "Password",
                          "secret": true
                        }
                      ]
                    },
                    "required": true
                  },
                  {
                    "name": "stateHandle",
                    "required": true,
                    "value": "029ZAB",
                    "visible": false,
                    "mutable": false
                  }
                ],
                "accepts": "application/ion+json; okta-version=1.0.0"
              }
            ]
          },
          "currentAuthenticatorEnrollment": {
            "type": "object",
            "value": {
              "recover": {
                "rel": [
                  "create-form"
                ],
                "name": "recover",
                "href": "https://foo.oktapreview.com/idp/idx/recover",
                "method": "POST",
                "value": [
                  {
                    "name": "stateHandle",
                    "required": true,
                    "value": "029ZAB",
                    "visible": false,
                    "mutable": false
                  }
                ],
                "accepts": "application/ion+json; okta-version=1.0.0"
              },
              "type": "password",
              "id": "lae609uDthwWF3VvV2o4",
              "displayName": "Password",
              "methods": [
                {
                  "type": "password"
                }
              ]
            }
          },
          "app": {
            "type": "object",
            "value": {
              "name": "oidc_client",
              "label": "OIE Android Sample",
              "id": "0oal2s4yhspmifyt65d6"
            }
          }
        }
        """.trimIndent()
        val response = idxResponseFactory.fromJson(json)
        val clickCounter = AtomicInteger(0)
        val clickReference = AtomicReference<IdxRemediation>()
        val formBuilder = RealIdxResponseTransformer().transform(noInteractionsResponseTransformer, response) { remediation, _ ->
            clickReference.set(remediation)
            clickCounter.incrementAndGet()
        }
        assertThat(formBuilder.launchActions).isEmpty()
        val elements = formBuilder.elements
        assertThat(elements).hasSize(3)
        assertThat((elements[0] as Element.TextInput.Builder).remediation.name).isEqualTo("challenge-authenticator")
        assertThat((elements[0] as Element.TextInput.Builder).idxField.name).isEqualTo("passcode")
        assertThat((elements[0] as Element.TextInput.Builder).label).isEqualTo("Password")
        assertThat((elements[0] as Element.TextInput.Builder).value).isEqualTo("")
        assertThat((elements[0] as Element.TextInput.Builder).isSecret).isTrue()
        assertThat((elements[1] as Element.Action.Builder).text).isEqualTo("Continue")
        assertThat((elements[1] as Element.Action.Builder).remediation?.name).isEqualTo("challenge-authenticator")
        (elements[1] as Element.Action.Builder).onClick(formBuilder.build(emptyList()))
        assertThat(clickCounter.get()).isEqualTo(1)
        assertThat(clickReference.get().name).isEqualTo("challenge-authenticator")
        assertThat((elements[2] as Element.Action.Builder).text).isEqualTo("Recover")
        assertThat((elements[2] as Element.Action.Builder).remediation?.name).isEqualTo("recover")
        (elements[2] as Element.Action.Builder).onClick(formBuilder.build(emptyList()))
        assertThat(clickCounter.get()).isEqualTo(2)
        assertThat(clickReference.get().name).isEqualTo("recover")
    }

    @Test fun resendCodeElement() {
        val json = """
        {
          "version": "1.0.0",
          "stateHandle": "02ifdLyhqQ9Il4OtUU50jCdhFeCH-bzojwfpOci9EO",
          "expiresAt": "2021-10-01T17:40:25.000Z",
          "intent": "LOGIN",
          "remediation": {
            "type": "array",
            "value": [
              {
                "rel": [
                  "create-form"
                ],
                "name": "challenge-authenticator",
                "relatesTo": [
                  "${'$'}.currentAuthenticatorEnrollment"
                ],
                "href": "https://foo.okta.com/idp/idx/challenge/answer",
                "method": "POST",
                "produces": "application/ion+json; okta-version=1.0.0",
                "value": [
                  {
                    "name": "credentials",
                    "type": "object",
                    "form": {
                      "value": [
                        {
                          "name": "passcode",
                          "label": "Enter code"
                        }
                      ]
                    },
                    "required": true
                  },
                  {
                    "name": "stateHandle",
                    "required": true,
                    "value": "02ifdLyhqQ9Il4OtUU50jCdhFeCH-bzojwfpOci9EO",
                    "visible": false,
                    "mutable": false
                  }
                ],
                "accepts": "application/json; okta-version=1.0.0"
              }
            ]
          },
          "currentAuthenticatorEnrollment": {
            "type": "object",
            "value": {
              "profile": {
                "email": "j***a@gmail.com"
              },
              "resend": {
                "rel": [
                  "create-form"
                ],
                "name": "resend",
                "href": "https://foo.okta.com/idp/idx/challenge/resend",
                "method": "POST",
                "produces": "application/ion+json; okta-version=1.0.0",
                "value": [
                  {
                    "name": "stateHandle",
                    "required": true,
                    "value": "02ifdLyhqQ9Il4OtUU50jCdhFeCH-bzojwfpOci9EO",
                    "visible": false,
                    "mutable": false
                  }
                ],
                "accepts": "application/json; okta-version=1.0.0"
              },
              "poll": {
                "rel": [
                  "create-form"
                ],
                "name": "poll",
                "href": "https://foo.okta.com/idp/idx/challenge/poll",
                "method": "POST",
                "produces": "application/ion+json; okta-version=1.0.0",
                "refresh": 4000,
                "value": [
                  {
                    "name": "stateHandle",
                    "required": true,
                    "value": "02ifdLyhqQ9Il4OtUU50jCdhFeCH-bzojwfpOci9EO",
                    "visible": false,
                    "mutable": false
                  }
                ],
                "accepts": "application/json; okta-version=1.0.0"
              },
              "type": "email",
              "key": "okta_email",
              "id": "eaewrvclbBPr2PAxl5d6",
              "displayName": "Email",
              "methods": [
                {
                  "type": "email"
                }
              ]
            }
          },
          "authenticators": {
            "type": "array",
            "value": [
              {
                "type": "email",
                "key": "okta_email",
                "id": "auttbu5xxmIlrSqER5d6",
                "displayName": "Email",
                "methods": [
                  {
                    "type": "email"
                  }
                ]
              }
            ]
          },
          "authenticatorEnrollments": {
            "type": "array",
            "value": [
              {
                "profile": {
                  "email": "j***a@gmail.com"
                },
                "type": "email",
                "key": "okta_email",
                "id": "eaewrvclbBPr2PAxl5d6",
                "displayName": "Email",
                "methods": [
                  {
                    "type": "email"
                  }
                ]
              }
            ]
          },
          "user": {
            "type": "object",
            "value": {
              "id": "00uwrvclaP8RzBSq45d6",
              "identifier": "jaynewstromokta@gmail.com"
            }
          },
          "app": {
            "type": "object",
            "value": {
              "name": "oidc_client",
              "label": "IdxSample",
              "id": "0oauaifdh0dynIvL85d6"
            }
          }
        }
        """.trimIndent()
        val response = idxResponseFactory.fromJson(json)
        val clickCounter = AtomicInteger(0)
        val clickReference = AtomicReference<IdxRemediation>()
        val formBuilder = RealIdxResponseTransformer().transform(noInteractionsResponseTransformer, response) { remediation, _ ->
            clickReference.set(remediation)
            clickCounter.incrementAndGet()
        }
        assertThat(formBuilder.launchActions).hasSize(1)
        val elements = formBuilder.elements
        assertThat(elements).hasSize(3)
        assertThat((elements[0] as Element.TextInput.Builder).remediation.name).isEqualTo("challenge-authenticator")
        assertThat((elements[0] as Element.TextInput.Builder).idxField.name).isEqualTo("passcode")
        assertThat((elements[0] as Element.TextInput.Builder).label).isEqualTo("Enter code")
        assertThat((elements[0] as Element.TextInput.Builder).value).isEqualTo("")
        assertThat((elements[0] as Element.TextInput.Builder).isSecret).isFalse()
        assertThat((elements[1] as Element.Action.Builder).text).isEqualTo("Continue")
        assertThat((elements[1] as Element.Action.Builder).remediation?.name).isEqualTo("challenge-authenticator")
        (elements[1] as Element.Action.Builder).onClick(formBuilder.build(emptyList()))
        assertThat(clickCounter.get()).isEqualTo(1)
        assertThat(clickReference.get().name).isEqualTo("challenge-authenticator")
        assertThat((elements[2] as Element.Action.Builder).text).isEqualTo("Resend Code")
        assertThat((elements[2] as Element.Action.Builder).remediation?.name).isEqualTo("resend")
        (elements[2] as Element.Action.Builder).onClick(formBuilder.build(emptyList()))
        assertThat(clickCounter.get()).isEqualTo(2)
        assertThat(clickReference.get().name).isEqualTo("resend")
    }

    @Test fun topLevelFormErrors() {
        val json = """
        {
          "version": "1.0.0",
          "stateHandle": "029ZAB",
          "expiresAt": "2021-05-21T16:41:22.000Z",
          "intent": "LOGIN",
          "remediation": {
            "type": "array",
            "value": [
            ]
          },
          "messages": {
            "type": "array",
            "value": [{
              "message": "Expecting a password.",
              "class": "ERROR"
            }]
          },
          "app": {
            "type": "object",
            "value": {
              "name": "oidc_client",
              "label": "OIE Android Sample",
              "id": "0oal2s4yhspmifyt65d6"
            }
          }
        }
        """.trimIndent()
        val response = idxResponseFactory.fromJson(json)
        val formBuilder = RealIdxResponseTransformer().transform(noInteractionsResponseTransformer, response) { _, _ -> }
        assertThat(formBuilder.launchActions).isEmpty()
        val elements = formBuilder.elements
        assertThat(elements).hasSize(1)
        assertThat((elements[0] as Element.Label.Builder).text).isEqualTo("Expecting a password.")
    }

    @Test fun textElementFieldLevelError() {
        val json = """
        {
          "version": "1.0.0",
          "stateHandle": "029ZAB",
          "expiresAt": "2021-05-21T16:41:22.000Z",
          "intent": "LOGIN",
          "remediation": {
            "type": "array",
            "value": [
              {
                "rel": [
                  "create-form"
                ],
                "name": "identify",
                "href": "https://foo.oktapreview.com/idp/idx/identify",
                "method": "POST",
                "produces": "application/ion+json; okta-version=1.0.0",
                "value": [
                  {
                    "name": "identifier",
                    "label": "Username",
                    "messages": {
                      "type": "array",
                      "value": [
                        {
                          "message": "Invalid code. Try again.",
                          "i18n": {
                            "key": "api.authn.error.PASSCODE_INVALID",
                            "params": []
                          },
                          "class": "ERROR"
                        }
                      ]
                    }
                  },
                  {
                    "name": "stateHandle",
                    "required": true,
                    "value": "029ZAB",
                    "visible": false,
                    "mutable": false
                  }
                ],
                "accepts": "application/json; okta-version=1.0.0"
              }
            ]
          },
          "app": {
            "type": "object",
            "value": {
              "name": "oidc_client",
              "label": "OIE Android Sample",
              "id": "0oal2s4yhspmifyt65d6"
            }
          }
        }
        """.trimIndent()
        val response = idxResponseFactory.fromJson(json)
        val formBuilder = RealIdxResponseTransformer().transform(noInteractionsResponseTransformer, response) { _, _ -> }
        assertThat(formBuilder.launchActions).isEmpty()
        val elements = formBuilder.elements
        assertThat(elements).hasSize(2)
        assertThat((elements[0] as Element.TextInput.Builder).errorMessage).isEqualTo("Invalid code. Try again.")
        assertThat(elements[1]).isInstanceOf(Element.Action.Builder::class.java)
    }

    @Test fun optionElementFieldLevelError(): Unit = runBlocking {
        val json = """
        {
          "version": "1.0.0",
          "stateHandle": "029ZAB",
          "expiresAt": "2021-05-21T16:41:22.000Z",
          "intent": "LOGIN",
          "remediation": {
            "type": "array",
            "value": [
              {
                "rel": [
                  "create-form"
                ],
                "name": "select-authenticator-authenticate",
                "href": "https://foo.okta.com/idp/idx/challenge",
                "method": "POST",
                "produces": "application/ion+json; okta-version=1.0.0",
                "value": [
                  {
                    "name": "authenticator",
                    "type": "object",
                    "options": [
                      {
                        "label": "Email",
                        "value": {
                          "form": {
                            "value": [
                              {
                                "name": "id",
                                "required": true,
                                "value": "auttbu5xxmIlrSqER5d6",
                                "mutable": false
                              },
                              {
                                "name": "methodType",
                                "required": false,
                                "value": "email",
                                "mutable": false
                              }
                            ]
                          }
                        },
                        "relatesTo": "${'$'}.authenticatorEnrollments.value[0]"
                      },
                      {
                        "label": "Phone",
                        "value": {
                          "form": {
                            "value": [
                              {
                                "name": "id",
                                "required": true,
                                "value": "auttbu5xyM4W2p68j5d6",
                                "mutable": false
                              },
                              {
                                "name": "methodType",
                                "type": "string",
                                "required": false,
                                "options": [
                                  {
                                    "label": "SMS",
                                    "value": "sms"
                                  }
                                ]
                              },
                              {
                                "name": "enrollmentId",
                                "required": true,
                                "value": "paewtuilfHp0NRhpW5d6",
                                "mutable": false
                              }
                            ]
                          }
                        },
                        "relatesTo": "${'$'}.authenticatorEnrollments.value[1]"
                      }
                    ],
                    "messages": {
                      "type": "array",
                      "value": [
                        {
                          "message": "Invalid code. Try again.",
                          "i18n": {
                            "key": "api.authn.error.PASSCODE_INVALID",
                            "params": []
                          },
                          "class": "ERROR"
                        }
                      ]
                    }
                  },
                  {
                    "name": "stateHandle",
                    "required": true,
                    "value": "02jbM3ltYruI-MEq8h8RCpHfnjPy-kJxpVq2HlfO2l",
                    "visible": false,
                    "mutable": false
                  }
                ],
                "accepts": "application/json; okta-version=1.0.0"
              }
            ]
          },
          "authenticatorEnrollments": {
            "type": "array",
            "value": [
              {
                "profile": {
                  "email": "j***8@gmail.com"
                },
                "type": "email",
                "key": "okta_email",
                "id": "eaewtv2kvtxDll3Js5d6",
                "displayName": "Email",
                "methods": [
                  {
                    "type": "email"
                  }
                ]
              },
              {
                "profile": {
                  "phoneNumber": "+1 XXX-XXX-0364"
                },
                "type": "phone",
                "key": "phone_number",
                "id": "paewtuilfHp0NRhpW5d6",
                "displayName": "Phone",
                "methods": [
                  {
                    "type": "sms"
                  }
                ]
              }
            ]
          },
          "app": {
            "type": "object",
            "value": {
              "name": "oidc_client",
              "label": "OIE Android Sample",
              "id": "0oal2s4yhspmifyt65d6"
            }
          }
        }
        """.trimIndent()
        val response = idxResponseFactory.fromJson(json)
        val formBuilder = RealIdxResponseTransformer().transform(noInteractionsResponseTransformer, response) { _, _ -> }
        assertThat(formBuilder.launchActions).isEmpty()
        val elements = formBuilder.build(emptyList()).elements
        assertThat(elements).hasSize(2)
        val optionsElement = elements[0] as Element.Options
        assertThat(optionsElement.errorMessage).isEqualTo("Invalid code. Try again.")
    }
}
