Feature: Combined scoring with sources unavailable

  Background:
    Given the user is authenticated

  Scenario: Combined score redistributes weights when three sources are unavailable for TSLA
    Given technical, news and analyst signals are available for TSLA
    And the insider, social and WSB sources are unavailable
    When the user requests the signals dashboard
    Then the combined score for "TSLA" is computed from exactly the sources "TECHNICAL,NEWS,ANALYST"
    And "TSLA" is not reported as a sell purely because data was missing
