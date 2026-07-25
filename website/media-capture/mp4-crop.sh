#!/usr/bin/env bash

set -euo pipefail

OUT_DIR="processed"
mkdir -p "$OUT_DIR"

shopt -s nullglob

for input in *.mp4; do
    filename="${input%.*}"
    output="$OUT_DIR/${filename}.mp4"

    echo "Processing: $input"

    ffmpeg -y \
        -i "$input" \
        -vf "crop=in_w:in_h-250:0:100" \
        -an \
        -c:v libsvtav1 \
        -crf 28 \
        -preset 6 \
        -pix_fmt yuv420p \
        -movflags +faststart \
        "$output"
done

echo "Done!"
