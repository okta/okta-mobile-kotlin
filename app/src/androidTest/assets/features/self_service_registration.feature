Feature: 4.1 Self Service Registration with Email Activation And optional SMS

  @requireA18NProfile
  @requireUserDeletionAfterRegistration
  Scenario: 4.1.1 Mary signs up for an account with Password, setups up required Email factor, then skips optional SMS
    Given Mary navigates to the Self Service Registration View
    When she fills out her First Name
    And she fills out her Last Name
    And she fills out her Email
    And she submits the registration form
    Then she sees a list of required factors to setup
    When she selects Password
    Then she sees a page to setup password
    When she fills out her Password
    And she confirms her Password
    And she submits the verify form
    Then she sees the list of optional factors
    When she selects Email
    Then she sees a page to input a code
    When she inputs the correct code from her email
    And she submits the verify form
    When she selects "Skip" on SMS
    Then she is redirected to the Root View
    And an application session is created

  @requireA18NProfile
  @requireUserDeletionAfterRegistration
  Scenario: 4.1.2 Mary signs up for an account with Password, setups up required Email factor, And sets up optional SMS
    Given Mary navigates to the Self Service Registration View
    When she fills out her First Name
    And she fills out her Last Name
    And she fills out her Email
    And she submits the registration form
    Then she sees a list of required factors to setup
    When she selects Password
    Then she sees a page to setup password
    When she fills out her Password
    And she confirms her Password
    And she submits the verify form
    Then she sees the list of optional factors
    When she selects Email
    Then she sees a page to input a code
    When she inputs the correct code from her email
    And she submits the verify form
    Then she sees the list of authenticators
    When she selects Phone from the list
    And she inputs a valid phone number
    And submits the enrollment form
    Then the screen changes to receive an input for a code
    When she inputs the correct code from the SMS
    And she submits the verify form
    Then she is redirected to the Root View
    And an application session is created

  Scenario: 4.1.3 Mary signs up with an invalid Email
    Given Mary navigates to the Self Service Registration View
    When she fills out her First Name
    And she fills out her Last Name
    And she fills out her Email with an invalid email format
    And she submits the registration form
    Then she sees an error message "'Email' must be in the form of an email address, Provided value for property 'Email' does not match required pattern"

  @requireA18NProfile
  @requireUserDeletionAfterRegistration
  Scenario: 4.1.4 Mary signs up for an account with Password, sets up required Email factor, And sets up optional SMS with an invalid phone number
    Given Mary navigates to the Self Service Registration View
    When she fills out her First Name
    And she fills out her Last Name
    And she fills out her Email
    And she submits the registration form
    Then she sees a list of required factors to setup
    When she selects Password
    Then she sees a page to setup password
    When she fills out her Password
    And she confirms her Password
    And she submits the verify form
    Then she sees the list of optional factors
    When she selects Email
    Then she sees a page to input a code
    When she inputs the correct code from her email
    And she submits the verify form
    Then she sees the list of authenticators
    When she selects Phone from the list
    And she inputs an invalid phone number
    And submits the enrollment form
    Then she should see a message "Unable to initiate factor enrollment: Invalid Phone Number"
