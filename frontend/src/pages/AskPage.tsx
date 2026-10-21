import { Fragment, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { api } from '../api'
import { ErrorMessage } from '../components/ErrorMessage'
import type { AgentResponse } from '../types'

const EXAMPLES = [
  'Which urgent or high priority tickets are unassigned?',
  'What has Tess reported, and what did the AI triage suggest?',
  'Is anyone having problems with invoices?',
]

// Staff ask questions in plain language; the backend's agent answers them by calling
// read-only tools (list, search, read tickets, find users) as the logged-in user.
export function AskPage() {
  const [question, setQuestion] = useState('')
  const [asked, setAsked] = useState('')
  const [result, setResult] = useState<AgentResponse | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function ask(text: string) {
    setBusy(true)
    setError(null)
    setResult(null)
    setAsked(text)
    try {
      setResult(await api<AgentResponse>('/agent/ask', { method: 'POST', body: { question: text } }))
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    ask(question.trim())
  }

  return (
    <section className="narrow ask">
      <h1>Ask about the tickets</h1>
      <form onSubmit={handleSubmit}>
        <label>
          Question
          <textarea
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            required
            maxLength={500}
            rows={2}
            autoFocus
          />
        </label>
        <div className="ask-actions">
          <button type="submit" disabled={busy || !question.trim()}>
            {busy ? 'Thinking…' : 'Ask'}
          </button>
          <span className="hint">The AI can look up tickets and users, but can't change anything.</span>
        </div>
      </form>
      <p className="hint">
        Try:{' '}
        {EXAMPLES.map((example, index) => (
          <Fragment key={example}>
            {index > 0 && ' · '}
            <button
              type="button"
              className="link"
              disabled={busy}
              onClick={() => {
                setQuestion(example)
                ask(example)
              }}
            >
              {example}
            </button>
          </Fragment>
        ))}
      </p>

      {busy && <p className="hint">Looking things up… this can take up to half a minute.</p>}
      <ErrorMessage error={error} />
      {result && (
        <div className="card ask-answer">
          <p className="hint">{asked}</p>
          <p className="answer">
            <WithTicketLinks text={result.answer} />
          </p>
          <details>
            <summary className="hint">
              How it got there: {result.steps.length} tool {result.steps.length === 1 ? 'call' : 'calls'},{' '}
              {result.modelCalls} model {result.modelCalls === 1 ? 'call' : 'calls'},{' '}
              {(result.inputTokens + result.outputTokens).toLocaleString()} tokens
            </summary>
            <ol className="steps">
              {result.steps.map((step, index) => (
                <li key={index}>
                  <code>
                    {step.tool}({step.input})
                  </code>
                  {step.error && <span className="error-text"> failed: {step.error}</span>}
                </li>
              ))}
            </ol>
          </details>
          <p className="hint">AI-generated. Open the tickets to check before acting on it.</p>
        </div>
      )}
    </section>
  )
}

// Turns "#12" in the answer into links to the ticket. The text is split into plain strings
// and <Link> elements, never inserted as HTML: the answer can quote what customers wrote.
function WithTicketLinks({ text }: { text: string }) {
  const parts = text.split(/(#\d+)/)
  return (
    <>
      {parts.map((part, index) =>
        /^#\d+$/.test(part) ? (
          <Link key={index} to={`/tickets/${part.slice(1)}`}>
            {part}
          </Link>
        ) : (
          <Fragment key={index}>{part}</Fragment>
        ),
      )}
    </>
  )
}
