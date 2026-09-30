-- Consolidated opening positions: these BUY rows represent the accumulated quantity and
-- cost of each holding as of 2026-06-30 (the date of the last diversified purchase per
-- AGENT_SPEC.md), not a replay of every individual historical trade. The unit price is
-- derived as cost / quantity at scale 10 so CalculateTotalReturnUseCase's weighted-average
-- recomputation lands back on the same cost figures the dashboard has been showing.
--
-- Source: portafolio-dashboard/index.html PORT_USD (Hapi + ARQ, NYSE/NASDAQ, cost in USD),
-- TRII_USD (NYSE ADRs held at Trii, cost in USD) and PORT_BVC (BVC Colombia, cost in COP).

INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 0.486372, 616.8118230490, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'QQQ' AND b.name = 'Hapi';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 0.240145, 624.6226238314, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'VOO' AND b.name = 'Hapi';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 0.536535, 492.0461852442, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'SMH' AND b.name = 'Hapi';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 0.239739, 417.1202849766, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'GLD' AND b.name = 'Hapi';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 0.113097, 442.0983757306, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'TSLA' AND b.name = 'Hapi';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 0.378529, 343.4347170230, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'GOOGL' AND b.name = 'Hapi';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 1.031311, 242.4099035112, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'TTWO' AND b.name = 'Hapi';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 2.702703, 36.9999959300, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'IBIT' AND b.name = 'Hapi';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 4.519425, 15.4886960177, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'NU' AND b.name = 'Hapi';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 0.587269213, 388.2376173532, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'TSM' AND b.name = 'ARQ';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 1.677612941, 251.1246722673, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'AMZN' AND b.name = 'ARQ';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 0.294578, 441.3092627420, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'MSFT' AND b.name = 'ARQ';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 0.431131115, 115.9740001600, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'NLR' AND b.name = 'ARQ';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 0.971606564, 185.2601728615, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'SPCX' AND b.name = 'ARQ';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 0.137206207, 728.8300011092, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'QQQ' AND b.name = 'ARQ';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 0.073035349, 684.6000010214, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'VOO' AND b.name = 'ARQ';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 0.118460955, 422.0800009590, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'TSLA' AND b.name = 'ARQ';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 0.251200178, 358.2800009003, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'GOOGL' AND b.name = 'ARQ';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 6.015037593, 13.3000000022, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'NU' AND b.name = 'ARQ';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 0.166500166, 120.1200003608, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'PLTR' AND b.name = 'ARQ';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 0.119522864, 418.3300025341, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'UNH' AND b.name = 'ARQ';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 0.152313644, 328.2700005523, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'JPM' AND b.name = 'ARQ';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 50.0, 11.8132000000, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'EC' AND b.name = 'Trii';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 3.0, 16.6200000000, 0, 'USD', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'NU' AND b.name = 'Trii';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 25.0, 16649.3124000000, 0, 'COP', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'TERPEL' AND b.name = 'Trii';
INSERT INTO transactions (instrument_id, broker_id, type, quantity, unit_price, fees, currency, traded_at)
SELECT i.id, b.id, 'BUY', 25.0, 16961.5840000000, 0, 'COP', DATE '2026-06-30'
FROM instruments i, brokers b WHERE i.ticker = 'MINEROS' AND b.name = 'Trii';

-- Reference TRM the total-return calculation compares the current rate against.
INSERT INTO exchange_rates (base_currency, quote_currency, rate, rate_date)
VALUES ('USD', 'COP', 3568.42, DATE '2026-06-30');
