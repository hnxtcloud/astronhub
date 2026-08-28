import { cn } from '@/shared/lib/utils'

export const APP_HEADER_BASE_CLASS_NAME =
  'sticky top-0 z-50 border-b bg-white/95 transition-shadow duration-200'

export const APP_HEADER_ELEVATED_CLASS_NAME = 'shadow-[0_2px_6px_rgba(15,23,42,0.06)]'

export function getAppHeaderClassName(isElevated: boolean): string {
  return cn(APP_HEADER_BASE_CLASS_NAME, isElevated && APP_HEADER_ELEVATED_CLASS_NAME)
}
