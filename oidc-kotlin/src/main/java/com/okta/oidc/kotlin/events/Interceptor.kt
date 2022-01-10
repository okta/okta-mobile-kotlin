/*
 * Copyright 2021-Present Okta, Inc.
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
package com.okta.oidc.kotlin.events

// TODO: Document
interface Interceptor<I, O> {
    interface Chain<I, O> {
        // TODO: Maybe rename to value?
        val input: I

        fun proceed(input: I): O

        companion object {
            private fun <I, O> create(initialValue: I, interceptors: List<Interceptor<I, O>>): Chain<I, O> {
                return RealChain(initialValue, ArrayList(interceptors))
            }

            fun <I, O> getValue(initialValue: I, interceptors: List<Interceptor<I, O>>): O {
                val chain = create(initialValue, interceptors)
                return chain.proceed(chain.input)
            }
        }
    }

    fun intercept(chain: Chain<I, O>): O
}

internal data class RealChain<I, O>(
    override val input: I,
    private val interceptors: List<Interceptor<I, O>>,
    private val index: Int = 0,
) : Interceptor.Chain<I, O> {
    override fun proceed(input: I): O {
        check(index < interceptors.size)
        val interceptor = interceptors[index]
        val chain = copy(index = index + 1, input = input)
        return interceptor.intercept(chain)
    }
}
