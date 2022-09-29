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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
internal class FormFactoryTest {
    @Test fun testFormFactoryEmitsForm() = runBlocking {
        val channel = Channel<Form>(capacity = Channel.BUFFERED)
        val formFactory = FormFactory(this, channel, emptyList())
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
        val formFactory = FormFactory(this, channel, emptyList())

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
            this,
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

    @Test fun testFormFactoryLaunchesActions(): Unit = runBlocking {
        val channel = Channel<Form>(capacity = Channel.BUFFERED)

        val actionList = Collections.synchronizedList(mutableListOf<String>())

        val formBuilder = Form.Builder()

        formBuilder.launchActions += {
            actionList += "first"
        }
        formBuilder.launchActions += {
            actionList += "second"
        }

        withContext(Dispatchers.IO) {
            val formFactory = FormFactory(this, channel, emptyList())
            formFactory.emit(formBuilder)
        }

        val form = channel.receive()
        assertThat(form.elements).hasSize(0)
        assertThat(actionList).containsExactly("first", "second")
    }

    @Test fun testFormFactoryCancelsPreviouslyLaunchedActions(): Unit = runBlocking {
        val channel = Channel<Form>(capacity = Channel.BUFFERED)

        val actionList = Collections.synchronizedList(mutableListOf<String>())

        lateinit var delayJob: Job
        val formBuilder1 = Form.Builder()
        val firstActionTriggered = CountDownLatch(1)
        formBuilder1.launchActions += {
            actionList += "first"
            delayJob = launch {
                awaitCancellation()
            }
            firstActionTriggered.countDown()
        }

        val secondActionTriggered = CountDownLatch(1)
        val formBuilder2 = Form.Builder()
        formBuilder2.launchActions += {
            actionList += "second"
            secondActionTriggered.countDown()
        }

        withContext(Dispatchers.IO) {
            val formFactory = FormFactory(this, channel, emptyList())
            formFactory.emit(formBuilder1)

            val form1 = channel.receive()
            assertThat(form1.elements).hasSize(0)
            assertThat(firstActionTriggered.await(1, TimeUnit.SECONDS)).isTrue()
            assertThat(delayJob.isCancelled).isFalse()
            assertThat(actionList).containsExactly("first")

            formFactory.emit(formBuilder2)
            val form2 = channel.receive()
            assertThat(form2.elements).hasSize(0)
            assertThat(secondActionTriggered.await(1, TimeUnit.SECONDS)).isTrue()
            assertThat(delayJob.isCancelled).isTrue()
            assertThat(actionList).containsExactly("first", "second")
        }
    }

    @Test fun testEmitDoesNotEmitSameFormTwice(): Unit = runBlocking {
        val channel = Channel<Form>(capacity = Channel.BUFFERED)
        val formFactory = FormFactory(this, channel, emptyList())
        formFactory.emit(LoadingFormBuilder.create())
        formFactory.emit(LoadingFormBuilder.create())
        val form = channel.receive()
        assertThat(form.elements).hasSize(1)
        assertThat(form.elements[0]).isInstanceOf(Element.Loading::class.java)
        assertThat(channel.tryReceive().isFailure).isTrue()
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
