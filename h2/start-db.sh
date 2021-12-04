#!/bin/sh -ex
dir=$(dirname "$0")

java -cp $dir/h2-*.jar org.h2.tools.Server -tcp -web -baseDir .