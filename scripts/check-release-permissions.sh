#!/usr/bin/env bash
#
# Privacy gate: the app promises that it cannot reach the network, which is
# enforced by never requesting the network permissions. ML Kit pulls
# INTERNET/ACCESS_NETWORK_STATE in transitively (transport-backend-cct), so a
# dependency bump could silently re-add them to the shipped artifact. This
# script inspects the merged release manifest, the built release APK(s) and the
# release AAB — the file uploaded to Play — and fails the build if either
# permission comes back.
#
# Run after :app:assembleRelease :app:bundleRelease, with ANDROID_HOME (or
# ANDROID_SDK_ROOT) set.
#
#   ./scripts/check-release-permissions.sh [path/to/app-release.apk ...] [path/to/app-release.aab ...]
#
# With no arguments, every release APK and AAB in app/build/outputs is checked
# and a missing AAB is an error. Passing paths explicitly checks only those.

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

args_given=false
if [[ "$#" -gt 0 ]]; then
    args_given=true
fi

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
if ! command -v unzip >/dev/null 2>&1; then
    fail "unzip not found; it is needed to read the release AAB."
fi

# ---------------------------------------------------------------------------
# Locate artifacts
# ---------------------------------------------------------------------------
APKS=()
AABS=()
for arg in "$@"; do
    case "$arg" in
        *.aab) AABS+=("$arg") ;;
        *) APKS+=("$arg") ;;
    esac
done

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

if [[ "${#AABS[@]}" -eq 0 ]]; then
    while IFS= read -r aab; do
        [[ -n "$aab" ]] && AABS+=("$aab")
    done < <(
        find app/build/outputs/bundle/release -maxdepth 1 -type f -name '*.aab' 2>/dev/null \
            | sort || true
    )
fi
for aab in "${AABS[@]}"; do
    [[ -f "$aab" ]] || fail "Release AAB not found: $aab"
done
if [[ "${#AABS[@]}" -eq 0 && "$args_given" == "false" ]]; then
    # The AAB is what Play serves to users, so it must be checked too. Failing
    # (rather than skipping) keeps a removed :app:bundleRelease step from
    # quietly narrowing the gate.
    fail "Release AAB not found; run :app:bundleRelease first (or pass the .aab path explicitly)."
fi

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
for aab in "${AABS[@]}"; do
    note "  AAB:             $aab"
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

# ---------------------------------------------------------------------------
# Release bundle (AAB)
# ---------------------------------------------------------------------------
# The AAB is the artifact uploaded to Play. aapt2 cannot read it because the
# manifest inside a bundle is protobuf-encoded, so the module manifest is read
# straight out of the zip: a declared permission is stored there as an exact
# UTF-8 string, so a token match is a reliable detection of it being shipped.
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

for aab in "${AABS[@]}"; do
    module_manifest="$tmp_dir/module-manifest.xml"
    if ! unzip -p "$aab" base/manifest/AndroidManifest.xml > "$module_manifest" 2>/dev/null; then
        fail "Could not read base/manifest/AndroidManifest.xml from $aab; the bundle cannot be verified."
    fi
    if [[ ! -s "$module_manifest" ]]; then
        fail "base/manifest/AndroidManifest.xml in $aab is empty; the bundle cannot be verified."
    fi

    # Vacuity guard: an unreadable or mangled extraction would otherwise pass
    # the forbidden-permission check by finding nothing at all.
    if ! grep -aqiE '(^|[^[:alnum:]_.])[a-z0-9_.]+\.permission\.' "$module_manifest"; then
        fail "No permission names found in the module manifest of $aab; the bundle cannot be verified."
    fi

    for permission in "${FORBIDDEN[@]}"; do
        # Token boundaries, so `...INTERNET_X` in some asset string is not a
        # false hit while a real declaration always is one.
        if grep -aqE -- "(^|[^[:alnum:]_.])${permission//./\\.}([^[:alnum:]_.]|\$)" "$module_manifest"; then
            printf '::error file=%s::Release AAB declares %s in its module manifest. Play would ship that permission: identify the dependency that injects it (./gradlew :app:dependencies) and strip it with tools:node="remove".\n' \
                "$aab" "$permission" >&2
            status=1
        fi
    done

    # Bundle and APKs are built from the same merged manifest, so every
    # permission the APK ships must also be in the bundle manifest. This also
    # proves the extracted manifest is the real one rather than a stub.
    for permission in "${shipped_all[@]}"; do
        if ! grep -aqF -- "$permission" "$module_manifest"; then
            printf '::error file=%s::Release AAB is missing %s, which the release APK ships. The bundle and APK were not built from the same manifest.\n' \
                "$aab" "$permission" >&2
            status=1
        fi
    done

    # Named/referenced, not necessarily requested: component attributes such as
    # android:permission also appear here. The APK list above is authoritative
    # for what the app requests.
    note "Permission names in the base module manifest of ${aab##*/}:"
    grep -aoE '[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)*\.([Pp][Ee][Rr][Mm][Ii][Ss][Ss][Ii][Oo][Nn])\.[A-Za-z0-9_]+' \
        "$module_manifest" | sort -u | sed 's/^/  - /' || true
    note ""
done

if [[ "$status" -ne 0 ]]; then
    fail "Release permissions check failed: the app must not request INTERNET or ACCESS_NETWORK_STATE."
fi

note "OK  release APK requests no network permission."
if [[ "${#AABS[@]}" -gt 0 ]]; then
    note "OK  release AAB (Play upload) requests no network permission."
fi

# Report-only: the list is printed for human review, but only the network
# permissions are hard failures. A brand-new permission should be a conscious
# decision, not an accident.
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    {
        echo "### Release permissions"
        echo ""
        echo "No \`INTERNET\` / \`ACCESS_NETWORK_STATE\` permission in the release manifest,"
        echo "the release APK or the release AAB."
        echo ""
        echo "Shipped permissions (release APK):"
        echo ""
        printf '%s\n' "${shipped_all[@]}" | sort -u | sed 's/^\(.*\)$/- `\1`/'
    } >> "$GITHUB_STEP_SUMMARY"
fi
