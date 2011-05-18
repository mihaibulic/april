#!/bin/bash

MPOINT=0.0

for M in {10..50}
do
    java edu.umich.mihai.util.PointLocator 0.0001 0.01 ${MPOINT}${M} >> output
done
