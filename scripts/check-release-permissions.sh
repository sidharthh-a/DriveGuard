#!/usr/bin/env bash
#
# Release gate for two promises that a dependency bump can silently break:
#
#  1. The app cannot reach the network, enforced by never requesting the
#     network permissions. ML Kit pulls INTERNET/ACCESS_NETWORK_STATE in
#     transitively (transport-backend-cct), so a dependency bump could
#     re-add them. The merged release manifest, the built release APK(s) and
#     the release AAB are all inspected.
#  2. Native libraries support 16 KB memory pages. Play serves users from the
#     AAB and rejects apps targeting API 35+ whose 64-bit libraries have
#     PT_LOAD segments aligned below 16384 bytes, so the libraries bundled in
#     the AAB are checked here too.
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
if ! command -v python3 >/dev/null 2>&1; then
    fail "python3 not found; it is needed to read the ELF program headers of the bundled libraries."
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

# ---------------------------------------------------------------------------
# Release bundle native library alignment (16 KB pages)
# ---------------------------------------------------------------------------
# Play builds the 16 KB-page variant from the AAB, so every shipped 64-bit
# library must have all of its PT_LOAD segments aligned to at least 0x4000.
# The ELF program headers are parsed directly instead of shelling out to
# readelf/llvm-readelf, which are not part of the Android SDK and are absent on
# some developer machines. 32-bit ABIs are reported but not failed: 32-bit
# devices always use 4 KB pages, and their libraries are legitimately aligned
# to 0x1000 (as LiteRT's armeabi-v7a binaries are).
PAGE_16K=16384
lib_index=0
for aab in "${AABS[@]}"; do
    lib_index=$((lib_index + 1))
    lib_dir="$tmp_dir/libs-$lib_index"
    mkdir -p "$lib_dir"
    if ! unzip -o -q "$aab" '*/lib/*/*.so' -d "$lib_dir" >/dev/null 2>&1; then
        # unzip exits 11 when the pattern matches nothing, which must not be
        # confused with a damaged bundle; the empty-list check below reports it.
        if [[ -n "$(find "$lib_dir" -type f -name '*.so' -print -quit)" ]]; then
            fail "Could not extract the native libraries from $aab; 16 KB-page alignment cannot be verified."
        fi
    fi

    libs=()
    while IFS= read -r lib; do
        [[ -n "$lib" ]] && libs+=("$lib")
    done < <(find "$lib_dir" -type f -name '*.so' | sort)
    if [[ "${#libs[@]}" -eq 0 ]]; then
        fail "No native libraries found in $aab. This app ships LiteRT and ML Kit libraries, so an empty result means the bundle layout changed and the alignment check would pass vacuously."
    fi

    if ! alignment_report="$(
        python3 - "${libs[@]}" <<'PY' 2>&1
import struct
import sys

PT_LOAD = 1
ALIGN_OFFSET = {1: 0x1C, 2: 0x30}
ALIGN_FORMAT = {1: 'I', 2: 'Q'}

for path in sys.argv[1:]:
    with open(path, 'rb') as handle:
        data = handle.read()
    if len(data) < 0x34 or data[:4] != b'\x7fELF':
        sys.exit('%s: not an ELF file' % path)
    elf_class, elf_data = data[4], data[5]
    if elf_class not in (1, 2) or elf_data not in (1, 2):
        sys.exit('%s: unsupported ELF class/byte order' % path)
    endian = '<' if elf_data == 1 else '>'
    if elf_class == 2:
        phoff, = struct.unpack_from(endian + 'Q', data, 0x20)
        phentsize, phnum = struct.unpack_from(endian + 'HH', data, 0x36)
    else:
        phoff, = struct.unpack_from(endian + 'I', data, 0x1C)
        phentsize, phnum = struct.unpack_from(endian + 'HH', data, 0x2A)

    # The minimum, not the maximum: one under-aligned PT_LOAD segment is
    # enough to break loading on a 16 KB-page device.
    min_align = None
    for index in range(phnum):
        base = phoff + index * phentsize
        if base + phentsize > len(data):
            sys.exit('%s: truncated program header table' % path)
        if struct.unpack_from(endian + 'I', data, base)[0] != PT_LOAD:
            continue
        p_align, = struct.unpack_from(
            endian + ALIGN_FORMAT[elf_class], data, base + ALIGN_OFFSET[elf_class]
        )
        min_align = p_align if min_align is None else min(min_align, p_align)
    if min_align is None:
        sys.exit('%s: no PT_LOAD segment found' % path)
    print('%s\t%d' % (path, min_align))
PY
    )"; then
        fail "Could not read the ELF program headers of the libraries in $aab; 16 KB-page alignment cannot be verified:
$alignment_report"
    fi

    aligned_64bit=0
    note "Native library PT_LOAD alignment in ${aab##*/}:"
    while IFS=$'\t' read -r lib min_align; do
        [[ -n "$lib" ]] || continue
        rel="${lib#"$lib_dir"/}"
        abi="$(basename "$(dirname "$lib")")"
        case "$abi" in
            arm64-v8a | x86_64 | riscv64 | mips64)
                aligned_64bit=$((aligned_64bit + 1))
                if (( min_align < PAGE_16K )); then
                    printf '::error file=%s::%s ships a PT_LOAD segment aligned to %d bytes (0x%x), below the 16384 bytes (0x4000) required for 16 KB-page devices. Update or replace the dependency that provides it.\n' \
                        "$aab" "$rel" "$min_align" "$min_align" >&2
                    status=1
                    note "  $rel: $min_align bytes (0x$(printf '%x' "$min_align")) FAIL"
                else
                    note "  $rel: $min_align bytes (0x$(printf '%x' "$min_align")) ok"
                fi
                ;;
            *)
                note "  $rel: $min_align bytes (0x$(printf '%x' "$min_align")) — 32-bit ABI, 16 KB pages are not required"
                ;;
        esac
    done <<< "$alignment_report"
    note ""

    if [[ "$aligned_64bit" -eq 0 ]]; then
        fail "No 64-bit native library found in $aab; the 16 KB-page alignment check cannot verify anything."
    fi
done

if [[ "$status" -ne 0 ]]; then
    fail "Release gate failed: the app must not request INTERNET or ACCESS_NETWORK_STATE, and every 64-bit library in the AAB must be 16 KB-page aligned."
fi

note "OK  release APK requests no network permission."
if [[ "${#AABS[@]}" -gt 0 ]]; then
    note "OK  release AAB (Play upload) requests no network permission and its 64-bit libraries are 16 KB-page aligned."
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
        echo ""
        echo "Every 64-bit native library in the release AAB is 16 KB-page aligned."
    } >> "$GITHUB_STEP_SUMMARY"
fi
