import { isAxiosError } from 'axios';

export const getApiErrorMessage = (error: unknown, fallback: string) => {
  if (isAxiosError(error)) {
    const data = error.response?.data as { message?: string } | undefined;
    return data?.message || error.message || fallback;
  }
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
};
