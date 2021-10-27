[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Okta IDX Android - embedded-auth-with-sdk

This repository contains a sample Android application which can be used a reference for using 
[Okta IDX Java][okta-idx-java-github] on Android. 

* [Introduction](#introduction)
* [Need help?](#need-help)
* [Getting started](#installation--running-the-app)
* [Contributing](#contributing)

## Introduction
> :grey_exclamation: This Sample Application uses an SDK that requires usage of the Okta Identity Engine. 
This functionality is in [General Availability](https://developer.okta.com/docs/reference/releases-at-okta/#general-availability-ga) but is being gradually rolled out to customers. If you want to gain access to the Okta Identity Engine, please reach out to your account manager. If you 
do not have an account manager, please reach out to oie@okta.com for more information.

This Sample Application will show you the best practices for integrating Authentication into your app
using [Okta's Identity Engine](https://developer.okta.com/docs/concepts/ie-intro/). Specifically, this 
application will cover some basic needed use cases to get you up and running quickly with Okta.
These Examples are:
1. Sign In
2. Sign Out
3. Sign Up
4. Sign In/Sign Up with Social Identity Providers
5. Sign In with Multifactor Authentication using Email or Phone
6. Password reset using Email

## Need help?

If you run into problems using the SDK, you can

* Ask questions on the [Okta Developer Forums][devforum]
* Post [issues][github-issues] here on GitHub (for code errors)

## Installation & Running The App

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
        | Sign-in redirect URIs| com.okta.sample.android:/login                          |
        | Allowed grant types  | Authorization Code, Interaction Code, Refresh Token *(recommended)*   |

    4. Click **Finish** to redirect back to the *General Settings* of your application.
    5. Copy the **Client ID**, as it will be needed for the client configuration.
    6. Get your issuer, which is a combination of your Org URL (found in the upper right of the console home page) . For example, <https://dev-1234.okta.com.>
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

### Dependencies

This sample uses [Okta IDX Java Library][okta-idx-java-github] dependency in `build.gradle` file:

```bash
implementation "com.okta.idx.sdk:okta-idx-java-api:${okta.sdk.version}"
```

See the latest release [here][okta-idx-java-releases].

### Running This Sample

You can open this sample in Android Studio or build it using gradle.

```bash
./gradlew :app:assembleDebug
```

#### Running tests with mock data

```bash
./gradlew :app:connectedCheck
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
./gradlew :app:connectedCheck -PcucumberUseAndroidJUnitRunner=false
```

## Contributing

We are happy to accept contributions and PRs! Please see the [contribution guide](CONTRIBUTING.md) to understand how to structure a contribution.

[devforum]: https://devforum.okta.com/
[github-issues]: https://github.com/okta/okta-idx-android/issues
[okta-idx-java-releases]: https://github.com/okta/okta-idx-java/releases
[okta-idx-java-github]: https://github.com/okta/okta-idx-java
