import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./client";
import type {
  DashboardStats,
  Repository,
  ReviewDetail,
  ReviewSummary,
} from "./types";

export function useDashboardStats() {
  return useQuery({
    queryKey: ["dashboard"],
    queryFn: async () => (await api.get<DashboardStats>("/dashboard/stats")).data,
    refetchInterval: 5000,
  });
}

export function useReviews() {
  return useQuery({
    queryKey: ["reviews"],
    queryFn: async () => (await api.get<ReviewSummary[]>("/reviews")).data,
    refetchInterval: 5000,
  });
}

export function useReview(id: number) {
  return useQuery({
    queryKey: ["review", id],
    queryFn: async () => (await api.get<ReviewDetail>(`/reviews/${id}`)).data,
    // Poll while the review is still being processed.
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === "PENDING" || status === "IN_PROGRESS" ? 2000 : false;
    },
  });
}

export function useRepositories() {
  return useQuery({
    queryKey: ["repositories"],
    queryFn: async () => (await api.get<Repository[]>("/repositories")).data,
  });
}

export interface SubmitReviewPayload {
  repositoryId?: number | null;
  title: string;
  author?: string;
  diff: string;
}

export function useSubmitReview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (payload: SubmitReviewPayload) =>
      (await api.post<ReviewDetail>("/reviews", payload)).data,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["reviews"] });
      qc.invalidateQueries({ queryKey: ["dashboard"] });
    },
  });
}

export interface CreateRepoPayload {
  fullName: string;
  minScoreThreshold?: number;
}

export function useCreateRepository() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (payload: CreateRepoPayload) =>
      (await api.post<Repository>("/repositories", payload)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["repositories"] }),
  });
}

export function useDeleteRepository() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => api.delete(`/repositories/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["repositories"] }),
  });
}
