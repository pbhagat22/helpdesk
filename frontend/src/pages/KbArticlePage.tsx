import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api, ApiError } from '../api'
import { useAuth } from '../auth'
import { ErrorMessage } from '../components/ErrorMessage'
import { formatDate, isStaff } from '../format'
import type { Article } from '../types'
import { useApi } from '../useApi'

export function KbArticlePage() {
  const { id } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const { data: article, error } = useApi<Article>(`/kb/articles/${id}`)
  const [deleteError, setDeleteError] = useState<unknown>(null)

  async function handleDelete() {
    if (!window.confirm('Delete this article?')) return
    try {
      await api<void>(`/kb/articles/${id}`, { method: 'DELETE' })
      navigate('/kb')
    } catch (e) {
      setDeleteError(e)
    }
  }

  if (error instanceof ApiError && error.status === 404) {
    return (
      <section>
        <h1>Article not found</h1>
        <Link to="/kb">Back to the knowledge base</Link>
      </section>
    )
  }
  if (error) return <ErrorMessage error={error} />
  if (!article) return <p>Loading…</p>

  return (
    <section className="narrow">
      <p>
        <Link to="/kb">← Knowledge base</Link>
      </p>
      <div className="page-header">
        <h1>{article.title}</h1>
        {isStaff(user?.role) && (
          <div className="pills">
            <Link to={`/kb/${article.id}/edit`} className="button">
              Edit
            </Link>
            <button type="button" className="secondary" onClick={handleDelete}>
              Delete
            </button>
          </div>
        )}
      </div>
      <p className="hint">
        By {article.author.name} · updated {formatDate(article.updatedAt)}
        {isStaff(user?.role) &&
          (article.chunkCount > 0
            ? ` · searchable (${article.chunkCount} ${article.chunkCount === 1 ? 'chunk' : 'chunks'})`
            : ' · not searchable yet: embedding failed, ask an admin to re-index')}
      </p>
      {/* Plain text, never HTML: the body is whatever staff typed */}
      <div className="card kb-body">{article.body}</div>
      <ErrorMessage error={deleteError} />
    </section>
  )
}
