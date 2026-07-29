import { mapApiError } from '../model/apiError';

export const backendUrl = String(import.meta.env.VITE_BACKEND_URL ?? '').trim();
const DEFAULT_TIMEOUT_MS = Number(import.meta.env.VITE_API_TIMEOUT_MS ?? 15000);

export async function apiRequest<T>(path: string, clientId: string, options: RequestInit = {}): Promise<T> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);
  try {
    const response = await fetch(`${backendUrl}${path}`, {
      ...options,
      signal: controller.signal,
      headers: {
        'Content-Type': 'application/json',
        'X-Client-Id': clientId,
        ...(options.headers ?? {})
      }
    });

    if (!response.ok) {
      let body: unknown = {};
      try {
        body = await response.json();
      } catch {
        body = { message: response.statusText };
      }
      throw mapApiError({ ...(typeof body === 'object' && body !== null ? body : {}), status: response.status });
    }

    return (await response.json()) as T;
  } catch (error) {
    if (typeof error === 'object' && error !== null && 'fieldErrors' in error) throw error;
    throw mapApiError(error);
  } finally {
    window.clearTimeout(timeout);
  }
}
