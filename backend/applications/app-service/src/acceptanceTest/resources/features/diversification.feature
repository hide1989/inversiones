Feature: Diversification ceilings

  Background:
    Given the user is authenticated

  Scenario: A portfolio breaching the single-fund ceiling reports the violation with subject and weight
    Given QQQ's current price makes it dominate the portfolio's market value
    When the user requests the diversification report
    Then the response has status 200
    And a diversification violation is reported for subject "QQQ" with its actual weight above the allowed weight
