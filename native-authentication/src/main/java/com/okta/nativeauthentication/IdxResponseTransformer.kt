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

import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.idx.kotlin.client.InteractionCodeFlow
import com.okta.idx.kotlin.dto.IdxAuthenticator
import com.okta.idx.kotlin.dto.IdxAuthenticatorCollection
import com.okta.idx.kotlin.dto.IdxIdpCapability
import com.okta.idx.kotlin.dto.IdxMessageCollection
import com.okta.idx.kotlin.dto.IdxPollAuthenticatorCapability
import com.okta.idx.kotlin.dto.IdxPollRemediationCapability
import com.okta.idx.kotlin.dto.IdxRecoverCapability
import com.okta.idx.kotlin.dto.IdxRemediation
import com.okta.idx.kotlin.dto.IdxResendCapability
import com.okta.idx.kotlin.dto.IdxResponse
import com.okta.nativeauthentication.form.Element
import com.okta.nativeauthentication.form.Form

internal interface IdxResponseTransformer {
    fun transform(
        resultHandler: suspend (resultProducer: suspend (InteractionCodeFlow) -> OAuth2ClientResult<IdxResponse>) -> Unit,
        response: IdxResponse,
        clickHandler: (IdxRemediation, Form) -> Unit,
    ): Form.Builder
}

internal class RealIdxResponseTransformer : IdxResponseTransformer {
    override fun transform(
        resultHandler: suspend (resultProducer: suspend (InteractionCodeFlow) -> OAuth2ClientResult<IdxResponse>) -> Unit,
        response: IdxResponse,
        clickHandler: (IdxRemediation, Form) -> Unit,
    ): Form.Builder {
        val builder = Form.Builder()
        builder.elements.addAll(response.messages.asElementBuilders())
        for (remediation in response.remediations) {
            for (field in remediation.form.visibleFields) {
                builder.elements.addAll(field.asElementBuilders(remediation))
            }
            builder.elements.addAll(remediation.actionAsElementBuilders(clickHandler))
            builder.elements.addAll(remediation.resendCodeElement(clickHandler))
            remediation.pollingAction(resultHandler)?.let { action -> builder.launchActions += { action() } }
        }
        builder.elements.addAll(response.recoverElement(clickHandler))
        return builder
    }

    private fun IdxMessageCollection.asElementBuilders(): List<Element.Builder<*>> {
        val elements = mutableListOf<Element.Builder<*>>()
        for (message in messages) {
            elements += Element.Label.Builder(
                remediation = null,
                text = message.message,
                type = Element.Label.Type.ERROR,
            )
        }
        return elements
    }

    private fun IdxRemediation.Form.Field.asElementBuilders(
        remediation: IdxRemediation,
        forceRequired: Boolean = false,
    ): List<Element.Builder<*>> {
        return when {
            // Nested form inside a field.
            !form?.visibleFields.isNullOrEmpty() -> {
                val result = mutableListOf<Element.Builder<*>>()
                form?.visibleFields?.forEach {
                    result += it.asElementBuilders(remediation, isRequired)
                }
                result
            }
            // Options represent multiple choice items like authenticators or security questions and can be nested.
            !options.isNullOrEmpty() -> {
                options?.let { options ->
                    val transformed = options.map {
                        val nestedElements = it.form?.visibleFields?.flatMap { field ->
                            field.asElementBuilders(remediation)
                        } ?: emptyList()
                        Element.Options.Option.Builder(
                            field = it,
                            elements = nestedElements.toMutableList(),
                            label = it.label ?: "",
                        )
                    }
                    val elementBuilder = Element.Options.Builder(
                        remediation = remediation,
                        options = transformed.toMutableList(),
                        isRequired = isRequired,
                        errorMessage = messages.joinToString(separator = "\n") { it.message },
                        valueUpdater = {
                            selectedOption = it
                        },
                    )
                    listOf(elementBuilder)
                } ?: emptyList()
            }
            // Simple text field.
            (type == "string") -> {
                val elementBuilder = Element.TextInput.Builder(
                    remediation = remediation,
                    idxField = this,
                    label = label ?: "",
                    isSecret = isSecret,
                    value = (value as? String?) ?: "",
                    isRequired = isRequired || forceRequired,
                    errorMessage = messages.joinToString(separator = "\n") { it.message },
                )
                listOf(elementBuilder)
            }
            else -> {
                emptyList()
            }
        }
    }

