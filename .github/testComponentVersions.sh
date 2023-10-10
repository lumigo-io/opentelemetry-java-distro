#!/bin/bash

instrumentationFolder=$1

instrumentation=$(basename "$instrumentationFolder")

if [ "$instrumentation" == "javaagent" ]; then
  # Find next directory up
  instrumentation=$(basename "$(dirname "$instrumentationFolder")")
fi

# Read the list of versions from the file in tested_versions
existingVersions=$(cat instrumentation/"${instrumentationFolder}"/src/main/resources/tested_versions/"$instrumentation")

success=true

# Loop through the untested versions and test them with gradle
for version in "${existingVersions[@]}"; do
  echo "Testing ${instrumentation} version ${version} ..."

  # Run the instrumentation tests
  ./gradlew :instrumentation:"${instrumentationFolder//\//:}":test --rerun -Pversions."${instrumentation//[-.]//}"="$version"

  if [ $? -ne 0 ]; then
    ## Test failed
    echo "Instrumentation tests failed for version ${version}"
    success=false
  else
    ## Test passed
    echo "Instrumentation tests passed for version ${version}"
  fi
done

if [ "$success" = false ]; then
  echo "Instrumentation tests failed for ${instrumentation}"
  exit 1
fi
