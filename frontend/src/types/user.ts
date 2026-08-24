export type UserRoleCode = 'USER' | 'ADMIN'
export type UserStatus = 'ACTIVE' | 'DISABLED'

export interface CurrentUser {
  id: number
  displayName: string
  departmentName: string
  roleCode: UserRoleCode
  status: UserStatus
}
