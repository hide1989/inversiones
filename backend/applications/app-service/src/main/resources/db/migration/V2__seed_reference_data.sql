-- Reference data: the three brokers, the twenty instruments the current portfolio
-- covers, and the single local user.
--
-- Source of truth: portafolio-dashboard/index.html (PORT_USD, TRII_USD, PORT_BVC),
-- which is the file that actually renders the dashboard today. AGENT_SPEC.md documents
-- an older, smaller PORT_USD (14 rows, pre-2026-06-30) that had already drifted from
-- index.html's 23-row array by the time this migration was written; index.html wins.
--
-- Sector and exchange values matter beyond description: EvaluateDiversificationUseCase
-- enforces a 30% per-sector ceiling, so sectors must be genuine GICS-style buckets, not
-- one bucket per ticker. CalculateTotalReturnUseCase classifies a position as the
-- Colombian segment only when broker = 'Trii' AND instrument.exchange = 'BVC' exactly,
-- so TERPEL and MINEROS carry exchange 'BVC'/currency 'COP', and EC — an ADR held at
-- Trii but listed on the NYSE — carries exchange 'NYSE' so it is not misclassified.

INSERT INTO brokers (name) VALUES
    ('Hapi'),
    ('ARQ'),
    ('Trii');

INSERT INTO instruments (ticker, name, type, sector, exchange, currency) VALUES
    ('QQQ',     'Invesco QQQ Trust',                              'ETF',    'Broad Market',           'NASDAQ', 'USD'),
    ('VOO',     'Vanguard S&P 500 ETF',                           'ETF',    'Broad Market',           'NYSE',   'USD'),
    ('SMH',     'VanEck Semiconductor ETF',                       'ETF',    'Technology',             'NASDAQ', 'USD'),
    ('GLD',     'SPDR Gold Shares',                               'ETF',    'Materials',              'NYSE',   'USD'),
    ('IBIT',    'iShares Bitcoin Trust',                          'ETF',    'Broad Market',           'NASDAQ', 'USD'),
    ('TSLA',    'Tesla Inc.',                                     'EQUITY', 'Consumer Discretionary', 'NASDAQ', 'USD'),
    ('GOOGL',   'Alphabet Inc. Class A',                          'EQUITY', 'Communication Services', 'NASDAQ', 'USD'),
    ('TTWO',    'Take-Two Interactive Software Inc.',             'EQUITY', 'Communication Services', 'NASDAQ', 'USD'),
    ('NU',      'Nu Holdings Ltd.',                                'EQUITY', 'Financials',             'NYSE',   'USD'),
    ('TSM',     'Taiwan Semiconductor Manufacturing Co. ADR',     'EQUITY', 'Technology',             'NYSE',   'USD'),
    ('AMZN',    'Amazon.com Inc.',                                'EQUITY', 'Consumer Discretionary', 'NASDAQ', 'USD'),
    ('MSFT',    'Microsoft Corporation',                          'EQUITY', 'Technology',             'NASDAQ', 'USD'),
    ('NLR',     'VanEck Uranium and Nuclear Energy ETF',          'EQUITY', 'Energy',                 'NYSE',   'USD'),
    ('SPCX',    'SPCX Holdings',                                  'EQUITY', 'Industrials',            'NYSE',   'USD'),
    ('PLTR',    'Palantir Technologies Inc.',                     'EQUITY', 'Technology',             'NYSE',   'USD'),
    ('UNH',     'UnitedHealth Group Inc.',                        'EQUITY', 'Health Care',            'NYSE',   'USD'),
    ('JPM',     'JPMorgan Chase & Co.',                           'EQUITY', 'Financials',             'NYSE',   'USD'),
    ('EC',      'Ecopetrol S.A. ADR',                             'ADR',    'Energy',                 'NYSE',   'USD'),
    ('TERPEL',  'Organizacion Terpel S.A.',                       'EQUITY', 'Energy',                 'BVC',    'COP'),
    ('MINEROS', 'Mineros S.A.',                                   'EQUITY', 'Materials',              'BVC',    'COP');

-- Local development user. Plaintext password (never stored, and nowhere else in this
-- repository): "Inversiones2026!". The hash below is a real BCrypt digest produced by
-- Spring Security's BCryptPasswordEncoder (strength 10), generated from a throwaway
-- JUnit test that was deleted immediately after and never committed:
--
--   BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
--   encoder.encode("Inversiones2026!");
--   // => $2a$10$8h4HIXTmlqYgxHHTXt9vi.Tg1neXgrGmgZcv06pcahzg2CztxVUZ6
--
-- encoder.matches("Inversiones2026!", hash) returned true before this hash was pasted in.
INSERT INTO users (username, password_hash) VALUES
    ('david', '$2a$10$8h4HIXTmlqYgxHHTXt9vi.Tg1neXgrGmgZcv06pcahzg2CztxVUZ6');
