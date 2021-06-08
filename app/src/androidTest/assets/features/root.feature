Feature: 0.1: Root page for Direct Auth Demo Application

  Scenario: 0.1.1: Mary visits the Root View WITHOUT an authenticated session (no tokens)
    Given Mary has an unauthenticated session
    When Mary navigates to the Root View
    Then the Root Page shows links to the Entry Points as defined in https://oktawiki.atlassian.net/l/c/Pw7DVm1t

  Scenario: 0.1.2: Mary visits the Root View And WITH an authenticated session
    Given Mary has an authenticated session
    Then Mary sees a table with the claims from the /userinfo response
    And Mary sees a logout button

  Scenario: 0.1.3: Mary logs out of the app
    Given Mary has an authenticated session
    When Mary clicks the logout button
    Then she is redirected back to the Root View
    And Mary sees login, registration buttons
    And she does not see claims from /userinfo
