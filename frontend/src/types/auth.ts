export interface LoginRequest {
  name: string
  pin: string
}

export interface ChangePinRequest {
  currentPin: string
  newPin: string
}
