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
@file:UseSerializers(InstantSerializer::class)

package com.okta.idx.kotlin.kmp.v1

import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement

internal object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())

    override fun serialize(
        encoder: Encoder,
        value: Instant,
    ) {
        encoder.encodeString(value.toString())
    }
}

@Serializable
internal data class Response(
    val expiresAt: Instant? = null,
    val intent: String? = null,
    val remediation: IonCollection<Form>? = null,
    val messages: IonCollection<Message>? = null,
    val authenticators: IonCollection<Authenticator>? = null,
    val authenticatorEnrollments: IonCollection<Authenticator>? = null,
    val currentAuthenticatorEnrollment: IonObject<Authenticator>? = null,
    val currentAuthenticator: IonObject<Authenticator>? = null,
    val recoveryAuthenticator: IonObject<Authenticator>? = null,
    val user: IonObject<User>? = null,
    val app: IonObject<App>? = null,
    val successWithInteractionCode: Form? = null,
    val cancel: Form? = null,
    val webauthnAutofillUIChallenge: Map<String, JsonElement>? = null,
)

@Serializable
internal data class IonObject<T>(
    val value: T,
)

@Serializable
internal data class IonCollection<T>(
    val value: List<T>,
)

@Serializable
internal data class User(
    val id: String? = null,
    val profile: Profile? = null,
    val identifier: String? = null,
) {
    @Serializable
    internal data class Profile(
        val firstName: String? = null,
        val lastName: String? = null,
        val timeZone: String? = null,
        val locale: String? = null,
    )
}

@Serializable
internal data class App(
    val id: String,
    val label: String,
    val name: String,
)

@Serializable
internal data class Authenticator(
    val displayName: String? = null,
    val id: String? = null,
    val type: String,
    val key: String? = null,
    val methods: List<Map<String, String>>? = null,
    val settings: Settings? = null,
    val contextualData: Map<String, JsonElement>? = null,
    val profile: Map<String, String>? = null,
    val send: Form? = null,
    val resend: Form? = null,
    val poll: Form? = null,
    val recover: Form? = null,
) {
    @Serializable
    internal data class Settings(
        val complexity: Complexity,
        val age: Age? = null,
    ) {
        @Serializable
        internal data class Complexity(
            val minLength: Int,
            val minLowerCase: Int,
            val minUpperCase: Int,
            val minNumber: Int,
            val minSymbol: Int,
            val excludeUsername: Boolean,
            val excludeAttributes: List<String>,
        )

        @Serializable
        internal data class Age(
            val minAgeMinutes: Int,
            val historyCount: Int,
        )
    }
}

@Serializable
internal data class Form(
    val rel: List<String>? = null,
    val name: String,
    val method: String,
    val href: String,
    val value: List<FormValue>? = null,
    val accepts: String? = null,
    val relatesTo: List<String>? = null,
    val refresh: Double? = null,
    val idp: Map<String, String>? = null,
)

@Serializable
internal data class CompositeFormValue(
    val value: List<FormValue>? = null,
)

@Serializable
internal data class FormValue(
    val id: String? = null,
    val name: String? = null,
    val label: String? = null,
    val type: String? = null,
    val value: JsonElement? = null,
    val required: Boolean? = null,
    val secret: Boolean? = null,
    val visible: Boolean? = null,
    val mutable: Boolean? = null,
    val form: CompositeFormValue? = null,
    val options: List<FormValue>? = null,
    val relatesTo: String? = null,
    val messages: IonCollection<Message>? = null,
)

@Serializable
internal data class Message(
    @SerialName("class") val type: String,
    val i18n: Localization? = null,
    val message: String,
) {
    @Serializable
    internal data class Localization(
        val key: String? = null,
    )
}

@Serializable
internal data class InteractResponse(
    @SerialName("interaction_handle") val interactionHandle: String,
)
