export function displayOptionalDetailValue(value: string | number | null | undefined): string {
  if (typeof value === 'string') return value.trim()
  return value === null || value === undefined ? '' : String(value)
}
