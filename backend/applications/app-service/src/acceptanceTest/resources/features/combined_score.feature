Feature: Combined scoring with sources unavailable

  Background:
    Given the user is authenticated

  Scenario: Combined score redistributes weights when three sources are unavailable for TSLA
    Given technical, news and analyst signals are available for TSLA with scores 4.5, 4.6 and 1.27
    And the insider, social and WSB sources are unavailable
    When the user requests the signals dashboard
    Then the combined score for "TSLA" is computed from exactly the sources "TECHNICAL,NEWS,ANALYST"
    And the combined score for "TSLA" equals "3.79", the value proportional redistribution of the three surviving sources' weights produces
