import axios, { AxiosError } from 'axios'

import { ApiError, type ApiErrorResponse } from '@/types/api'

export const http = axios.create({
  baseURL: '/api/v1',
  headers: { Accept: 'application/json' },
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorResponse>) => {
    const response = error.response
    const body = response?.data

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

// 未来 SSO 接入时，可在这里集中扩展 401/403 行为；F0 不做登录跳转。
