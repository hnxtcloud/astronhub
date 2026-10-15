import { useParams } from '@tanstack/react-router'
import { PluginPublishedDetail } from '@/features/plugin/plugin-access'
export function PluginDetailPage() {
  const { namespace, slug } = useParams({ strict: false })
  return <PluginPublishedDetail namespace={namespace ?? ''} slug={slug ?? ''} />
}
