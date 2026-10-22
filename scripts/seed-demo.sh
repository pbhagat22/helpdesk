#!/usr/bin/env bash
# Fills a helpdesk with demo data through its public API, the way a user would:
#   - help articles from seed/kb-articles.json (created as the admin, which also embeds them)
#   - a demo customer with the tickets from seed/tickets.json (each one is triaged by the AI)
# Safe to run again: existing articles are skipped, and tickets are only created if the
# customer has none.
#
# Usage: ADMIN_PASSWORD=... scripts/seed-demo.sh [base URL]     (default http://localhost:8080)
# On AWS the admin password is the /helpdesk/admin-password SSM parameter, e.g.
#   ADMIN_PASSWORD=$(aws ssm get-parameter --name /helpdesk/admin-password --with-decryption \
#     --query Parameter.Value --output text) scripts/seed-demo.sh https://d1fpqs2kiuwucp.cloudfront.net
set -euo pipefail

BASE=${1:-http://localhost:8080}
BASE=${BASE%/}
ADMIN_EMAIL=${ADMIN_EMAIL:-admin@example.com}
: "${ADMIN_PASSWORD:?Set ADMIN_PASSWORD to the password of the bootstrap admin}"
CUSTOMER_EMAIL=${CUSTOMER_EMAIL:-demo-customer@example.com}
CUSTOMER_PASSWORD=${CUSTOMER_PASSWORD:-demo-customer-password}
SEED_DIR="$(cd "$(dirname "$0")" && pwd)/seed"

NL=$'\n'

# call METHOD PATH TOKEN [JSON]: sets STATUS (HTTP code) and BODY
# (curl appends the status code on its own last line)
call() {
	local args=(-sS -X "$1" "$BASE$2" -H 'Content-Type: application/json' -w "$NL%{http_code}")
	if [[ -n $3 ]]; then
		args+=(-H "Authorization: Bearer $3")
	fi
	if [[ -n ${4:-} ]]; then
		args+=(-d "$4")
	fi
	local response
	response=$(curl "${args[@]}")
	STATUS=${response##*$NL}
	BODY=${response%$NL*}
}

fail() {
	echo "FAILED: $1 (HTTP $STATUS)" >&2
	echo "$BODY" >&2
	exit 1
}

# login EMAIL PASSWORD: prints the access token
login() {
	call POST /api/auth/login "" "$(jq -n --arg email "$1" --arg password "$2" '{email: $email, password: $password}')"
	[[ $STATUS == 200 ]] || fail "login as $1"
	jq -r .accessToken <<<"$BODY"
}

echo "Seeding $BASE"
call GET /actuator/health ""
[[ $STATUS == 200 ]] || fail "health check (is the app up?)"

admin=$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD")

# Articles: create the missing ones. chunkCount 0 means the embedding call failed (Bedrock
# access?): the article is saved but can't be found by search.
call GET /api/kb/articles "$admin"
[[ $STATUS == 200 ]] || fail "list articles"
existing=$(jq -r '.[].title' <<<"$BODY")
while IFS= read -r article; do
	title=$(jq -r .title <<<"$article")
	if grep -qxF "$title" <<<"$existing"; then
		echo "  article exists:  $title"
		continue
	fi
	call POST /api/kb/articles "$admin" "$article"
	[[ $STATUS == 201 ]] || fail "create article '$title'"
	chunks=$(jq -r .chunkCount <<<"$BODY")
	echo "  article created: $title ($chunks chunk(s))"
	[[ $chunks != 0 ]] || echo "    WARNING: not embedded, so search won't find it" >&2
done < <(jq -c '.[]' "$SEED_DIR/kb-articles.json")

# Customer: register (409 = already registered), then log in
call POST /api/auth/register "" "$(jq -n --arg email "$CUSTOMER_EMAIL" --arg password "$CUSTOMER_PASSWORD" \
	'{email: $email, name: "Demo Customer", password: $password}')"
[[ $STATUS == 201 || $STATUS == 409 ]] || fail "register $CUSTOMER_EMAIL"
customer=$(login "$CUSTOMER_EMAIL" "$CUSTOMER_PASSWORD")

# Tickets: only if the customer has none yet
call GET /api/tickets "$customer"
[[ $STATUS == 200 ]] || fail "list tickets"
if [[ $(jq length <<<"$BODY") -gt 0 ]]; then
	echo "  $CUSTOMER_EMAIL already has tickets, none created"
else
	while IFS= read -r ticket; do
		call POST /api/tickets "$customer" "$ticket"
		[[ $STATUS == 201 ]] || fail "create ticket"
		echo "  ticket created:  #$(jq -r .id <<<"$BODY") $(jq -r .title <<<"$BODY")"
	done < <(jq -c '.[]' "$SEED_DIR/tickets.json")
fi

echo "Done. Customer login: $CUSTOMER_EMAIL / $CUSTOMER_PASSWORD"
