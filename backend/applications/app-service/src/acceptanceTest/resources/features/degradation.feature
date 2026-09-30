Feature: Graceful degradation when n8n is down

  GetSignalsDashboardUseCase reads a persisted, once-a-day snapshot rather than calling n8n live
  (see AGENT_SPEC.md / spec.md 4.5), and RefreshSignalSnapshotUseCase deliberately leaves a
  previous successful snapshot in place rather than erasing it over a transient failure — so an
  outage is only honestly observable, request by request, on the endpoints that call n8n live on
  every call: portfolio pricing, the earnings calendar and the watchlist.

  Background:
    Given the user is authenticated

  Scenario: Every n8n endpoint failing still yields 200s, not 500s, and reports absent data honestly
    Given every n8n endpoint is down
    When the user requests the portfolio
    Then the response has status 200
    And every position is reported without a live market price rather than the request failing
    When the user requests the earnings calendar
    Then the response has status 200
    And the earnings calendar is empty rather than the request failing
    When the user requests the watchlist
    Then the response has status 200
    And the watchlist is empty rather than the request failing
