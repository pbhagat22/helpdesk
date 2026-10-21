import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useAuth } from '../auth'
import { ErrorMessage } from '../components/ErrorMessage'
import { formatDate, isStaff } from '../format'
import type { Article, ArticleSearchResult } from '../types'
import { useApi } from '../useApi'

// The knowledge base: everyone can read and search it, staff write it. The search finds
// articles by meaning ("I can't sign in" finds "Resetting your password"), not by keywords.
// The query lives in the URL (?q=), like the ticket filters.
export function KbListPage() {
  const { user } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q') ?? ''
  const [text, setText] = useState(query)

  // Search on submit, not while typing: every search costs a call to the embedding model.
  function handleSearch(event: FormEvent) {
    event.preventDefault()
    setSearchParams(text.trim() ? { q: text.trim() } : {})
  }

  return (
    <section>
      <div className="page-header">
        <h1>Knowledge base</h1>
        {isStaff(user?.role) && (
          <Link to="/kb/new" className="button">
            New article
          </Link>
        )}
      </div>
      <form className="kb-search" onSubmit={handleSearch}>
        <input
          type="search"
          placeholder="Describe your problem, e.g. “I was charged twice”"
          aria-label="Search the knowledge base"
          value={text}
          onChange={(e) => setText(e.target.value)}
        />
        <button type="submit">Search</button>
      </form>
      {query ? (
        <KbSearchResults query={query} staff={isStaff(user?.role)} />
      ) : (
        <KbArticleList staff={isStaff(user?.role)} />
      )}
    </section>
  )
}

function KbSearchResults({ query, staff }: { query: string; staff: boolean }) {
  const { data: results, error } = useApi<ArticleSearchResult[]>(`/kb/search?q=${encodeURIComponent(query)}`)
  return (
    <>
      <ErrorMessage error={error} />
      {results === undefined && !error && <p>Searching…</p>}
      {results?.length === 0 && <p className="hint">No articles match “{query}”.</p>}
      <ul className="search-results">
        {results?.map((result) => (
          <li key={result.id} className="card">
            <div className="result-head">
              <Link to={`/kb/${result.id}`}>{result.title}</Link>
              {/* Cosine similarity isn't a percentage (a strong match can be 0.35), so it's only
                  shown to staff, who can use it to judge the search */}
              {staff && (
                <span className="hint" title="Cosine similarity of the best passage to the question">
                  similarity {result.similarity.toFixed(2)}
                </span>
              )}
            </div>
            <p className="snippet kb-snippet">{result.snippet}</p>
          </li>
        ))}
      </ul>
      <p>
        <Link to="/kb">← All articles</Link>
      </p>
    </>
  )
}

function KbArticleList({ staff }: { staff: boolean }) {
  const { data: articles, error } = useApi<Article[]>('/kb/articles')
  return (
    <>
      <ErrorMessage error={error} />
      {articles === undefined && !error && <p>Loading…</p>}
      {articles?.length === 0 && <p className="hint">No articles yet.</p>}
      <ul className="kb-list">
        {articles?.map((article) => (
          <li key={article.id}>
            <Link to={`/kb/${article.id}`}>{article.title}</Link>
            <span className="hint"> · updated {formatDate(article.updatedAt)}</span>
            {staff && article.chunkCount === 0 && <span className="error-text"> · not searchable yet</span>}
          </li>
        ))}
      </ul>
    </>
  )
}
