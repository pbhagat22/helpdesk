import { ApiError } from '../api'

// Shows an API error: the ProblemDetail's message, plus per-field validation errors.
export function ErrorMessage({ error }: { error: unknown }) {
  if (!error) {
    return null
  }
  if (error instanceof ApiError) {
    const fieldErrors = Object.entries(error.problem?.errors ?? {})
    return (
      <div className="error" role="alert">
        {error.message}
        {fieldErrors.length > 0 && (
          <ul>
            {fieldErrors.map(([field, message]) => (
              <li key={field}>
                {field}: {message}
              </li>
            ))}
          </ul>
        )}
      </div>
    )
  }
  return (
    <div className="error" role="alert">
      Something went wrong. Is the backend running?
    </div>
  )
}
