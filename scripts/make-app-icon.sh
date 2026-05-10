#!/usr/bin/env bash

set -euo pipefail

usage() {
    cat <<'EOF'
Usage:
  ./scripts/make-app-icon.sh <icon.svg> [options]

Options:
  -r, --res-dir <path>            Android res directory (default: app/src/main/res)
  -b, --background-color <#RRGGBB>
                                  Adaptive icon background color override
      --validate                  Run :app:processDebugResources after generation
  -h, --help                      Show this help

Examples:
  ./scripts/make-app-icon.sh icon.svg
  ./scripts/make-app-icon.sh assets/logo.svg --background-color '#D4C5B0' --validate
EOF
}

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
default_res_dir="$repo_root/app/src/main/res"
gradlew_path="$repo_root/gradlew"

svg_input=""
res_dir="$default_res_dir"
background_color=""
should_validate=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        -r|--res-dir)
            if [[ $# -lt 2 ]]; then
                echo "Error: --res-dir requires a value." >&2
                exit 1
            fi
            res_dir="$2"
            shift 2
            ;;
        -b|--background-color)
            if [[ $# -lt 2 ]]; then
                echo "Error: --background-color requires a value." >&2
                exit 1
            fi
            background_color="$2"
            shift 2
            ;;
        --validate)
            should_validate=1
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        -* )
            echo "Error: Unknown option: $1" >&2
            usage
            exit 1
            ;;
        *)
            if [[ -n "$svg_input" ]]; then
                echo "Error: Multiple SVG inputs provided." >&2
                usage
                exit 1
            fi
            svg_input="$1"
            shift
            ;;
    esac
done

if [[ -z "$svg_input" ]]; then
    echo "Error: Missing SVG input path." >&2
    usage
    exit 1
fi

if ! command -v magick >/dev/null 2>&1; then
    echo "Error: ImageMagick 'magick' is required but not found in PATH." >&2
    exit 1
fi

if [[ "$svg_input" != /* ]]; then
    svg_input="$PWD/$svg_input"
fi

if [[ "$res_dir" != /* ]]; then
    res_dir="$PWD/$res_dir"
fi

if [[ ! -f "$svg_input" ]]; then
    echo "Error: SVG file not found: $svg_input" >&2
    exit 1
fi

if [[ -n "$background_color" ]]; then
    background_color="#${background_color#\#}"
    if [[ ! "$background_color" =~ ^#[0-9A-Fa-f]{6}$ ]]; then
        echo "Error: Invalid --background-color. Use format #RRGGBB." >&2
        exit 1
    fi
fi

mkdir -p "$res_dir/drawable" "$res_dir/drawable-nodpi" "$res_dir/mipmap-anydpi-v26"
mkdir -p "$res_dir/mipmap-mdpi" "$res_dir/mipmap-hdpi" "$res_dir/mipmap-xhdpi" "$res_dir/mipmap-xxhdpi" "$res_dir/mipmap-xxxhdpi"

render_icon() {
    local size="$1"
    local out_file="$2"
    magick \
        -background none \
        "$svg_input" \
        -resize "${size}x${size}" \
        -gravity center \
        -extent "${size}x${size}" \
        -alpha set \
        -define webp:lossless=true \
        "$out_file"
}

full_icon_file="$res_dir/drawable-nodpi/ic_launcher_full.webp"
render_icon 432 "$full_icon_file"

declare -A launcher_sizes=(
    [mdpi]=48
    [hdpi]=72
    [xhdpi]=96
    [xxhdpi]=144
    [xxxhdpi]=192
)

for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
    size="${launcher_sizes[$density]}"
    target_dir="$res_dir/mipmap-$density"

    render_icon "$size" "$target_dir/ic_launcher.webp"
    cp -f "$target_dir/ic_launcher.webp" "$target_dir/ic_launcher_round.webp"
done

if [[ -z "$background_color" ]]; then
    detected_hex=""
    if command -v identify >/dev/null 2>&1; then
        detected_hex="$(identify -format '%[hex:p{0,0}]' "$full_icon_file" 2>/dev/null || true)"
    fi

    if [[ "$detected_hex" =~ ^[0-9A-Fa-f]{6,8}$ ]]; then
        background_color="#${detected_hex:0:6}"
    else
        background_color="#D4C5B0"
    fi
fi

cat > "$res_dir/drawable/ic_launcher_foreground.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<inset xmlns:android="http://schemas.android.com/apk/res/android"
    android:inset="0dp">
    <bitmap
        android:gravity="fill"
        android:src="@drawable/ic_launcher_full" />
</inset>
EOF

cat > "$res_dir/drawable/ic_launcher_background.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="$background_color" />
</shape>
EOF

cat > "$res_dir/mipmap-anydpi-v26/ic_launcher.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
EOF

cp -f "$res_dir/mipmap-anydpi-v26/ic_launcher.xml" "$res_dir/mipmap-anydpi-v26/ic_launcher_round.xml"

echo "Generated launcher icons from: $svg_input"
echo "Resource directory: $res_dir"
echo "Adaptive background color: $background_color"

if [[ "$should_validate" -eq 1 ]]; then
    if [[ -x "$gradlew_path" ]]; then
        echo "Running Gradle resource validation..."
        "$gradlew_path" :app:processDebugResources --console=plain
    else
        echo "Warning: gradlew not found at $gradlew_path, skipping validation." >&2
    fi
fi

echo "Done."