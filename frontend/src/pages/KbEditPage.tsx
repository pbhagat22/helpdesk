import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api } from '../api'
import { ErrorMessage } from '../components/ErrorMessage'
import type { Article } from '../types'
import { useApi } from '../useApi'

// /kb/new creates an article, /kb/:id/edit changes one. Staff only.
export function KbEditPage() {
  const { id } = useParams()
  // key: start the form fresh when switching from one article to another
  return id ? <EditArticle key={id} id={id} /> : <ArticleForm />
}

function EditArticle({ id }: { id: string }) {
  const { data: article, error } = useApi<Article>(`/kb/articles/${id}`)
  if (error) return <ErrorMessage error={error} />
  if (!article) return <p>Loading…</p>
  return <ArticleForm article={article} />
}

function ArticleForm({ article }: { article?: Article }) {
  const navigate = useNavigate()
  const [title, setTitle] = useState(article?.title ?? '')
  const [body, setBody] = useState(article?.body ?? '')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const saved = article
        ? await api<Article>(`/kb/articles/${article.id}`, { method: 'PUT', body: { title, body } })
        : await api<Article>('/kb/articles', { method: 'POST', body: { title, body } })
      navigate(`/kb/${saved.id}`)
    } catch (e) {
      setError(e)
      setBusy(false)
    }
  }

  return (
    <section className="narrow">
      <p>
        <Link to={article ? `/kb/${article.id}` : '/kb'}>← Back</Link>
      </p>
      <h1>{article ? 'Edit article' : 'New article'}</h1>
      <form onSubmit={handleSubmit}>
        <label>
          Title
          <input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={255} autoFocus />
        </label>
        <label>
          Text
          <textarea value={body} onChange={(e) => setBody(e.target.value)} required maxLength={20000} rows={14} />
        </label>
        <p className="hint">Plain text. Leave a blank line between paragraphs: the search works paragraph by paragraph.</p>
        <ErrorMessage error={error} />
        <div>
          <button type="submit" disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
        </div>
      </form>
    </section>
  )
}
