#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <github-actions-run-url | github-pr-url>"
  echo ""
  echo "Examples:"
  echo "  $0 https://github.com/restatedev/e2e/actions/runs/23837567301"
  echo "  $0 https://github.com/restatedev/e2e/pull/406"
  exit 1
fi

URL="$1"

# Project root is one level up from .tools/
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
DATE="$(date +%Y%m%d)"

# Ensure .gitignore exists so reports never get committed
mkdir -p "$PROJECT_ROOT/.downloaded-test-reports"
echo "*" > "$PROJECT_ROOT/.downloaded-test-reports/.gitignore"

# Download all -report artifacts for a given run
download_run_artifacts() {
  local full_repo="$1"
  local run_id="$2"
  local output_dir="$3"

  echo "Fetching artifacts for run $run_id..."
  local artifacts
  artifacts=$(gh api "repos/${full_repo}/actions/runs/${run_id}/artifacts" \
    --paginate \
    --jq '.artifacts[] | select(.name | endswith("-report")) | "\(.id) \(.name)"')

  if [[ -z "$artifacts" ]]; then
    echo "  No -report artifacts in run $run_id"
    return
  fi

  local count
  count=$(echo "$artifacts" | wc -l | tr -d ' ')
  echo "  Found $count report artifact(s) in run $run_id"

  while IFS=' ' read -r artifact_id artifact_name; do
    local zip_path="$output_dir/${artifact_name}.zip"
    echo "    Downloading: $artifact_name"
    gh api "repos/${full_repo}/actions/artifacts/${artifact_id}/zip" > "$zip_path"
    unzip -o -q "$zip_path" -d "$output_dir/${artifact_name}/"
    rm "$zip_path"
    echo "    Extracted: $artifact_name"
  done <<< "$artifacts"
}

# --- Actions run URL ---
if [[ "$URL" =~ ^https://github\.com/([^/]+/[^/]+)/actions/runs/([0-9]+) ]]; then
  FULL_REPO="${BASH_REMATCH[1]}"
  RUN_ID="${BASH_REMATCH[2]}"
  REPO_NAME="${FULL_REPO#*/}"

  echo "Repo: $FULL_REPO | Run: $RUN_ID"

  OUTPUT_DIR="$PROJECT_ROOT/.downloaded-test-reports/${REPO_NAME}-${DATE}-${RUN_ID}"
  mkdir -p "$OUTPUT_DIR"

  download_run_artifacts "$FULL_REPO" "$RUN_ID" "$OUTPUT_DIR"

  echo ""
  echo "Done! Reports saved to: .downloaded-test-reports/${REPO_NAME}-${DATE}-${RUN_ID}/"

# --- PR URL ---
elif [[ "$URL" =~ ^https://github\.com/([^/]+/[^/]+)/pull/([0-9]+) ]]; then
  FULL_REPO="${BASH_REMATCH[1]}"
  PR_NUMBER="${BASH_REMATCH[2]}"
  REPO_NAME="${FULL_REPO#*/}"

  echo "Repo: $FULL_REPO | PR: #$PR_NUMBER"

  # Get the SHA of the last commit on the PR
  HEAD_SHA=$(gh api "repos/${FULL_REPO}/pulls/${PR_NUMBER}" --jq '.head.sha')
  echo "Last commit: $HEAD_SHA"

  # Get all workflow runs triggered by that commit
  RUN_IDS=$(gh api "repos/${FULL_REPO}/actions/runs?head_sha=${HEAD_SHA}" \
    --paginate \
    --jq '.workflow_runs[].id')

  if [[ -z "$RUN_IDS" ]]; then
    echo "No workflow runs found for commit $HEAD_SHA"
    exit 0
  fi

  RUN_COUNT=$(echo "$RUN_IDS" | wc -l | tr -d ' ')
  echo "Found $RUN_COUNT workflow run(s) for this commit"

  OUTPUT_DIR="$PROJECT_ROOT/.downloaded-test-reports/${REPO_NAME}-${DATE}-pr${PR_NUMBER}"
  mkdir -p "$OUTPUT_DIR"

  while read -r run_id; do
    download_run_artifacts "$FULL_REPO" "$run_id" "$OUTPUT_DIR"
  done <<< "$RUN_IDS"

  echo ""
  echo "Done! Reports saved to: .downloaded-test-reports/${REPO_NAME}-${DATE}-pr${PR_NUMBER}/"

else
  echo "Error: URL doesn't match expected format."
  echo "  Actions run: https://github.com/{owner}/{repo}/actions/runs/{id}"
  echo "  Pull request: https://github.com/{owner}/{repo}/pull/{number}"
  exit 1
fi
