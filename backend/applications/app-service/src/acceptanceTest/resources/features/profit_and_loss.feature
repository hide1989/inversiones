Feature: Portfolio profit and loss with the exchange effect reported separately

  The critical defect this project already paid for hid the currency conversion effect inside the
  asset gain. This is the end-to-end proof that it stays fixed: the seam between aggregation,
  persistence and serialization, which no unit test can exercise.

  Background:
    Given the user is authenticated

  Scenario: A mixed currency portfolio is valued without the currency effect being hidden
    Given the seeded portfolio holds QQQ at Hapi in dollars and TERPEL at Trii in pesos
    And the current exchange rate is 3912.40 COP per USD
    When the user requests the total return report
    Then the response has status 200
    And the asset gain and the exchange rate effect are reported as separate amounts
    And the portfolio total is expressed in a single declared base currency
    And every monetary amount in the response is encoded as a JSON string
