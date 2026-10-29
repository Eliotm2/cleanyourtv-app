#!/usr/bin/env bash
# Fork (Thomas): merge the latest upstream release into the current branch (thomas), or stop.
# Exit 0 with "new=false" when there is nothing new, 0 with "new=true" when the merge is ready to
# build (not committed yet), 2 when a person must look (the reason is in $RUNNER_TEMP/reason.md).
set -euo pipefail
out=${GITHUB_OUTPUT:-/dev/stdout}
reason=${RUNNER_TEMP:-/tmp}/reason.md
upstream_repo=udhaykumarbala/cleanyourtv-app

git remote get-url upstream >/dev/null 2>&1 || git remote add upstream "https://github.com/$upstream_repo"
git fetch --quiet --tags upstream
latest=$(gh api "repos/$upstream_repo/releases/latest" --jq .tag_name)
echo "upstream latest release: $latest"
echo "tag=$latest" >> "$out"
if git merge-base --is-ancestor "$latest" HEAD; then
  echo "already merged"; echo "new=false" >> "$out"; exit 0
fi

launcher_before=$(git rev-parse HEAD:third_party/cleanyourtv)
git merge --no-ff --no-commit "$latest" || true

# The upstream workflows stay out of the fork (they sign and publish with upstream's secrets); ours are thomas-*.
# ls-files lists a conflicted file once per stage, hence sort -u; --ignore-unmatch for a file already gone.
for f in $(git ls-files .github/workflows | sort -u); do
  case "$f" in .github/workflows/thomas-*) ;; *) git rm -q --force --ignore-unmatch "$f" ;; esac
done

if ! version=$(python3 .github/thomas/version.py "$latest" 2>&1); then
  printf 'La version dans app/build.gradle.kts ne se règle pas toute seule :\n\n    %s\n' "$version" > "$reason"
  exit 2
fi
git add app/build.gradle.kts

conflicts=$(git diff --name-only --diff-filter=U)
if [ -n "$conflicts" ]; then
  { echo "La reprise de $latest bute sur des conflits entre l'original et nos ajouts :"; echo
    for f in $conflicts; do echo "- \`$f\`"; done; } > "$reason"
  exit 2
fi

launcher_after=$(git rev-parse :third_party/cleanyourtv)
if [ "$launcher_before" != "$launcher_after" ]; then
  { echo "$latest embarque un autre launcher (\`${launcher_after:0:7}\` au lieu de \`${launcher_before:0:7}\`)."
    echo; echo "Notre launcher (Eliotm2/cleanyourtv) doit d'abord être repris sur cette version, puis recompilé et épinglé."; } > "$reason"
  exit 2
fi

echo "version=${version%% *}" >> "$out"
echo "new=true" >> "$out"
echo "merge of $latest ready: $version"
