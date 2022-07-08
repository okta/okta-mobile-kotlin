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
package com.okta.dokka.suppressinternal

import org.jetbrains.dokka.base.transformers.documentables.SuppressedByConditionDocumentableFilterTransformer
import org.jetbrains.dokka.model.Annotations
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.properties.WithExtraProperties
import org.jetbrains.dokka.plugability.DokkaContext

class SuppressInternalApiTransformer(context: DokkaContext) : SuppressedByConditionDocumentableFilterTransformer(context) {
    override fun shouldBeSuppressed(d: Documentable): Boolean {
        @Suppress("UNCHECKED_CAST")
        return (d as? WithExtraProperties<out Documentable>)?.isInternalApi ?: false
    }
}

private val WithExtraProperties<out Documentable>.isInternalApi
    get() = extra[Annotations]?.let { annotations ->
        annotations.directAnnotations.values.flatten().firstOrNull {
            it.dri.toString() == "com.okta.authfoundation/InternalAuthFoundationApi///PointingToDeclaration/"
        }
    } != null
