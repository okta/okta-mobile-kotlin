Feature: 5.2 Direct Auth Social Login with MFA

  @logOutOfSocialIdP
  Scenario: 5.2.1 Mary logs in with a social IDP and gets MFA screen
    Given Mary navigates to the Basic Login View
    When she clicks the "Login with Okta OIDC IdP" button
    And logs in to Okta OIDC with MFA User
    Then Mary should see a page to select an authenticator for MFA
