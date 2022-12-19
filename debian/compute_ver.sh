#!/bin/sh
shorthash=$(git log -1 --format='%h' "$1")
rev_count=$(git rev-list --count "$shorthash")
echo "0.0.0.r${rev_count}.g${shorthash}"
