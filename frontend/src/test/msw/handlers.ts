import { http, HttpResponse } from 'msw'
import {
  emptySignalHistoryResponse,
  validDiversificationResponse,
  validLoginResponse,
  validPortfolioResponse,
  validSignalsNoDataResponse,
  validTotalReturnResponse,
} from './fixtures'

const API_BASE = 'http://localhost:8080/api/v1'

export const handlers = [
  http.get(`${API_BASE}/portfolio`, () => HttpResponse.json(validPortfolioResponse)),
  http.get(`${API_BASE}/portfolio/returns`, () => HttpResponse.json(validTotalReturnResponse)),
  http.post(`${API_BASE}/auth/login`, () => HttpResponse.json(validLoginResponse)),
  http.get(`${API_BASE}/signals`, () => HttpResponse.json(validSignalsNoDataResponse)),
  http.get(`${API_BASE}/signals/history`, () => HttpResponse.json(emptySignalHistoryResponse)),
  http.get(`${API_BASE}/diversification`, () => HttpResponse.json(validDiversificationResponse)),
]
