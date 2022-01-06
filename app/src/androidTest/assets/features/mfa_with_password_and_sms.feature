Feature: 6.2 Multi-Factor Authentication with Password and SMS

  @requireA18NProfile
  @requireExistingUser
  @requireMFAGroupsForUser
  Scenario: 6.2.1 Enroll in SMS Factor prompt when authenticating
    Given Mary navigates to the Basic Login View
    When she fills in her correct username for mfa
    And she fills in her correct password for mfa
    And she submits the Login form
    Then she is presented with a list of factors
    When she selects Phone from the list
    When she selects SMS from the list
    And she inputs a valid phone number
    And submits the enrollment form
    Then the screen changes to receive an input for a code
    When she inputs the correct code from the SMS
    And she submits the verify form
    Then she is redirected to the Root View
    And an application session is created

  @requireA18NProfile
  @requireExistingUser
  @requireEnrolledPhone
  @requireMFAGroupsForUser
  Scenario: 6.2.2 2FA Login with SMS
    Given Mary navigates to the Basic Login View
    When she fills in her correct username for mfa
    And she fills in her correct password for mfa
    And she submits the Login form
    Then she is presented with a list of factors
    When she selects SMS from the list
    Then the screen changes to receive an input for a code
    When she inputs the correct code from the SMS
    And she submits the verify form
    Then she is redirected to the Root View
    And an application session is created

  @requireA18NProfile
  @requireExistingUser
  @requireMFAGroupsForUser
  Scenario: 6.2.3 Enroll with Invalid Phone Number
    Given Mary navigates to the Basic Login View
    When she fills in her correct username for mfa
    And she fills in her correct password for mfa
    And she submits the Login form
    Then she is presented with a list of factors
    When she selects Phone from the list
    When she selects SMS from the list
    And she inputs an invalid phone number
    And submits the enrollment form
    Then she should see a message "Unable to initiate factor enrollment: Invalid Phone Number"

  @requireA18NProfile
  @requireExistingUser
  @requireMFAGroupsForUser
  Scenario: 6.2.4 Mary enters a wrong verification code on verify
    Given Mary navigates to the Basic Login View
    When she fills in her correct username for mfa
    And she fills in her correct password for mfa
    And she submits the Login form
    Then she is presented with a list of factors
    When she selects Phone from the list
    When she selects SMS from the list
    And she inputs a valid phone number
    And submits the enrollment form
    Then the screen changes to receive an input for a code
    When she inputs the incorrect code from the phone
    And she submits the verify form
    Then the sample shows an error message "Invalid code. Try again." on the Sample App
    And she sees a field to re-enter another code
