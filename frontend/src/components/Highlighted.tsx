import { Fragment } from 'react'

// Markers the backend wraps around matched words (TicketSearchService.MARK_START / MARK_END)
const MARK_START = ''
const MARK_END = ''

// Renders "smells of smoke" as: smells of <mark>smoke</mark>
//
// Safe by construction: the text is split into plain strings and React renders strings as
// text, so a ticket containing "<script>" shows up literally instead of running. (The common
// shortcut, inserting the search engine's HTML with dangerouslySetInnerHTML, would let anyone
// who files a ticket inject code into the pages of the agents searching for it.)
export function Highlighted({ text }: { text: string }) {
  const [before, ...marked] = text.split(MARK_START)
  return (
    <>
      {before}
      {marked.map((part, index) => {
        const [match, rest = ''] = part.split(MARK_END)
        return (
          <Fragment key={index}>
            <mark>{match}</mark>
            {rest}
          </Fragment>
        )
      })}
    </>
  )
}

// The text without markers, e.g. to compare a highlight with the original title
export const stripMarks = (text: string) => text.replaceAll(MARK_START, '').replaceAll(MARK_END, '')
