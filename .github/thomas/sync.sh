#!/usr/bin/env bash
# Fork (Thomas): bring `thomas` up to date with the two originals, or stop.
#  1. the latest release of the original app (udhaykumarbala/cleanyourtv-app), merged with our additions;
#  2. the latest launcher release of the fork (Eliotm2/cleanyourtv, launcher-<version>, itself made each
#     night from the original launcher by that repo's thomas-launcher.yml), bundled and pinned.
# The app is then numbered after the launcher it bundles: <launcher upstream version>-thomas.N (version.py).
# Exit 0 with "new=false" when there is nothing new, 0 with "new=true" when the result is ready to build
# (not committed yet), 2 when a person must look (the reason is in $RUNNER_TEMP/reason.md).
# Needs gh (GH_TOKEN) and, for a new launcher, $ANDROID_HOME's build-tools (apksigner, aapt2).
set -euo pipefail
out=${GITHUB_OUTPUT:-/dev/stdout}
tmp=${RUNNER_TEMP:-/tmp}
reason=$tmp/reason.md
upstream_repo=udhaykumarbala/cleanyourtv-app
launcher_repo=Eliotm2/cleanyourtv
signer=fa73a3a033c6bb075706307ff446928530b90305a198b4349b02c4ce1eae29d5
pins=core/trust/src/main/kotlin/tv/cleanyour/core/trust/Pins.kt
app_new=false
launcher_new=false
notes=""

# ---- 1. the original app --------------------------------------------------------------------------
git remote get-url upstream >/dev/null 2>&1 || git remote add upstream "https://github.com/$upstream_repo"
git fetch --quiet --tags upstream
latest=$(gh api "repos/$upstream_repo/releases/latest" --jq .tag_name)
echo "upstream app latest release: $latest"
echo "tag=$latest" >> "$out"
if git merge-base --is-ancestor "$latest" HEAD; then
  echo "app: already merged"
else
  app_new=true
  notes="appli de l'original $latest"
  git merge --no-ff --no-commit "$latest" || true
  # The upstream workflows stay out of the fork; ours are thomas-*. ls-files lists a conflicted file once
  # per stage, hence sort -u; --ignore-unmatch for a file already gone.
  for f in $(git ls-files .github/workflows | sort -u); do
    case "$f" in .github/workflows/thomas-*) ;; *) git rm -q --force --ignore-unmatch "$f" ;; esac
  done
  # The bundled launcher and its pin are ours (step 2 sets them); upstream's pin of its own launcher goes.
  for f in "$pins" core/trust/src/test/kotlin/tv/cleanyour/core/trust/LauncherPinTest.kt \
           android/platform/src/main/assets/bundled/cleanyourtv-launcher.apk; do
    if git diff --name-only --diff-filter=U | grep -qx "$f"; then git checkout -q --ours -- "$f"; git add "$f"; fi
  done
fi

# ---- 2. the fork's launcher -----------------------------------------------------------------------
pinned=$(sed -n '/object PinnedLauncher/,/^}/s/.*const val VERSION_NAME = "\([^"]*\)".*/\1/p' "$pins")
release=$(gh api "repos/$launcher_repo/releases/latest" --jq .tag_name 2>/dev/null || true)
echo "launcher pinned: $pinned, fork's latest launcher release: ${release:-none}"
if [ -n "$release" ] && [ "${release#launcher-}" != "$pinned" ]; then
  rm -rf "$tmp/launcher" && mkdir -p "$tmp/launcher"
  gh release download "$release" -R "$launcher_repo" -p '*.apk' -D "$tmp/launcher"
  # No "| head" under pipefail: a cut pipe would end the script (SIGPIPE, 141).
  apk=$(find "$tmp/launcher" -name '*.apk' | sort | sed -n 1p)
  commit=$(gh release view "$release" -R "$launcher_repo" --json targetCommitish --jq .targetCommitish)
  bt="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | sed -n '$p')"
  certs=$("$bt/apksigner" verify --print-certs "$apk")
  got=$(sed -n 's/.*certificate SHA-256 digest: //p' <<<"$certs" | sed -n 1p)
  if [ "$got" != "$signer" ]; then
    echo "Le launcher $release n'est pas signé avec la clé du fork ($got)." > "$reason"; exit 2
  fi
  badging=$("$bt/aapt2" dump badging "$apk")
  badging=${badging%%$'\n'*}
  pkg=$(sed -n "s/.*package: name='\([^']*\)'.*/\1/p" <<<"$badging")
  lcode=$(sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" <<<"$badging")
  lname=$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<<"$badging")
  if [ "$pkg" != com.gathin.tvhome ] || [ "launcher-$lname" != "$release" ]; then
    echo "Le launcher $release ne correspond pas à son APK ($pkg $lname)." > "$reason"; exit 2
  fi
  python3 .github/thomas/pin-launcher.py "$apk" "$lcode" "$lname" "$commit"
  git add "$pins" core/trust/src/test/kotlin/tv/cleanyour/core/trust/LauncherPinTest.kt \
          android/platform/src/main/assets/bundled/cleanyourtv-launcher.apk
  launcher_new=true
  pinned=$lname
  notes="${notes:+$notes, }launcher $lname"
fi

if [ "$app_new" = false ] && [ "$launcher_new" = false ]; then
  echo "nothing new"; echo "new=false" >> "$out"; exit 0
fi

# ---- 3. the version, after the launcher it bundles -------------------------------------------------
if ! version=$(python3 .github/thomas/version.py "${pinned%%-thomas*}" 2>&1); then
  printf 'La version dans app/build.gradle.kts ne se règle pas toute seule :\n\n    %s\n' "$version" > "$reason"
  exit 2
fi
git add app/build.gradle.kts

conflicts=$(git diff --name-only --diff-filter=U | sort -u)
if [ -n "$conflicts" ]; then
  { echo "La reprise de $latest bute sur des conflits entre l'original et nos ajouts :"; echo
    for f in $conflicts; do echo "- \`$f\`"; done; } > "$reason"
  exit 2
fi

echo "version=${version%% *}" >> "$out"
echo "notes=$notes" >> "$out"
echo "new=true" >> "$out"
echo "ready: $version ($notes)"
