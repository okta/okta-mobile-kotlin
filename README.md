[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Okta IDX Android

* [Need help?](#need-help)
* [Getting started](#getting-started)
* [Dependencies](#dependencies)
* [Running This Sample](#running-this-sample)
* [Contributing](#contributing)

This repository contains a sample Android application which can be used a reference for using 
[Okta IDX Java][okta-idx-java-github] on Android. 

> :grey_exclamation: The use of this project requires you to be a part of our limited general availability (LGA) program with access to Okta Identity Engine. If you want to request to be a part of our LGA program for Okta Identity Engine, please reach out to your account manager. If you do not have an account manager, please reach out to oie@okta.com for more information.

## Need help?

If you run into problems using the SDK, you can

* Ask questions on the [Okta Developer Forums][devforum]
* Post [issues][github-issues] here on GitHub (for code errors)

## Getting started

### Prerequisites

#### Okta Admin Dashboard 

Before running this sample, you will need the following:

* An Okta Developer Account, you can sign up for one at <https://developer.okta.com/signup/>.
* An Okta Application, configured for Mobile app.
    1. After login, from the Admin dashboard, navigate to **Applications**&rarr;**Add Application**
    2. Choose **Native** as the platform
    3. Populate your new Native OpenID Connect application with values similar to:

        | Setting              | Value                                               |
        | -------------------- | --------------------------------------------------- |
        | Application Name     | MyApp *(must be unique)*        |
        | Login URI            | com.okta.example:/callback                          |
        | End Session URI      | com.okta.example:/logoutCallback                    |
        | Allowed grant types  | Authorization Code, Refresh Token *(recommended)*   |

    4. Click **Finish** to redirect back to the *General Settings* of your application.
    5. Copy the **Client ID**, as it will be needed for the client configuration.
    6. Get your issuer, which is a combination of your Org URL (found in the upper right of the console home page) . For example, <https://dev-1234.oktapreview.com.>

**Note:** *As with any Okta application, make sure you assign Users or Groups to the application. Otherwise, no one can use it.*

#### Android Studio

Open the project in Android Studio

### Configuration

Update the `Network` class at `Network.kt` in the projects `app/src/main/java/com/okta/idx/android/network/` directory with
the contents created from the [okta admin dashboard](#Okta-Admin-Dashboard):

```kotlin
fun idxClient(): IDXClient {
    return Clients.builder()
        .setIssuer("https://YOUR_ORG.okta.com/oauth2/default")
        .setClientId("{client_id}")
        .setScopes(setOf("openid", "profile", "offline_access"))
        .setRedirectUri("{redirect_uri}") // example: com.oktasample.oie:/callback
        .build()
}
```

**Notes:**
- `discovery_uri` can be customized for specific authorization servers. See [Discovery URI Guidance](https://github.com/okta/okta-oidc-android#discovery-uri-guidance) for more info.
- To receive a **refresh_token**, you must include the `offline_access` scope.
- `end_session_redirect_uri` is a mandatory parameter.

## Dependencies

This sample uses [Okta IDX Java Library][okta-idx-java-github] dependency in `build.gradle` file:

```bash
implementation "com.okta.idx.sdk:okta-idx-java-impl::${okta.sdk.version}"
```

See the latest release [here][okta-idx-java-releases].

## Running This Sample

You can open this sample into Android Studio or build it using gradle.

```bash
./gradlew :app:assembleDebug
```

## Contributing

We are happy to accept contributions and PRs! Please see the [contribution guide](CONTRIBUTING.md) to understand how to structure a contribution.

[devforum]: https://devforum.okta.com/
[github-issues]: https://github.com/okta/okta-idx-android/issues
[okta-idx-java-releases]: https://github.com/okta/okta-idx-java/releases
[okta-idx-java-github]: https://github.com/okta/okta-idx-java
