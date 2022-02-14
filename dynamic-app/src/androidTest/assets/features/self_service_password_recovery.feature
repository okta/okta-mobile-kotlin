Feature: 3.1 Direct Auth Password Recovery
  As a user, Mary should be able to resets her password

  @requireA18NProfile
  @requireExistingUser
  Scenario: 3.1.1 Mary resets her password
    Given Mary navigates to the Basic Login View
    When she clicks on the "Forgot Password Link"
    Then she is redirected to the Self Service Password Reset View
    When she inputs her correct Email
    And she submits the recovery form
    Then she sees the list of authenticators
    When she selects Email authenticator
    Then she sees a page to input her code
    When she fills in the correct code
    And she submits the verify form
    Then she sees a page to set her password
    When she fills a password that fits within the password policy
    And she submits the form
    Then she is redirected to the Root View
    And an application session is created

  Scenario: 3.1.2 Mary tries to reset a password with the wrong email
    Given Mary navigates to the Basic Login View
    When she clicks on the "Forgot Password Link"
    Then she is redirected to the Self Service Password Reset View
    When she inputs an Email that doesn't exist
    And she submits the recovery form
    Then she sees a message "There is no account with the Username mary@unknown.com."

  @skipScenario # Backend regression.
  @requireA18NProfile
  @requireExistingUser
  Scenario: 3.1.3 Mary resets her password with recovery token
    Given Mary bootstraps the application with a recovery token
    Then she sees a page to set her password
    When she fills a password that fits within the password policy
    And she submits the form
    Then she is redirected to the Root View
    And an application session is created
