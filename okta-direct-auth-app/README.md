# Okta Direct Authentication App

This application demonstrates how to use the Okta Direct Authentication SDK for Android to build a custom authentication experience.

## Features

*   Sign in with a username and password.
*   Multi-factor authentication (MFA) with Okta Verify, One-Time Passwords (OTP), and more.

## Setup

To build and run this application, you will need to configure your Okta domain and application settings in the `local.properties` file at the root of the `okta-mobile-kotlin` project.

### Prerequisites

Before you begin, you will need to have:

*   An Okta developer account.
*   An application configured for Direct Authentication on your Okta tenant. See https://developer.okta.com/docs/guides/configure-direct-auth-grants/aotp/main/

### Configuration

1.  Create or edit a `local.properties` file in the root of the `okta-mobile-kotlin` project.
2.  Add the following properties to the file:

    ```properties
    issuer=<your_okta_domain>
    clientId=<your_application_client_id>
    authorizationServerId=<your_authorization_server_id>
    ```

    Replace the following values:
    *   `<your_okta_domain>`: Your Okta organization's domain.
    *   `<your_application_client_id>`: The client ID of your Okta application.
    *   `<your_authorization_server_id>`: The ID of your authorization server.

## Build and Run

Once you have configured the `local.properties` file, you can build and run the application on an Android device or emulator.

1.  Open the `okta-mobile-kotlin` project in Android Studio.
2.  Select the `okta-direct-auth-app` run configuration.
3.  Click the "Run" button.
