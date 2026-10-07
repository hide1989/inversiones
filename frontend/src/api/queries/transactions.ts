import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../client'
import {
  TransactionListResponseSchema,
  TransactionResponseSchema,
  type TransactionRequest,
} from '../schemas/transactions'
import { z } from 'zod'

export const transactionsKeys = {
  all: ['transactions'] as const,
}

export function useTransactions() {
  return useQuery({
    queryKey: transactionsKeys.all,
    queryFn: () => apiFetch('/transactions', TransactionListResponseSchema),
  })
}

export function useCreateTransaction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: TransactionRequest) =>
      apiFetch('/transactions', TransactionResponseSchema, { method: 'POST', body }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: transactionsKeys.all })
    },
  })
}

export function useUpdateTransaction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: TransactionRequest }) =>
      apiFetch(`/transactions/${id}`, TransactionResponseSchema, { method: 'PUT', body }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: transactionsKeys.all })
    },
  })
}

export function useDeleteTransaction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiFetch(`/transactions/${id}`, z.void(), { method: 'DELETE' }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: transactionsKeys.all })
    },
  })
}
