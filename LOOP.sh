#!/bin/bash
x=1
while [ $x -le 5 ]
do
  echo "################ ITERATION $x"
  junie --brave "$(cat PROMPT.md)"
  x=$(( $x + 1 ))
done
