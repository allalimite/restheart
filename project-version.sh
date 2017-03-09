#!/bin/sh
export MVN_VERSION=$(mvn --quiet \
    -Dexec.executable="echo" \
    -Dexec.args='${project.version}' \
    --non-recursive \
    org.codehaus.mojo:exec-maven-plugin:1.3.1:exec 2>/dev/null)
echo $MVN_VERSION
