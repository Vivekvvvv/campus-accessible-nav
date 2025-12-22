#!/usr/bin/env bash
set -euo pipefail

today="$(date -u +%F)"
fail=0

write_summary() {
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    cat >>"$GITHUB_STEP_SUMMARY"
  else
    cat
  fi
}

trim() {
  local s="$1"
  s="$(echo "$s" | sed -e 's/^[[:space:]]\+//' -e 's/[[:space:]]\+$//')"
  printf '%s' "$s"
}

date_to_epoch() {
  local d="$1"
  date -u -d "$d" +%s 2>/dev/null || return 1
}

check_trivyignore() {
  local f=".trivyignore"
  [[ -f "$f" ]] || return 0

  local total=0 active=0 soon=0 invalid=0

  while IFS= read -r line || [[ -n "$line" ]]; do
    line="$(trim "$line")"
    [[ -z "$line" ]] && continue
    [[ "$line" == \#* ]] && continue
    total=$((total + 1))

    local id meta
    id="$(trim "${line%%#*}")"
    meta=""
    if [[ "$line" == *"#"* ]]; then
      meta="$(trim "${line#*#}")"
    fi
    if [[ -z "$id" || -z "$meta" ]]; then
      invalid=$((invalid + 1)); fail=1; continue
    fi

    [[ "$meta" =~ reason:[[:space:]]*[^;]+ ]] || { invalid=$((invalid + 1)); fail=1; continue; }
    [[ "$meta" =~ owner:[[:space:]]*[^;]+ ]] || { invalid=$((invalid + 1)); fail=1; continue; }
    [[ "$meta" =~ severity:[[:space:]]*(HIGH|CRITICAL) ]] || { invalid=$((invalid + 1)); fail=1; continue; }
    [[ "$meta" =~ cvss:[[:space:]]*([0-9]+(\.[0-9]+)?) ]] || { invalid=$((invalid + 1)); fail=1; continue; }
    local cvss="${BASH_REMATCH[1]}"
    awk -v s="$cvss" 'BEGIN { exit (s >= 0 && s <= 10) ? 0 : 1 }' || { invalid=$((invalid + 1)); fail=1; continue; }
    [[ "$meta" =~ exploitability:[[:space:]]*(LOW|MEDIUM|HIGH) ]] || { invalid=$((invalid + 1)); fail=1; continue; }
    [[ "$meta" =~ ticket:[[:space:]]*[^;]+ ]] || { invalid=$((invalid + 1)); fail=1; continue; }
    [[ "$meta" =~ expires:[[:space:]]*([0-9]{4}-[0-9]{2}-[0-9]{2}) ]] || { invalid=$((invalid + 1)); fail=1; continue; }

    local exp="${BASH_REMATCH[1]}"
    local exp_epoch today_epoch
    exp_epoch="$(date_to_epoch "$exp" || echo "")"
    today_epoch="$(date_to_epoch "$today" || echo "")"
    if [[ -z "$exp_epoch" || -z "$today_epoch" ]]; then
      invalid=$((invalid + 1)); fail=1; continue
    fi
    if [[ "$exp" < "$today" ]]; then
      invalid=$((invalid + 1)); fail=1; continue
    fi

    local days_left=$(( (exp_epoch - today_epoch) / 86400 ))
    if (( days_left > 90 )); then
      invalid=$((invalid + 1)); fail=1; continue
    fi

    active=$((active + 1))
    if (( days_left <= 14 )); then
      soon=$((soon + 1))
    fi
  done <"$f"

  write_summary <<EOF
## Security Allowlist Policy

### Trivy ignore list (\`.trivyignore\`)
- Today (UTC): \`$today\`
- Entries: $total (active: $active, expiring <= 14d: $soon)
- Invalid/expired entries: $invalid

EOF

  if (( invalid > 0 )); then
    write_summary <<'EOF'
Trivy ignore entries must follow:
- `<VULN_ID> # reason: ...; expires: YYYY-MM-DD; owner: ...; severity: HIGH|CRITICAL; cvss: 0-10; exploitability: LOW|MEDIUM|HIGH; ticket: ...`
- max allowlist lifetime: 90 days

EOF
  fi
}

check_osv_config() {
  local f="osv-scanner.toml"
  [[ -f "$f" ]] || return 0

  local blocks=0 active=0 soon=0 invalid=0
  local in_block=0
  local id="" until="" reason="" severity="" cvss="" exploitability="" ticket=""

  flush_block() {
    if (( in_block == 0 )); then
      return
    fi
    blocks=$((blocks + 1))

    if [[ -z "$id" || -z "$until" || -z "$reason" || -z "$severity" || -z "$cvss" || -z "$exploitability" || -z "$ticket" ]]; then
      invalid=$((invalid + 1))
      fail=1
    else
      local until_epoch today_epoch
      until_epoch="$(date_to_epoch "$until" || echo "")"
      today_epoch="$(date_to_epoch "$today" || echo "")"
      if [[ -z "$until_epoch" || -z "$today_epoch" ]]; then
        invalid=$((invalid + 1)); fail=1
      elif [[ "$until" < "$today" ]]; then
        invalid=$((invalid + 1)); fail=1
      else
        local days_left=$(( (until_epoch - today_epoch) / 86400 ))
        if (( days_left > 90 )); then
          invalid=$((invalid + 1)); fail=1
        else
          active=$((active + 1))
          if (( days_left <= 14 )); then
            soon=$((soon + 1))
          fi
        fi
      fi
      awk -v s="$cvss" 'BEGIN { exit (s >= 0 && s <= 10) ? 0 : 1 }' || { invalid=$((invalid + 1)); fail=1; }
    fi

    in_block=0
    id=""; until=""; reason=""; severity=""; cvss=""; exploitability=""; ticket=""
  }

  while IFS= read -r line || [[ -n "$line" ]]; do
    local s
    s="$(trim "$line")"
    [[ -z "$s" ]] && continue
    [[ "$s" == \#* ]] && continue

    if [[ "$s" == "[[IgnoredVulns]]" ]]; then
      flush_block
      in_block=1
      continue
    fi

    (( in_block == 0 )) && continue

    [[ "$s" =~ ^id[[:space:]]*=[[:space:]]*\"([^\"]+)\" ]] && { id="${BASH_REMATCH[1]}"; continue; }
    [[ "$s" =~ ^ignoreUntil[[:space:]]*=[[:space:]]*\"([0-9]{4}-[0-9]{2}-[0-9]{2})\" ]] && { until="${BASH_REMATCH[1]}"; continue; }
    [[ "$s" =~ ^reason[[:space:]]*=[[:space:]]*\"([^\"]+)\" ]] && { reason="${BASH_REMATCH[1]}"; continue; }
    [[ "$s" =~ ^severity[[:space:]]*=[[:space:]]*\"(HIGH|CRITICAL)\" ]] && { severity="${BASH_REMATCH[1]}"; continue; }
    [[ "$s" =~ ^cvss[[:space:]]*=[[:space:]]*\"([0-9]+(\.[0-9]+)?)\" ]] && { cvss="${BASH_REMATCH[1]}"; continue; }
    [[ "$s" =~ ^exploitability[[:space:]]*=[[:space:]]*\"(LOW|MEDIUM|HIGH)\" ]] && { exploitability="${BASH_REMATCH[1]}"; continue; }
    [[ "$s" =~ ^ticket[[:space:]]*=[[:space:]]*\"([^\"]+)\" ]] && { ticket="${BASH_REMATCH[1]}"; continue; }
  done <"$f"

  flush_block

  write_summary <<EOF
### OSV ignore list (\`osv-scanner.toml\`)
- Blocks: $blocks (active: $active, expiring <= 14d: $soon)
- Invalid/expired blocks: $invalid

EOF
}

check_trivyignore
check_osv_config

if (( fail != 0 )); then
  write_summary <<'EOF'
**Policy check failed.** Fix the ignore entries (or remove them) before merging.
EOF
  exit 1
fi
