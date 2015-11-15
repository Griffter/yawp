#!/bin/bash -e

if [ $# -ne 1 ]; then
    echo "use: archetype-tests.sh YAWP_VERSION"
    exit 1
fi

VERSION=$1
WORK_DIR=.working/archetype-tests
MAVEN_ARGS="-Dyawp.port=8081 -Dyawp.shutdownPort=8331"
unset MAVEN_OPTS

before() {
    rm -rf $WORK_DIR
    mkdir -p $WORK_DIR
    cd $WORK_DIR
    echo "generating project archetype to $WORK_DIR"

    mvn archetype:generate \
       -DarchetypeGroupId=io.yawp \
       -DarchetypeArtifactId=yawp \
       -DarchetypeVersion=$VERSION \
       -DgroupId=yawpapp \
       -DartifactId=yawpapp \
       -Dversion=1.0-SNAPSHOT \
       --batch-mode

    cd yawpapp
}

after() {
    cd ../..
    rm -rf $WORK_DIR
}

run_scaffolds() {
    echo "runnning scaffolds..."

    mvn yawp:endpoint -Dmodel=person
    mvn yawp:action -Dmodel=person -Dname=dummy
    mvn yawp:transformer -Dmodel=person -Dname=dummy
    mvn yawp:hook -Dmodel=person -Dname=dummy

    mvn clean install
}

run_endpoint_test() {
    echo "stopping devserver..."
    mvn yawp:devserver_stop $MAVEN_ARGS
    echo "done."

    echo "starting devserver..."
    mvn yawp:devserver $MAVEN_ARGS &
    mvn yawp:devserver_wait $MAVEN_ARGS
    echo "done."

    echo "calling endpoint"
    curl -H "Content-type: application/json" -X POST \
         -d "{'name': 'John'}" http://localhost:8081/api/people; echo

    echo "stopping devserver..."
    mvn yawp:devserver_stop $MAVEN_ARGS
    echo "done."

    ls target/appengine-generated/local_db.bin
}

run() {
    run_scaffolds
    run_endpoint_test
}

before
run
after
