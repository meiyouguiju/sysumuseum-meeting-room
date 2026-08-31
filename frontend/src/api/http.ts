import axios, { AxiosError } from 'axios'

import { ApiError, type ApiErrorResponse } from '@/types/api'

export const http = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  headers: { Accept: 'application/json' },
})

function isLoginRequest(url: string | undefined): boolean {
  return url === '/auth/login'
}

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorResponse>) => {
    const response = error.response
    const body = response?.data

    if (response?.status === 401 && !isLoginRequest(error.config?.url)) {
      window.dispatchEvent(new CustomEvent('auth:unauthenticated'))
    }

    return Promise.reject(
      new ApiError({
        message:
          body?.message ??
          (response ? '请求失败，请稍后重试' : '无法连接服务器，请检查网络或服务状态'),
        errorCode: body?.errorCode,
        fieldErrors: body?.fieldErrors,
        requestId: body?.requestId,
        status: response?.status,
      }),
    )
  },
)
