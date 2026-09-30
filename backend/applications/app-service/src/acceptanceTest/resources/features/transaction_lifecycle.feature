Feature: Transaction lifecycle

  Registering, listing, editing and deleting a trade through the real API, each change visible
  through the endpoints a client would actually call.

  Background:
    Given the user is authenticated

  Scenario: Registering a buy is reflected in the listing and in the portfolio
    When the user registers a BUY of "5" shares of "QQQ" at "700.00" USD with broker "Hapi" traded on "2026-09-01"
    Then the response has status 201
    And the transaction response field "ticker" equals "QQQ"
    When the user lists transactions filtered by ticker "QQQ"
    Then the response has status 200
    And the transaction list contains the last registered transaction id
    When the user requests the portfolio
    Then the response has status 200
    And the portfolio contains a position for ticker "QQQ"

  Scenario: Editing a registered transaction changes its recorded quantity
    Given the user has registered a BUY of "2" shares of "GLD" at "100.00" USD with broker "Hapi" traded on "2026-09-01"
    When the user edits that transaction to quantity "3" at unit price "110.00" USD
    Then the response has status 200
    And the transaction response field "quantity" equals "3"

  Scenario: Deleting a registered transaction removes it from the listing
    Given the user has registered a BUY of "1" shares of "SMH" at "50.00" USD with broker "Hapi" traded on "2026-09-01"
    When the user deletes that transaction
    Then the response has status 204
    When the user lists transactions filtered by ticker "SMH"
    Then the transaction list does not contain the last registered transaction id
