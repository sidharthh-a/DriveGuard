#!/usr/bin/env bash
#
# Privacy gate: the app promises that it cannot reach the network, which is
# enforced by never requesting the network permissions. ML Kit pulls
# INTERNET/ACCESS_NETWORK_STATE in transitively (transport-backend-cct), so a
# dependency bump could silently re-add them to the shipped artifact. This
# script inspects the merged release manifest and the shipped release APK(s)
# and fails the build if either permission comes back.
#
# Run after :app:assembleRelease, with ANDROID_HOME (or ANDROID_SDK_ROOT) set.
#
#   ./scripts/check-release-permissions.sh [path/to/app-release.apk ...]

set -euo pipefail

cd "$(dirname "$0")/.."

FORBIDDEN=(
    "android.permission.INTERNET"
    "android.permission.ACCESS_NETWORK_STATE"
)

fail() {
    printf '\n::error::%s\n' "$*" >&2
    exit 1
}

note() {
    printf '%s\n' "$*"
}

# ---------------------------------------------------------------------------
# Locate tooling
# ---------------------------------------------------------------------------
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK_ROOT" || ! -d "$SDK_ROOT" ]]; then
    fail "ANDROID_HOME/ANDROID_SDK_ROOT is not set to an existing Android SDK directory."
fi

AAPT2=""
if [[ -n "${AAPT2_BIN:-}" ]]; then
    AAPT2="$AAPT2_BIN"
elif [[ -d "$SDK_ROOT/build-tools" ]]; then
    # `ls`, not `find -printf`: BSD/macOS find has no -printf, and this script
    # is meant to be runnable outside CI too.
    newest_build_tools="$(
        ls -1 "$SDK_ROOT/build-tools" 2>/dev/null | sort -V | tail -n 1 || true
    )"
    if [[ -n "$newest_build_tools" && -x "$SDK_ROOT/build-tools/$newest_build_tools/aapt2" ]]; then
        AAPT2="$SDK_ROOT/build-tools/$newest_build_tools/aapt2"
    fi
fi
if [[ -z "$AAPT2" || ! -x "$AAPT2" ]]; then
    fail "aapt2 not found under \$ANDROID_HOME/build-tools; install Android build-tools."
fi

# ---------------------------------------------------------------------------
# Locate artifacts
# ---------------------------------------------------------------------------
APKS=("$@")
if [[ "${#APKS[@]}" -eq 0 ]]; then
    # Every APK in the release output directory: a future split-by-ABI or
    # split-by-density configuration must not slip a network permission past
    # the gate by producing more than one file.
    while IFS= read -r apk; do
        [[ -n "$apk" ]] && APKS+=("$apk")
    done < <(
        find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' 2>/dev/null \
            | sort || true
    )
fi
[[ "${#APKS[@]}" -gt 0 ]] || fail "Release APK not found; run :app:assembleRelease first."
for apk in "${APKS[@]}"; do
    [[ -f "$apk" ]] || fail "Release APK not found: $apk"
done

# AGP has emitted both merged_manifests/ and merged_manifest/ over time; either
# release output is authoritative for what packaging will merge.
MERGED_MANIFEST="$(
    find app/build/intermediates -type f -name AndroidManifest.xml \
        -path '*merged_manifest*' -path '*release*' 2>/dev/null \
        | grep -v '/debug/' | sort | tail -n 1 || true
)"

note "Checking artifacts:"
for apk in "${APKS[@]}"; do
    note "  APK:             $apk"
done
note "  merged manifest: ${MERGED_MANIFEST:-<not found, APK check only>}"
note "  aapt2:           $AAPT2"
note ""

# ---------------------------------------------------------------------------
# Checks
# ---------------------------------------------------------------------------
status=0

if [[ -n "$MERGED_MANIFEST" ]]; then
    for permission in "${FORBIDDEN[@]}"; do
        if grep -Fq "android:name=\"$permission\"" "$MERGED_MANIFEST"; then
            printf '::error file=%s::Merged release manifest declares %s. Find the dependency that injects it (./gradlew :app:dependencies) and strip it with tools:node="remove".\n' \
                "$MERGED_MANIFEST" "$permission" >&2
            status=1
        fi
    done
    if [[ "$status" -eq 0 ]]; then
        note "OK  merged release manifest declares no network permission."
    fi
fi

# aapt2 dump permissions prints one `uses-permission: name='...'` line per
# permission in the packaged manifest (including legacy
# `uses-permission-sdk-23:` forms), i.e. exactly what ships to users.
shipped_all=()
for apk in "${APKS[@]}"; do
    if ! permissions_raw="$("$AAPT2" dump permissions "$apk" 2>&1)"; then
        fail "aapt2 could not read $apk:
$permissions_raw"
    fi

    shipped="$(
        printf '%s\n' "$permissions_raw" \
            | sed -n "s/^uses-permission[a-zA-Z0-9_.-]*: name='\([^']*\)'.*/\1/p" \
            | sort -u || true
    )"

    # An unparseable dump would make this gate pass vacuously, so treat it as
    # a failure rather than "no permissions found".
    if [[ -z "$shipped" ]]; then
        fail "Could not read any permission from $apk; the check cannot verify the no-network promise."
    fi

    for permission in "${FORBIDDEN[@]}"; do
        if printf '%s\n' "$shipped" | grep -Fxq "$permission"; then
            printf '::error file=%s::Release APK requests %s. The no-network promise is broken: identify the dependency that injects it (./gradlew :app:dependencies) and strip it with tools:node="remove".\n' \
                "$apk" "$permission" >&2
            status=1
        fi
    done

    note "Shipped permissions in ${apk##*/}:"
    printf '%s\n' "$shipped" | sed 's/^/  - /'
    note ""

    while IFS= read -r line; do
        [[ -n "$line" ]] && shipped_all+=("$line")
    done <<< "$shipped"
done

if [[ "$status" -ne 0 ]]; then
    fail "Release permissions check failed: the app must not request INTERNET or ACCESS_NETWORK_STATE."
fi

note "OK  release APK requests no network permission."

# Report-only: the list is printed for human review, but only the network
# permissions are hard failures. A brand-new permission should be a conscious
# decision, not an accident.
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    {
        echo "### Release permissions"
        echo ""
        echo "No \`INTERNET\` / \`ACCESS_NETWORK_STATE\` permission in the release manifest or APK."
        echo ""
        echo "Shipped permissions:"
        echo ""
        printf '%s\n' "${shipped_all[@]}" | sort -u | sed 's/^\(.*\)$/- `\1`/'
    } >> "$GITHUB_STEP_SUMMARY"
fi
