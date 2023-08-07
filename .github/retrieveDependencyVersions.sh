#!/bin/bash

backupInstrumentationVersions () {
  local versionsFile=instrumentation/$1/src/main/resources/tested_versions/$2
  local backupFile=instrumentation/$1/src/main/resources/tested_versions/$2.backup
  if [ -f "$versionsFile" ]; then
    cp "$versionsFile" "$backupFile"
  fi
}

deleteInstrumentationVersionsBackup () {
  local backupFile=instrumentation/$1/src/main/resources/tested_versions/$2.backup
  if [ -f "$backupFile" ]; then
    rm "$backupFile"
  fi
}

# Find all instrumentations
instrumentationFolders=$(find instrumentation -type d -name "tested_versions" -not -path "*/build/*" | sed 's/instrumentation\///g' | sed 's/\/src\/main\/resources\/tested_versions//' | sort)

printf -v joinedInstrumentation '%s,' "${instrumentationFolders[@]}"
echo "Discovering untested versions of: ${joinedInstrumentation%,}"

for instrumentationFolder in $instrumentationFolders; do
  # If the instrumentation folder has many levels, remove all but the last one
  instrumentation=$(basename "$instrumentationFolder")

  if [ "$instrumentation" == "javaagent" ]; then
    # Find next directory up
    instrumentation=$(basename "$(dirname "$instrumentationFolder")")
  fi

  echo "Discovering untested versions of ${instrumentation} ..."

  # Read the list of existing versions from the file in tested_versions
  existingVersions=$(cat instrumentation/"${instrumentationFolder}"/src/main/resources/tested_versions/"$instrumentation")

  # Find the highest currently tested version by reading the last line of the file
  highestExistingVersion=$(echo "$existingVersions" | tail -n 1)

  # Remove the ! from the highest version if it's present
  if [[ $highestExistingVersion == *"!"* ]]; then
    highestExistingVersion="${highestExistingVersion:1}"
  fi

  # Retrieve the dependency name from build.gradle
  IFS='-' read -ra instr_parts <<< "$instrumentation"
  # shellcheck disable=SC2034
  search_regex=$(IFS='|' ; echo "${instr_parts[*]}")
  dependency=$(grep -i compileOnly instrumentation/"${instrumentationFolder}"/build.gradle | grep -iE "$search_regex" | head -n 1 | sed -E 's/.*"([^"]+)".*/\1/')

  # Retrieve all versions of the dependency from Maven Central
  allVersions=$(curl -s "https://search.maven.org/solrsearch/select?q=g:%22$(echo "$dependency" | cut -d: -f1)%22+AND+a:%22$(echo "$dependency" | cut -d: -f2)%22&core=gav&rows=100&wt=json" | jq -r '.response.docs[].v' | sort -V)

  untested_versions=()
  for version in $allVersions; do
    if [[ $version > $highestExistingVersion ]]; then
      untested_versions+=("$version")
    fi
  done

  # shellcheck disable=SC2128
  if [ -z "$untested_versions" ]; then
    echo "No untested versions of ${instrumentation} since ${highestExistingVersion} found."
  else
    echo "Testing ${#untested_versions[@]} untested versions of ${instrumentation} since ${highestExistingVersion} ..."

    backupInstrumentationVersions "$instrumentationFolder" "$instrumentation"

    # Loop through the untested versions and test them with gradle
    for version in "${untested_versions[@]}"; do
      echo "Testing ${instrumentation} version ${version} ..."


      # Run the instrumentation tests
      ./gradlew :instrumentation:"${instrumentationFolder//\//:}":test --rerun -Pversions."${instrumentation//[-.]//}"="$version"

      # Update the instrumentation version file
      if [ $? -ne 0 ]; then
        ## Test failed
        echo "!$version" >> instrumentation/"${instrumentationFolder}"/src/main/resources/tested_versions/"$instrumentation"
      else
        ## Test succeeded
        echo "$version" >> instrumentation/"${instrumentationFolder}"/src/main/resources/tested_versions/"$instrumentation"
      fi
    done

    deleteInstrumentationVersionsBackup "$instrumentationFolder" "$instrumentation"
  fi
done
