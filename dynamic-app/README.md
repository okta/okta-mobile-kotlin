# Installation & Running The App

## Prerequisites

### Okta Admin Dashboard

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

### Android Studio

Open the project in Android Studio

## Configuration

Update the `local.properties` file in the projects root directory with
the contents created from the [okta admin dashboard](#Okta-Admin-Dashboard):

```
issuer=https://YOUR_ORG.okta.com/oauth2/default
clientId=test-client-id
signInRedirectUri=com.okta.sample.android:/login
```

Notes:
- `issuer` - is your authorization server, usually `https://your_okta_domain.okta.com/oauth2/default`, but custom authorization servers are supported. See `https://your_okta_domain.okta.com/admin/oauth2/as` for available authorization servers.
- `clientId` - is your applications client id, created in your okta admin dashboard
- `signInRedirectUri` - is used for external [identity providers](https://developer.okta.com/docs/reference/api/idps/), and should follow the format of [reverse domain name notation](https://en.wikipedia.org/wiki/Reverse_domain_name_notation) + `:/login`, ie: `com.okta.sample.android:/login`

## Running This Sample

You can open this sample in Android Studio or build it using gradle.

```bash
./gradlew :dynamic-app:assembleDebug
```

### Running tests with mock data

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
