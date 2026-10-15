import { useCallback, useEffect, useState } from 'react'
import { api } from './api'

// Loads GET /api<path> when a component appears (and again whenever path changes).
// reload() fetches again, e.g. after saving a change.
export function useApi<T>(path: string) {
  const [data, setData] = useState<T | undefined>(undefined)
  const [error, setError] = useState<unknown>(null)
  const [version, setVersion] = useState(0)

  useEffect(() => {
    // If path changes (or the component disappears) before the response arrives,
    // ignore the stale response instead of showing the wrong data.
    let ignore = false
    setError(null)
    api<T>(path)
      .then((result) => {
        if (!ignore) setData(result)
      })
      .catch((e) => {
        if (!ignore) setError(e)
      })
    return () => {
      ignore = true
    }
  }, [path, version])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  return { data, error, reload }
}
