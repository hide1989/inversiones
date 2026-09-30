Feature: Authentication

  The API issues a bearer token for the seeded local user and rejects every request to a
  protected endpoint that does not carry a valid one.

  Scenario: Login succeeds with the seeded credentials and returns a usable token
    When the user logs in with username "david" and password "Inversiones2026!"
    Then the response has status 200
    And the response contains a non-blank bearer token
    When the user requests the portfolio using the issued token
    Then the response has status 200

  Scenario: A protected endpoint rejects a request without a token
    When the user requests the portfolio without a token
    Then the response has status 401

  Scenario: Login is rejected with a wrong password
    When the user logs in with username "david" and password "the-wrong-password"
    Then the response has status 401
