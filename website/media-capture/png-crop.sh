#!/usr/bin/env bash

set -euo pipefail

OUT_DIR="webp"
mkdir -p "$OUT_DIR"

shopt -s nullglob

TOP=100
BOTTOM=150

for input in *.png; do
    filename="${input%.*}"
    output="$OUT_DIR/${filename}.webp"

    width=$(magick identify -format "%w" "$input")
    height=$(magick identify -format "%h" "$input")
    new_height=$((height - TOP - BOTTOM))

    echo "Processing: $input"

    magick "$input" \
        -crop "${width}x${new_height}+0+${TOP}" +repage \
        -strip \
        -define webp:lossless=true \
        -define webp:method=6 \
        "$output"
done

echo "Done! WebP images are in '$OUT_DIR/'."
