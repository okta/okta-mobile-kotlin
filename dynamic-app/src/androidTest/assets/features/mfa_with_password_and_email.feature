Feature: 6.1 Multi-Factor Authentication with Password And Email

  @requireA18NProfile
  @requireExistingUser
  @requireMFAGroupsForUser
  Scenario: 6.1.2 2FA Login with Email
    Given Mary navigates to the Basic Login View
    When she fills in her correct username for mfa
    And she fills in her correct password for mfa
    And she submits the Login form
    Then she is presented with an option to select Email to verify
    When she selects Email
    Then she sees a page to input a code
    When she fills in the correct code
    And she submits the verify form
    Then she is redirected to the Root View
    And an application session is created

  @requireA18NProfile
  @requireExistingUser
  @requireMFAGroupsForUser
  Scenario: 6.1.3 Mary enters a wrong verification code
    Given Mary navigates to the Basic Login View
    When she fills in her correct username for mfa
    And she fills in her correct password for mfa
    And she submits the Login form
    Then she is presented with an option to select Email to verify
    When she selects Email
    Then she sees a page to input a code
    And she inputs the incorrect code from the email
    And she submits the verify form
    Then the sample shows an error message "Invalid code. Try again." on the Sample App
