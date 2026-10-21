import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { api } from '../api'
import { ErrorMessage } from '../components/ErrorMessage'
import { label } from '../format'
import { useApi } from '../useApi'
import { PRIORITIES, type ArticleSearchResult, type Priority, type Ticket } from '../types'

// The backend's limit for a knowledge base search query
const MAX_QUERY_LENGTH = 500

export function NewTicketPage() {
  const navigate = useNavigate()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [priority, setPriority] = useState<Priority>('MEDIUM')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  // What the suggested articles were searched for. Set when the title or description loses
  // focus, not on every keystroke: each search costs an embedding call.
  const [articleQuery, setArticleQuery] = useState('')

  function updateArticleQuery() {
    const query = title.trim() ? `${title.trim()}\n\n${description.trim()}`.trim() : ''
    setArticleQuery(query.slice(0, MAX_QUERY_LENGTH))
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      // No reporterId: the backend takes the reporter from the login token.
      const ticket = await api<Ticket>('/tickets', { method: 'POST', body: { title, description, priority } })
      navigate(`/tickets/${ticket.id}`)
    } catch (e) {
      setError(e)
      setBusy(false)
    }
  }

  return (
    <section className="narrow">
      <p>
        <Link to="/">← Back to tickets</Link>
      </p>
      <h1>New ticket</h1>
      <form onSubmit={handleSubmit}>
        <label>
          Title
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            onBlur={updateArticleQuery}
            required
            maxLength={255}
            autoFocus
          />
        </label>
        <label>
          Description
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            onBlur={updateArticleQuery}
            required
            maxLength={4000}
            rows={6}
          />
        </label>
        <label>
          Priority
          <select value={priority} onChange={(e) => setPriority(e.target.value as Priority)}>
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {label(p)}
              </option>
            ))}
          </select>
        </label>
        {articleQuery && <SuggestedArticles query={articleQuery} />}
        <ErrorMessage error={error} />
        <button type="submit" disabled={busy}>
          {busy ? 'Submitting…' : 'Submit ticket'}
        </button>
      </form>
    </section>
  )
}

// Help articles that might answer the question before it becomes a ticket. Shows nothing while
// loading, when nothing is relevant, or when the search fails: it's only a hint, and the form
// must keep working without it.
function SuggestedArticles({ query }: { query: string }) {
  const { data: articles } = useApi<ArticleSearchResult[]>(`/kb/search?q=${encodeURIComponent(query)}`)
  if (!articles || articles.length === 0) {
    return null
  }
  return (
    <div className="card suggested-articles">
      <strong>These articles might answer your question</strong>
      <ul>
        {articles.slice(0, 3).map((article) => (
          <li key={article.id}>
            {/* A new tab, so the half-written ticket isn't lost */}
            <Link to={`/kb/${article.id}`} target="_blank" rel="noopener">
              {article.title}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}
