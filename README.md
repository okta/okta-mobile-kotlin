[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Okta IDX Android

This repository contains an SDK written in Kotlin for the Okta Identity Engine, as well as a sample Android application which can be used a reference for using
idx-kotlin on Android.

* [Introduction](#introduction)
* [Installation](#installation)
* [Need help?](#need-help)
* [IDX Kotlin SDK Documentation](#idx-kotlin-sdk-documentation)
* [Getting started](#installation--running-the-app)
* [Contributing](#contributing)

## Introduction
> :grey_exclamation: This SDK requires usage of the Okta Identity Engine.
This functionality is in [General Availability](https://developer.okta.com/docs/reference/releases-at-okta/#general-availability-ga) but is being gradually rolled out to customers. If you want to gain access to the Okta Identity Engine, please reach out to your account manager. If you
do not have an account manager, please reach out to oie@okta.com for more information.

## Installation

Add the `Okta IDX Kotlin` dependency to your `build.gradle` file:

```gradle
implementation 'com.okta.android:okta-idx-kotlin:1.0.0'
```

See the [CHANGELOG](CHANGELOG.md) for the most recent changes.

## Need help?

If you run into problems using the SDK, you can

* Ask questions on the [Okta Developer Forums][devforum]
* Post [issues][github-issues] here on GitHub (for code errors)

## IDX Kotlin SDK Documentation

### idx-kotlin Overview

The idx-kotlin SDK embraces the dynamic [policies][dev-docs-policies] of Okta Identity Engine with the [interaction code flow][dev-docs-interaction-code-flow].
The SDK attempts to simplify the responses provided by the IDX endpoints, and provide a standard way of interaction with the IDX endpoints.
This is a high level flowchart of how the idx-kotlin SDK methods would be used by a calling application.

```mermaid
graph TD
    A(IdxClient.start) --> B(idxClient.resume)
    B --> C[Gather User Input]
    C --> D(idxClient.proceed)
    D --> E{idxResponse.isLoginSuccessful}
    E --> |yes| F(idxClient.exchangeInteractionCodeForTokens)
    E --> |no| C
    F --> G[Use properties from TokenResponse in your application]
```

Gather User Input Notes:
- Use [IdxResponse](idx-kotlin/src/main/java/com/okta/idx/kotlin/dto/IdxResponse.kt) properties such as `remediations` and `authenticators` to continue satisfying remediations until the user is logged in
- Set `value` property in [IdxRemediation.Form.Field](idx-kotlin/src/main/java/com/okta/idx/kotlin/dto/IdxRemediation.kt)
- Set `selectedOption` property in [IdxRemediation.Form.Field](idx-kotlin/src/main/java/com/okta/idx/kotlin/dto/IdxRemediation.kt)

Notice the cyclical call-and-response pattern. A user is presented with a series of choices in how they can iteratively step through the authentication process, with each step giving way to additional choices until they can either successfully authenticate or receive actionable error messages.

Each step in the authentication process is represented by an `IdxResponse` object, which contains the choices they can take, represented by the `IdxRemediation` class. Remediations provide metadata about its type, a form object tree that describes the fields and values that should be presented to the user, and other related data that helps you, the developer, build a UI capable of prompting the user to take action.

When a remediation is selected and its inputs have been supplied by the user, the `IdxClient.proceed` method can be called on the remediation to proceed to the next step of the authentication process. This returns another `IdxResponse` object, which causes the process to continue.

### IdxClient

The `IdxClient` class is used to define and initiate an authentication workflow utilizing the Okta Identity Engine.

This class makes heavy use of [Kotlin Coroutines][kotlin-coroutines] to perform the actions asynchronously.

#### IdxClient.start
The static start method on `IdxClient` is used to create an IdxClient, and to start an authorization flow.

#### IdxClient.resume
The resume method on an `IdxClient` is used to reveal the current remediations.

This method is usually performed after an `IdxClient` is created, but can also be called at any time to reveal what remediations are available to the user.

#### IdxClient.proceed
Executes the remediation option and proceeds through the workflow using the supplied form parameters.

This method is used to proceed through the authentication flow, using the data assigned to the nested fields' `value` and `selectedOption` to make selections.

#### IdxClient.exchangeInteractionCodeForTokens
This method is used when `IdxResponse.isLoginSuccessful` is true, and there is an `IdxRemediation` having a type of `IdxRemediation.Type.ISSUE` in the `IdxRemediationCollection`.

Pass the `IdxRemediation` with type `IdxRemediation.Type.ISSUE` to exchange the interaction code in the remediation for ID, access, and refresh tokens (based on the scopes provided in the `IdxClientConfiguration`).

#### IdxClient.evaluateRedirectUri
This method evaluates the given redirect url to determine what next steps can be performed.
This is usually used when receiving a redirection from an IDP authentication flow.

## Installation & Running The App

### Prerequisites

#### Okta Admin Dashboard

Before running this sample, you will need the following:

* An Okta Developer Account, you can sign up for one at <https://developer.okta.com/signup/>.
* An Okta Application, configured for Mobile app.
    1. After login, from the Admin dashboard, navigate to **Applications**&rarr;**Add Application**
    2. Choose **Native** as the platform
    3. Populate your new Native OpenID Connect application with values similar to:

        | Setting              | Value                                                                 |
        | -------------------- | --------------------------------------------------------------------- |
        | Application Name     | MyApp *(must be unique)*                                              |
        | Sign-in redirect URIs| com.okta.sample.android:/login                                        |
        | Allowed grant types  | Authorization Code, Interaction Code, Refresh Token *(recommended)*   |

    4. Click **Finish** to redirect back to the *General Settings* of your application.
    5. Copy the **Client ID**, as it will be needed for the client configuration.
    6. Get your issuer (found in the upper right of the console home page, for example, <https://dev-1234.okta.com.>).
    7. Ensure your authorization server has enabled the Interaction Code flow. Navigate to Security -> API -> Authorization Servers -> default -> Access Policies -> Default Policy -> Edit Default Policy Rule -> Interaction Code -> Update Rule

**Note:** *As with any Okta application, make sure you assign Users or Groups to the application. Otherwise, no one can use it.*

#### Android Studio

Open the project in Android Studio

### Configuration

Update the `okta.properties` file in the projects root directory with
the contents created from the [okta admin dashboard](#Okta-Admin-Dashboard):

```
issuer=https://YOUR_ORG.okta.com/oauth2/default
clientId=test-client-id
redirectUri=com.okta.sample.android:/login
```

Notes:
- `issuer` - is your authorization server, usually `https://your_okta_domain.okta.com/oauth2/default`, but custom authorization servers are supported. See `https://your_okta_domain.okta.com/admin/oauth2/as` for available authorization servers.
- `clientId` - is your applications client id, created in your okta admin dashboard
- `redirectUri` - is used for external [identity providers](https://developer.okta.com/docs/reference/api/idps/), and should follow the format of [reverse domain name notation](https://en.wikipedia.org/wiki/Reverse_domain_name_notation) + `/login`, ie: `com.okta.sample.android:/login`

### Running This Sample

You can open this sample in Android Studio or build it using gradle.

```bash
./gradlew :dynamic-app:assembleDebug
```

#### Running tests with mock data

```bash
./gradlew :dynamic-app:connectedCheck
```

#### Running end to end tests

Add a file called `e2eCredentials.yaml` to `app/src/androidTest/resources` directory.
Supply contents to the yaml file:
```yaml
cucumber:
  username: example-change-me
  password: example-change-me
  invalidUsername: example-change-me
  invalidPassword: example-change-me
  firstName: example-change-me
  newPassword: example-change-me
  socialEmail: example-change-me
  socialPassword: example-change-me
  socialName: example-change-me
  socialEmailMfa: example-change-me
  socialPasswordMfa: example-change-me
  socialNameMfa: example-change-me
managementSdk:
  clientId: example-change-me
  orgUrl: example-change-me
  token: example-change-me
a18n:
  token: example-change-me
```

Run the cucumber tests from the command line.
```bash
./gradlew :dynamic-app:connectedCheck -PcucumberUseAndroidJUnitRunner=false
```

## Contributing

We are happy to accept contributions and PRs! Please see the [contribution guide](CONTRIBUTING.md) to understand how to structure a contribution.

[devforum]: https://devforum.okta.com/
[github-issues]: https://github.com/okta/okta-idx-android/issues
[dev-docs-policies]: https://developer.okta.com/docs/concepts/policies/#how-policies-work
[dev-docs-interaction-code-flow]: https://developer.okta.com/docs/concepts/interaction-code/#the-interaction-code-flow
[kotlin-coroutines]: https://kotlinlang.org/docs/coroutines-basics.html
