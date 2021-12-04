#!/bin/sh -ex
dir=$(dirname "$0")

java -cp $dir/h2-*.jar org.h2.tools.RunScript -url jdbc:h2:$dir/producer -user sa -password '' -script $dir/producer.sql
java -cp $dir/h2-*.jar org.h2.tools.RunScript -url jdbc:h2:$dir/consumer1 -user sa -password '' -script $dir/consumer1.sql
java -cp $dir/h2-*.jar org.h2.tools.RunScript -url jdbc:h2:$dir/consumer2 -user sa -password '' -script $dir/consumer2.sql