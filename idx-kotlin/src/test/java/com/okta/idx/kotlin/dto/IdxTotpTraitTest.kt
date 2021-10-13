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
package com.okta.idx.kotlin.dto

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IdxTotpTraitTest {
    @Test fun testAsImage() {
        val trait = IdxTotpTrait("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMgAAADICAYAAACtWK6eAAAE8klEQVR42u3dwYrjMBAE0Pz/T+/eB5ZliNVVLb+COWVIHEsv0Jbc/vwRkX/m4xSIACICiAggIoCIACICiAggIoCIACIigIgAIgKICCAigIgAIgKICCA/3+jzGf373+f/9vh++35Pv//p4/vt8ae/79PzAxBAAAEEEEAAAQSQ+4E8Xix9+f7tE/A0qOnj2zY/AAEEEEAAAQQQQAC5D8h0Ef4tkPbvny5ypy+ipAACAggggAACCCCAAALIqYW9byfE1MLVFJhtFyEAAQQQQAABBBBAAAFkW5E+DTD9eacvGgACCCCAAAIIIIAAAkgaSPr92yfsdNE+ffw2KwICCCCAAAIIIIAAchpIe9MGr2df19UEEK8DAojXAQHE64DcltNFYbrITd/g1Ha+j40jIIAAAggggAACCCC1QKabBLR9/tMTctvxn/686zYrAgIIIIAAAggggAAyDqS9CG77wXhb47vTgAEBBBBAAAEEEEAA2Q8kXYSlmw60n7/p8UmPLyCAAAIIIIAAAggg9wNJP9g+XYRu+z5tF0VedxULEEAAAQQQQAABBJD1ReR0EZ5uDDddJG8DCAgggAACCCCAAALI+4C0TcC33dDVuvDW+gMMCCCAAAIIIIAAAoh1kPbNjOm/6YsC6WbTgAACCCCAAAIIIIAAcnoAphcSt12EaH+IZltzbUAAAQQQQAABBBBA7gfS/tDH0wt/6YW8dFOHts2VgAACCCCAAAIIIIAo0tMLg7dNwLZm0qd/YMfmJSCAAAIIIIAAAggga4FsW0g6DWzb+ZlukgEIIIAAAggggAACCCDTYNoGeHujutNF9/QNUa+/YQoQQAABBBBAAAEEkPjmvXQjuekmDNMP9Ek3pksV9YAAAggggAACCCCA7C3S2xqbtRX97Td0td0QBggggAACCCCAAAIIIOlm0+mFybaFxvYbwNp+cAEBBBBAAAEEEEAA2Q/kdBF8eoDagW5vzg0IIIAAAggggAACCCDTRfrTE+jpCdX+ENI0wOm8bjcvIIAAAggggAACCCDHv3AbqPSEP31+0+crvdkTEEAAAQQQQAABBJD7+mI9vdCU3mzY9gPRthC5fXMlIIAAAggggAACCCD39eZtO+FtC2ntRfPp8Zr+f0AAAQQQQAABBBBAAGlr/LZtYW97k4vWiyqAAAIIIIAAAggggOzZrDi9WW77gKQfmnk67TdkAQIIIIAAAggggACyD8j0QyfTRW1bs+f0DUbpG94AAQQQQAABBBBAAHkfkG0TanqA0wup0++X/gEEBBBAAAEEEEAAAeR9QG5/IEx6s156PKYneKpoBwQQQAABBBBAAAFk7zpI+iLA9IDc1jQivRB8+iIBIIAAAggggAACCCD7gGzbTNheJD89IVy0AAQQQAABBBBAAAHkdiCni/Dpov100Xy6qUT7A2xe/4QpQAABBBBAAAEEEEDGF8bevlkx3Wy67SJNSxEPCCCAAAIIIIAAAgggKUDtA97WOK7thitAAAEEEEAAAQQQQABpK9KmG6m1NS1oO97rn3ILCCCAAAIIIIAAAsjxEzw9gNuPv6259PRFkfR4AQIIIIAAAggggACyD0j6ATNPF5nbH7Dz9IS8rakGIIAAAggggAACCCD7gIjcGEBEABEBRAQQEUBEABEBRAQQEUBEABERQEQAEQFEBBARQEQAEQFE5Jr8Be5WavNTCnMNAAAAAElFTkSuQmCC", null)
        val bitmap = trait.asImage()
        assertThat(bitmap?.width).isEqualTo(200)
        assertThat(bitmap?.height).isEqualTo(200)
    }

    @Test fun testAsImageWithMalformedData() {
        val trait = IdxTotpTrait("data:image/png;base64,iVBORw%", null)
        assertThat(trait.asImage()).isNull()
    }
}
