export interface ApiFieldError {
  field: string
  message: string
}

export interface ApiErrorResponse {
  errorCode: string
  message: string
  fieldErrors: ApiFieldError[]
  requestId: string
}

export class ApiError extends Error {
  readonly errorCode?: string
  readonly fieldErrors: ApiFieldError[]
  readonly requestId?: string
  readonly status?: number

  constructor(options: {
    message: string
    errorCode?: string
    fieldErrors?: ApiFieldError[]
    requestId?: string
    status?: number
  }) {
    super(options.message)
    this.name = 'ApiError'
    this.errorCode = options.errorCode
    this.fieldErrors = options.fieldErrors ?? []
    this.requestId = options.requestId
    this.status = options.status
  }
}
