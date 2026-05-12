#!/bin/bash
# Generate FastMask icon variants via Gemini 2.5 Flash Image (Nano Banana).
# Reads prompts.json, posts each variant to the API in parallel, saves PNGs.

set -euo pipefail
cd "$(dirname "$0")"

API_KEY=$(cat ~/.config/gemini/api-key)
MODEL="gemini-2.5-flash-image"
ENDPOINT="https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${API_KEY}"

generate_one() {
  local id="$1"
  local prompt="$2"
  local full="${prompt} $(jq -r .common_style prompts.json)"
  local out="${id}.png"
  local body
  body=$(python3 -c "import json,sys; print(json.dumps({'contents':[{'role':'user','parts':[{'text':sys.argv[1]}]}],'generationConfig':{'responseModalities':['IMAGE']}}))" "$full")
  local resp
  resp=$(curl -sS -X POST "$ENDPOINT" -H "Content-Type: application/json" -d "$body")
  echo "$resp" | python3 -c "
import json, sys, base64
d = json.loads(sys.stdin.read())
if 'error' in d:
    print('ERROR', json.dumps(d['error']), file=sys.stderr)
    sys.exit(1)
data = d['candidates'][0]['content']['parts'][0]['inlineData']['data']
open(sys.argv[1],'wb').write(base64.b64decode(data))
print(f'{sys.argv[1]}: {len(base64.b64decode(data))} bytes')
" "$out"
}

# Variants to (re)generate. Default: all 6. Override with: ./generate.sh v3 v5
variants=("$@")
if [ ${#variants[@]} -eq 0 ]; then
  variants=(v1 v2 v3 v4 v5 v6)
fi

for v in "${variants[@]}"; do
  prompt=$(jq -r --arg id "$v" '.variants[] | select(.id==$id) | .prompt' prompts.json)
  if [ -z "$prompt" ] || [ "$prompt" = "null" ]; then
    echo "no prompt for $v" >&2
    continue
  fi
  (generate_one "$v" "$prompt") &
done
wait
echo "done."
