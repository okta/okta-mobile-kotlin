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
package com.okta.nativeauthentication.form

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Test

internal class FormFactoryTest {
    @Test fun testFormFactoryEmitsForm() = runBlocking {
        val channel = Channel<Form>(capacity = Channel.BUFFERED)
        val formFactory = FormFactory(channel, emptyList())
        val formBuilder = Form.Builder()
        val labelBuilder = Element.Label.Builder(null)
        labelBuilder.text = "Fake Label"
        formBuilder.elements += labelBuilder
        formFactory.emit(formBuilder)
        val form = channel.receive()
        assertThat(form.elements).hasSize(1)
        assertThat((form.elements[0] as Element.Label).text).isEqualTo("Fake Label")
    }

    @Test fun testFormFactoryEmitsMultipleForms() = runBlocking {
        val channel = Channel<Form>(capacity = Channel.BUFFERED)
        val formFactory = FormFactory(channel, emptyList())

        for (i in 1..2) {
            val formBuilder = Form.Builder()
            val labelBuilder = Element.Label.Builder(null)
            labelBuilder.text = "Fake Label $i"
            formBuilder.elements += labelBuilder
            formFactory.emit(formBuilder)
        }

        val form1 = channel.receive()
        assertThat(form1.elements).hasSize(1)
        assertThat((form1.elements[0] as Element.Label).text).isEqualTo("Fake Label 1")

        val form2 = channel.receive()
        assertThat(form2.elements).hasSize(1)
        assertThat((form2.elements[0] as Element.Label).text).isEqualTo("Fake Label 2")
    }

    @Test fun testFormFactoryEmitsTransformedForm() = runBlocking {
        val channel = Channel<Form>(capacity = Channel.BUFFERED)
        val formFactory = FormFactory(
            channel,
            listOf(
                ExtraLabelFormTransformer(addToBeginning = true, addToEnd = false),
                ExtraLabelFormTransformer(addToBeginning = false, addToEnd = true)
            )
        )

        val formBuilder = Form.Builder()
        for (i in 1..3) {
            val labelBuilder = Element.Label.Builder(null)
            labelBuilder.text = "Fake Label $i"
            formBuilder.elements += labelBuilder
        }
        formFactory.emit(formBuilder)

        val form1 = channel.receive()
        assertThat(form1.elements).hasSize(5)
        assertThat((form1.elements[0] as Element.Label).text).isEqualTo("Extra Beginning!")
        assertThat((form1.elements[1] as Element.Label).text).isEqualTo("Fake Label 1")
        assertThat((form1.elements[2] as Element.Label).text).isEqualTo("Fake Label 2")
        assertThat((form1.elements[3] as Element.Label).text).isEqualTo("Fake Label 3")
        assertThat((form1.elements[4] as Element.Label).text).isEqualTo("Extra End!")
    }
}

private class ExtraLabelFormTransformer(
    private val addToBeginning: Boolean,
    private val addToEnd: Boolean,
) : FormTransformer {
    override fun Form.Builder.transform() {
        if (addToBeginning) {
            val beginLabelBuilder = Element.Label.Builder(null)
            beginLabelBuilder.text = "Extra Beginning!"
            elements.add(0, beginLabelBuilder)
        }

        if (addToEnd) {
            val endLabelBuilder = Element.Label.Builder(null)
            endLabelBuilder.text = "Extra End!"
            elements += endLabelBuilder
        }
    }
}
