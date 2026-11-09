import { buttonVariants } from '@/shared/ui/button'

/** A consistent detail-page entry to the actual install, download or configuration controls. */
export function ContentUseAction({ targetId, label }: { targetId: string; label: string }) {
  return <a href={`#${targetId}`} className={buttonVariants({ variant: 'default' })}>{label}</a>
}
