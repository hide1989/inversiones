import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { AiSynthesisResponseSchema } from '../schemas/aiSynthesis'

export const aiSynthesisKeys = {
  all: ['ai-synthesis'] as const,
}

export function useAiSynthesis() {
  return useQuery({
    queryKey: aiSynthesisKeys.all,
    queryFn: () => apiFetch('/ai-synthesis', AiSynthesisResponseSchema),
  })
}
