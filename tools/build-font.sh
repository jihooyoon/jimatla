#!/bin/bash
# Rebuilds app/src/main/res/font/google_sans_flex.ttf from the upstream variable font (OFL 1.1).
# Keeps only what the launcher uses: Latin + Vietnamese, weights 300–700, optical sizes 14–128;
# roundness/slant/width are pinned to their defaults. Needs `pip install fonttools`.
set -euo pipefail
work=$(mktemp -d)
curl -sL -o "$work/src.ttf" "https://github.com/google/fonts/raw/main/ofl/googlesansflex/GoogleSansFlex%5BROND,opsz,slnt,wdth,wght%5D.ttf"
fonttools varLib.instancer "$work/src.ttf" ROND=0 slnt=0 wdth=100 -o "$work/pinned.ttf"
pyftsubset "$work/pinned.ttf" --layout-features='*' --no-hinting --output-file="$work/subset.ttf" \
  --unicodes="U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0300-0309,U+0323,U+0329,U+0100-024F,U+1E00-1EFF,U+2000-206F,U+20AB,U+20AC,U+2122,U+2190-2193,U+2212,U+2215,U+FEFF,U+FFFD"
fonttools varLib.instancer "$work/subset.ttf" wght=300:700 opsz=14:128 -o "$(dirname "$0")/../app/src/main/res/font/google_sans_flex.ttf"