    private fun IdxRemediation.actionAsElementBuilders(clickHandler: (IdxRemediation, Form) -> Unit): List<Element.Builder<*>> {
        if (form.visibleFields.isEmpty() && capabilities.get<IdxPollRemediationCapability>() != null) {
            return emptyList()
        }

        val title = when (type) {
            IdxRemediation.Type.SKIP -> "Skip"
            IdxRemediation.Type.ENROLL_PROFILE, IdxRemediation.Type.SELECT_ENROLL_PROFILE -> "Sign Up"
            IdxRemediation.Type.SELECT_IDENTIFY, IdxRemediation.Type.IDENTIFY -> "Sign In"
            IdxRemediation.Type.SELECT_AUTHENTICATOR_AUTHENTICATE, IdxRemediation.Type.SELECT_AUTHENTICATOR_ENROLL -> "Choose"
            IdxRemediation.Type.LAUNCH_AUTHENTICATOR -> "Launch Authenticator"
            IdxRemediation.Type.CANCEL -> "Restart"
            IdxRemediation.Type.UNLOCK_ACCOUNT -> "Unlock Account"
            IdxRemediation.Type.REDIRECT_IDP -> {
                capabilities.get<IdxIdpCapability>()?.let { capability ->
                    "Login with ${capability.name}"
                } ?: "Social Login"
            }
            else -> "Continue"
        }

        val builder = Element.Action.Builder(
            remediation = this,
            text = title,
            onClick = { form ->
                clickHandler(this, form)
            },
        )
        return listOf(builder)
    }

    private fun IdxRemediation.resendCodeElement(clickHandler: (IdxRemediation, Form) -> Unit): List<Element.Builder<*>> {
        val capability = authenticators.capability<IdxResendCapability>() ?: return emptyList()
        if (form.visibleFields.find { it.type != "string" } == null) {
            return emptyList() // There is no way to type in the code yet.
        }
        val builder = Element.Action.Builder(
            remediation = capability.remediation,
            text = "Resend Code",
            onClick = { form ->
                clickHandler(capability.remediation, form)
            },
        )
        return listOf(builder)
    }

    private fun IdxResponse.recoverElement(clickHandler: (IdxRemediation, Form) -> Unit): List<Element.Builder<*>> {
        val capability = authenticators.current?.capabilities?.get<IdxRecoverCapability>() ?: return emptyList()
        val builder = Element.Action.Builder(
            remediation = capability.remediation,
            text = "Recover",
            onClick = { form ->
                clickHandler(capability.remediation, form)
            },
        )
        return listOf(builder)
    }

    private inline fun <reified Capability : IdxAuthenticator.Capability> IdxAuthenticatorCollection.capability(): Capability? {
        val authenticator = firstOrNull { it.capabilities.get<Capability>() != null } ?: return null
        return authenticator.capabilities.get()
    }

    private fun IdxRemediation.pollingAction(
        resultHandler: suspend (resultProducer: suspend (InteractionCodeFlow) -> OAuth2ClientResult<IdxResponse>) -> Unit,
    ): (suspend () -> Unit)? {
        val remediationCapability = capabilities.get<IdxPollRemediationCapability>()
        val authenticatorCapability = authenticators.capability<IdxPollAuthenticatorCapability>()

        // Create a poll function for the available capability.
        val pollFunction = when {
            remediationCapability != null -> remediationCapability::poll
            authenticatorCapability != null -> authenticatorCapability::poll
            else -> return null
        }

        return suspend {
            resultHandler { interactionCodeFlow ->
                pollFunction(interactionCodeFlow)
            }
        }
    }
}
