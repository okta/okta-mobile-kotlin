Feature: 11.1 Account Unlock with Single factor (Email, Phone, Okta Verify Push)
  As a user, Mary should be able to unlock her account and access her profile

  @skipScenario
  @requireA18NProfile
  @requireExistingUser
  @lockUser
  @logOutOfSocialIdP
  Scenario: 11.1.2 Mary recovers from a locked account with Email Magic Link from a different Browser
    Given Mary navigates to the Basic Login View
    When she sees a link to unlock her account
    And she clicks the link to unlock her account
    Then she sees a page to input her user name
    When she inputs her email
    When she selects Email for unlocking
    And she submits the form
    Then the screen changes to receive an input for a code
    When she clicks the magic link from the email in her inbox
    Then she should see a page that says Did you just try to sign in?
    When she clicks "Yes, it's me"
    And switches back to the app
    Then she should see a terminal page that says "Your account is now unlocked!"

  @requireA18NProfile
  @requireExistingUser
  @lockUser
  Scenario: 11.1.3 Mary recovers from a locked account with Email OTP
    Given Mary navigates to the Basic Login View
    When she sees a link to unlock her account
    And she clicks the link to unlock her account
    Then she sees a page to input her user name
    When she inputs her email
    When she selects Email for unlocking
    And she submits the form
    Then the screen changes to receive an input for a code
    When she fills in the correct code
    And she submits the form
    Then she should see a terminal page that says "Your account is now unlocked!"

  @requireA18NProfile
  @requireExistingUser
  @requireEnrolledPhone
  @lockUser
  Scenario: 11.1.4 Mary recovers from a locked account with Phone SMS OTP
    Given Mary navigates to the Basic Login View
    When she sees a link to unlock her account
    And she clicks the link to unlock her account
    Then she sees a page to input her user name
    When she inputs her email
    When she selects Phone from the list
    And she selects SMS
    And she submits the form
    Then the screen changes to receive an input for a code
    When she inputs the correct code from the SMS
    And she submits the form
    Then she should see a terminal page that says "Your account is now unlocked!"

  @requireA18NProfile
  @requireExistingUser
  @requireEnrolledPhone
  @requirePhoneGroupForUser
  @lockUser
  Scenario: 11.2.1 Mary recovers from a locked account First with Email And Then Phone
    Given Mary navigates to the Basic Login View
    When she sees a link to unlock her account
    And she clicks the link to unlock her account
    Then she sees a page to input her user name
    When she inputs her email
    And she selects Email for unlocking
    And she submits the form
    Then the screen changes to receive an input for a code
    When she fills in the correct code
    And she submits the form
    Then she is presented with a list of factors
    And she selects SMS
    And she submits the form
    Then the screen changes to receive an input for a code
    When she inputs the correct code from the SMS
    And she submits the form
    Then she should see a terminal page that says "Your account is now unlocked!"

  @requireA18NProfile
  @requireExistingUser
  @requireSecurityQuestionGroupForUser
  @enrollSecurityQuestion
  @lockUser
  Scenario: 11.2.2 Mary recovers from a locked account First with Email And Then Security Question
    Given Mary navigates to the Basic Login View
    When she sees a link to unlock her account
    And she clicks the link to unlock her account
    Then she sees a page to input her user name
    When she inputs her email
    And she selects Email for unlocking
    And she submits the form
    Then the screen changes to receive an input for a code
    When she fills in the correct code
    And she submits the form
    Then she should see an input box for answering the security question
    When she enters the answer for the Security Question
    And she submits the form
    Then she should see a terminal page that says "Your account is now unlocked!"
