declare module 'cross-spawn' {
  import { spawn, spawnSync } from 'node:child_process'
  export default spawn
  export const sync: typeof spawnSync
}
